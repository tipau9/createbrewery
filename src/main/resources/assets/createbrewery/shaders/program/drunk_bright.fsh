#version 150

// Bloom, step 1: only what is really bright, scaled up for precision in the 8-bit target.
uniform sampler2D DiffuseSampler;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    fragColor = vec4(max(texture(DiffuseSampler, texCoord).rgb - 0.7, 0.0) * 3.3, 1.0);
}
