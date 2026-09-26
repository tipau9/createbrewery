#version 150

// Bloom, step 2: one direction of a 9-tap Gaussian. BlurDir is the step in pixels (set per pass
// in post/drunk.json); run horizontal then vertical, twice, wider the second time.
uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform vec2 BlurDir;
in vec2 texCoord;
out vec4 fragColor;

void main() {
    vec2 s = BlurDir / OutSize;
    vec3 sum = texture(DiffuseSampler, texCoord).rgb * 0.227;
    sum += (texture(DiffuseSampler, texCoord + s).rgb + texture(DiffuseSampler, texCoord - s).rgb) * 0.195;
    sum += (texture(DiffuseSampler, texCoord + s * 2.0).rgb + texture(DiffuseSampler, texCoord - s * 2.0).rgb) * 0.122;
    sum += (texture(DiffuseSampler, texCoord + s * 3.0).rgb + texture(DiffuseSampler, texCoord - s * 3.0).rgb) * 0.054;
    sum += (texture(DiffuseSampler, texCoord + s * 4.0).rgb + texture(DiffuseSampler, texCoord - s * 4.0).rgb) * 0.016;
    fragColor = vec4(sum, 1.0);
}
