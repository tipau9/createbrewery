#version 150

// Retinal burn-in (see StrobeFlash): holds the frame a strobe burnt into the eye. Capture 1 takes
// the scene as it is right now, a fraction tops the held picture up, 0 keeps it as it is.
uniform sampler2D DiffuseSampler;
uniform sampler2D BurnSampler;
uniform vec2 OutSize;
uniform vec3 StrobeBurnInPos;  // where the lens is on screen (xy) and how big (z, in screen heights)
uniform float Capture;

in vec2 texCoord;
out vec4 fragColor;

float luma(vec3 c) {
    return dot(c, vec3(0.299, 0.587, 0.114));
}

void main() {
    vec3 held = texture(BurnSampler, texCoord).rgb;
    if (Capture <= 0.0) {
        fragColor = vec4(held, 1.0);
        return;
    }
    vec3 now = texture(DiffuseSampler, texCoord).rgb;

    // Under a shaderpack the strobe does not light the lightmap, and the frame is the dark room.
    // Then lift it and draw the edges, strongest round the lens, so the silhouettes in the dark
    // still burn in. A lit (vanilla) frame stays as it is, or it would clip to white.
    float avg = 0.0;
    for (int i = 0; i < 4; i++) {
        for (int j = 0; j < 4; j++) {
            avg += luma(texture(DiffuseSampler, (vec2(float(i), float(j)) + 0.5) / 4.0).rgb);
        }
    }
    avg /= 16.0;
    float dark = smoothstep(0.15, 0.05, avg);
    if (dark > 0.0) {
        vec2 px = 2.0 / OutSize;
        float c = luma(now);
        float edge = abs(c - luma(texture(DiffuseSampler, texCoord + vec2(px.x, 0.0)).rgb))
                   + abs(c - luma(texture(DiffuseSampler, texCoord - vec2(px.x, 0.0)).rgb))
                   + abs(c - luma(texture(DiffuseSampler, texCoord + vec2(0.0, px.y)).rgb))
                   + abs(c - luma(texture(DiffuseSampler, texCoord - vec2(0.0, px.y)).rgb));
        vec2 d = (texCoord - StrobeBurnInPos.xy) * vec2(OutSize.x / OutSize.y, 1.0);
        float near = exp(-dot(d, d) / 0.18);
        // ponytail: 4.0 edge gain and 0.18 falloff eyeballed; tune here.
        vec3 lifted = pow(clamp(now, 0.0, 1.0), vec3(0.45)) + vec3(min(1.0, edge * 4.0) * (0.3 + 0.7 * near));
        now = mix(now, clamp(lifted, 0.0, 1.0), dark);
    }
    fragColor = vec4(mix(held, now, Capture), 1.0);
}
