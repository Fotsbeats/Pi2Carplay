#ifndef EXPERIMENTAL_ENCODER_CORE_H
#define EXPERIMENTAL_ENCODER_CORE_H
#include <stdint.h>
#define FRAME_BYTES (1920*720*4)
#define I420_BYTES (1920*720*3/2)
typedef struct Encoder Encoder;
Encoder *encoder_open(const char *path, int width, int height, int fps);
int encoder_width(const Encoder *e);
int encoder_height(const Encoder *e);
int encoder_reconfigure(Encoder *e, int fps, int bitrate);
int encoder_frame(Encoder *e, const unsigned char *rgba, uint32_t stamp, int force);
int encoder_frame_i420(Encoder *e, const unsigned char *i420, uint32_t stamp, int force);
void encoder_close(Encoder *e);
#endif
