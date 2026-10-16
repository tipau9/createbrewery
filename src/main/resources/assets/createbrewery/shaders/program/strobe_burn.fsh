#version 150

// Retinal burn-in (see StrobeFlash): holds the frame a strobe burnt into the eye. Capture 1 takes
// the scene as it is right now, a fraction tops the held picture up, 0 keeps it as it is.
uniform sampler2D DiffuseSampler;
uniform sampler2D BurnSampler;
uniform float Capture;

in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(mix(texture(BurnSampler, texCoord).rgb, texture(DiffuseSampler, texCoord).rgb, Capture), 1.0);
}
