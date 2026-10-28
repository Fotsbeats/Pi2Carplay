package org.bench.boxhome;
import android.graphics.*;

final class Backdrop {
    private static Bitmap cached;
    static Bitmap image() {
        if(cached!=null)return cached;
        Bitmap result=Bitmap.createBitmap(1744,720,Bitmap.Config.ARGB_8888);
        Canvas c=new Canvas(result);Paint paint=new Paint(3);
            paint.setShader(new LinearGradient(0,0,1744,720,new int[]{0xff1c232a,0xff0b1015,0xff151614},new float[]{0,.5f,1},Shader.TileMode.CLAMP));c.drawRect(0,0,1744,720,paint);
            paint.setShader(new RadialGradient(1378,691,1000,new int[]{0x45b85b18,0x00b85b18},null,Shader.TileMode.CLAMP));c.drawRect(0,0,1744,720,paint);paint.setShader(null);
            Path ribbon=new Path();ribbon.moveTo(-150,240);ribbon.cubicTo(380,35,685,765,1200,475);ribbon.cubicTo(1500,306,1710,80,1900,160);ribbon.lineTo(1900,305);ribbon.cubicTo(1630,205,1480,600,1170,650);ribbon.cubicTo(800,750,350,195,-150,410);ribbon.close();
            paint.setColor(0x123f505d);c.drawPath(ribbon,paint);
            Path edge=new Path();edge.moveTo(-170,610);edge.cubicTo(315,340,770,850,1230,535);edge.cubicTo(1510,343,1690,240,1910,330);
            paint.setStyle(Paint.Style.STROKE);paint.setAlpha(255);paint.setShader(new LinearGradient(0,0,1744,0,new int[]{0x00e37b2a,0x33e37b2a,0x99f7a652,0x08e97c25},new float[]{0,.32f,.7f,1},Shader.TileMode.CLAMP));paint.setStrokeWidth(2);c.drawPath(edge,paint);c.translate(0,12);paint.setStrokeWidth(.7f);c.drawPath(edge,paint);paint.setShader(null);paint.setStyle(Paint.Style.FILL);
        cached=result;return cached;
    }
}
