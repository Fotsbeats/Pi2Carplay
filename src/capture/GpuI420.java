import android.opengl.*;
import java.io.IOException;
import java.nio.*;

/** Packed top-down BT.601 limited-range I420 in an RGBA8 pbuffer. */
final class GpuI420 implements AutoCloseable {
    private final DisplayGeometry geometry;
    private EGLDisplay display=EGL14.EGL_NO_DISPLAY;
    private EGLContext context=EGL14.EGL_NO_CONTEXT;
    private EGLSurface surface=EGL14.EGL_NO_SURFACE;
    private int program;
    private final boolean external;
    private final FloatBuffer vertices=ByteBuffer.allocateDirect(32).order(ByteOrder.nativeOrder()).asFloatBuffer();
    private static final String FRAGMENT=
        "precision highp float; uniform mat4 m; uniform vec2 canvas; uniform vec4 area; uniform float packedWidth;\n"+
        "vec3 rgbAt(vec2 p) {\n"+
        " if(p.x<area.x||p.y<area.y||p.x>=area.x+area.z||p.y>=area.y+area.w) return vec3(0.0);\n"+
        " vec2 uv=vec2((p.x-area.x+0.5)/area.z,1.0-(p.y-area.y+0.5)/area.w);\n"+
        " return floor(texture2D(image,(m*vec4(uv,0.0,1.0)).xy).rgb*255.0+0.5); }\n"+
        "float component(float index) {\n"+
        " float pixels=canvas.x*canvas.y; if(index>=pixels*1.5)return 0.0; if(index<pixels) {\n"+
        "  vec3 rgb=rgbAt(vec2(mod(index,canvas.x),floor(index/canvas.x)));\n"+
        "  return (floor((dot(rgb,vec3(66.0,129.0,25.0))+128.0)/256.0)+16.0)/255.0; }\n"+
        " bool u=index<pixels*1.25; float k=index-(u?pixels:pixels*1.25);\n"+
        " vec2 p=vec2(mod(k,canvas.x*0.5)*2.0,floor(k/(canvas.x*0.5))*2.0);\n"+
        " vec3 rgb; if(p.x<area.x||p.y<area.y||p.x+1.0>=area.x+area.z||p.y+1.0>=area.y+area.w)\n"+
        " rgb=floor((rgbAt(p)+rgbAt(p+vec2(1.0,0.0))+rgbAt(p+vec2(0.0,1.0))+rgbAt(p+vec2(1.0,1.0)))*0.25+0.5);\n"+
        " else rgb=rgbAt(p+vec2(0.5));\n"+
        " vec3 coefficients=u?vec3(-38.0,-74.0,112.0):vec3(112.0,-94.0,-18.0);\n"+
        " return clamp(floor((dot(rgb,coefficients)+128.0)/256.0)+128.0,0.0,255.0)/255.0; }\n"+
        "void main(){ float base=(floor(gl_FragCoord.y)*packedWidth+floor(gl_FragCoord.x))*4.0;\n"+
        " gl_FragColor=vec4(component(base),component(base+1.0),component(base+2.0),component(base+3.0)); }\n";
    private static int shader(int type,String source) throws IOException {
        int s=GLES20.glCreateShader(type); GLES20.glShaderSource(s,source);GLES20.glCompileShader(s);
        int[] ok=new int[1];GLES20.glGetShaderiv(s,GLES20.GL_COMPILE_STATUS,ok,0);
        if(ok[0]==0){String log=GLES20.glGetShaderInfoLog(s);GLES20.glDeleteShader(s);throw new IOException(log);}
        return s;
    }
    GpuI420(boolean external,DisplayGeometry geometry) throws IOException {
        this.external=external;this.geometry=geometry;
        try {
            display=EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
            int[] v=new int[2];
            if(!EGL14.eglInitialize(display,v,0,v,1))throw new IOException("EGL initialization failed");
            EGLConfig[] config=new EGLConfig[1];int[] count=new int[1];
            int[] attrs={EGL14.EGL_RED_SIZE,8,EGL14.EGL_GREEN_SIZE,8,EGL14.EGL_BLUE_SIZE,8,EGL14.EGL_ALPHA_SIZE,8,
                EGL14.EGL_RENDERABLE_TYPE,EGL14.EGL_OPENGL_ES2_BIT,EGL14.EGL_SURFACE_TYPE,EGL14.EGL_PBUFFER_BIT,EGL14.EGL_NONE};
            if(!EGL14.eglChooseConfig(display,attrs,0,config,0,1,count,0)||count[0]<1)throw new IOException("RGBA8 EGL config missing");
            context=EGL14.eglCreateContext(display,config[0],EGL14.EGL_NO_CONTEXT,new int[]{EGL14.EGL_CONTEXT_CLIENT_VERSION,2,EGL14.EGL_NONE},0);
            surface=EGL14.eglCreatePbufferSurface(display,config[0],new int[]{EGL14.EGL_WIDTH,geometry.packedWidth,EGL14.EGL_HEIGHT,geometry.packedHeight,EGL14.EGL_NONE},0);
            if(!EGL14.eglMakeCurrent(display,surface,surface,context))throw new IOException("EGL current failed");
            for(int channel:new int[]{GLES20.GL_RED_BITS,GLES20.GL_GREEN_BITS,GLES20.GL_BLUE_BITS,GLES20.GL_ALPHA_BITS}) {
                GLES20.glGetIntegerv(channel,count,0);if(count[0]!=8)throw new IOException("Packed readback requires four 8-bit channels");
            }
            String header=external?"#extension GL_OES_EGL_image_external : require\nuniform samplerExternalOES image;\n":"uniform sampler2D image;\n";
            int vs=shader(GLES20.GL_VERTEX_SHADER,"attribute vec2 p; void main(){gl_Position=vec4(p,0.0,1.0);}");
            int fs;
            try {fs=shader(GLES20.GL_FRAGMENT_SHADER,header+FRAGMENT);}catch(IOException e){GLES20.glDeleteShader(vs);throw e;}
            program=GLES20.glCreateProgram();GLES20.glAttachShader(program,vs);GLES20.glAttachShader(program,fs);GLES20.glLinkProgram(program);
            GLES20.glDeleteShader(vs);GLES20.glDeleteShader(fs);
            GLES20.glGetProgramiv(program,GLES20.GL_LINK_STATUS,count,0);
            if(count[0]==0)throw new IOException(GLES20.glGetProgramInfoLog(program));
            vertices.put(new float[]{-1,-1,1,-1,-1,1,1,1}).position(0);
            GLES20.glDisable(GLES20.GL_DITHER);GLES20.glDisable(GLES20.GL_BLEND);
        }catch(IOException|RuntimeException e){close();throw e;}
    }
    void render(int texture,float[] matrix,ByteBuffer i420) throws IOException {
        if(!i420.isDirect()||i420.capacity()!=geometry.packedBytes)throw new IOException("Incorrect packed I420 buffer");
        GLES20.glViewport(0,0,geometry.packedWidth,geometry.packedHeight);GLES20.glUseProgram(program);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(external?GLES11Ext.GL_TEXTURE_EXTERNAL_OES:GLES20.GL_TEXTURE_2D,texture);
        GLES20.glUniform1i(GLES20.glGetUniformLocation(program,"image"),0);
        GLES20.glUniform2f(GLES20.glGetUniformLocation(program,"canvas"),geometry.width,geometry.height);
        GLES20.glUniform4f(GLES20.glGetUniformLocation(program,"area"),geometry.x,geometry.y,geometry.contentWidth,geometry.contentHeight);
        GLES20.glUniform1f(GLES20.glGetUniformLocation(program,"packedWidth"),geometry.packedWidth);
        GLES20.glUniformMatrix4fv(GLES20.glGetUniformLocation(program,"m"),1,false,matrix,0);
        int pos=GLES20.glGetAttribLocation(program,"p");vertices.position(0);
        GLES20.glEnableVertexAttribArray(pos);GLES20.glVertexAttribPointer(pos,2,GLES20.GL_FLOAT,false,0,vertices);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP,0,4);
        i420.clear();GLES20.glReadPixels(0,0,geometry.packedWidth,geometry.packedHeight,GLES20.GL_RGBA,GLES20.GL_UNSIGNED_BYTE,i420);i420.clear();
        if(GLES20.glGetError()!=GLES20.GL_NO_ERROR)throw new IOException("Packed YUV render/readback failed");
    }
    public void close(){
        if(display!=EGL14.EGL_NO_DISPLAY){
            if(program!=0)GLES20.glDeleteProgram(program);
            EGL14.eglMakeCurrent(display,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_SURFACE,EGL14.EGL_NO_CONTEXT);
            if(surface!=EGL14.EGL_NO_SURFACE)EGL14.eglDestroySurface(display,surface);
            if(context!=EGL14.EGL_NO_CONTEXT)EGL14.eglDestroyContext(display,context);
            EGL14.eglTerminate(display);
        }
        display=EGL14.EGL_NO_DISPLAY;surface=EGL14.EGL_NO_SURFACE;context=EGL14.EGL_NO_CONTEXT;program=0;
    }
}
