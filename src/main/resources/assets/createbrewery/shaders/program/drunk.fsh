#version 150

// Drunk vision. Two inputs from DrunkClient:
//   Mood      0..1  the party zone (a beer or two): "beer goggles" - glow, halos around lights,
//                   richer, warmer colours. The world simply looks nicer.
//   Intensity 0..1  how drunk: wobble, double vision, blurred edges, tunnel, washed-out colours,
//                   and afterimages - the picture drags behind when you turn.
// PrevSampler is last frame's output (see post/drunk.json), which makes the afterimages.

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;
uniform vec2 OutSize;
uniform float Intensity;
uniform float Mood;
uniform float DrunkTime;

in vec2 texCoord;

out vec4 fragColor;

vec3 tap(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

// Average of 8 samples on a ring, used for blur and glow.
vec3 ring(vec2 uv, vec2 r) {
    return (tap(uv + vec2(r.x, 0.0)) + tap(uv - vec2(r.x, 0.0))
          + tap(uv + vec2(0.0, r.y)) + tap(uv - vec2(0.0, r.y))
          + tap(uv + r * 0.707) + tap(uv - r * 0.707)
          + tap(uv + vec2(r.x, -r.y) * 0.707) + tap(uv + vec2(-r.x, r.y) * 0.707)) / 8.0;
}

// Light spilling over from bright spots within radius r: only what is really bright counts.
// Each ring is rotated against the last, so the halo comes out round instead of star-dotted.
vec3 spill(vec2 uv, vec2 r, float turn) {
    vec3 sum = vec3(0.0);
    for (int i = 0; i < 8; i++) {
        float a = float(i) * 0.785398 + turn;
        sum += max(tap(uv + vec2(cos(a), sin(a)) * r) - 0.8, 0.0);
    }
    return sum / 8.0;
}

void main() {
    float k = Intensity;
    float m = Mood;
    float t = DrunkTime;
    vec2 px = 1.0 / OutSize;
    vec2 uv = texCoord;

    // The world wobbles like looking through water.
    uv += vec2(sin(uv.y * 9.0 + t * 1.3), cos(uv.x * 7.0 + t * 1.1)) * 0.005 * k;

    // Double vision: a second image drifts apart and back together.
    vec2 ghost = vec2(sin(t * 0.7) + 0.35 * sin(t * 1.9), 0.4 * cos(t * 0.53)) * 0.022 * k;
    vec3 col = mix(tap(uv), tap(uv + ghost), 0.5 * smoothstep(0.0, 0.6, k));

    // Edges lose focus first.
    vec2 d = uv - 0.5;
    float edge = smoothstep(0.12, 0.7, length(d)) * k;
    col = mix(col, ring(uv, px * (1.0 + 7.0 * edge)), edge);

    // Beer goggles: soft focus, light bleeding into halos, rich warm colours.
    col = mix(col, ring(uv, px * 2.5), 0.25 * m);
    vec3 halo = spill(uv, px * 2.0, 0.0) * 1.2 + spill(uv, px * 4.0, 0.39) * 1.4 + spill(uv, px * 6.5, 0.2)
              * 1.6 + spill(uv, px * 9.5, 0.59) * 1.8 + spill(uv, px * 13.0, 0.1) * 2.0;
    col += halo * vec3(1.0, 0.8, 0.5) * m;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.0 + 0.35 * m);
    col *= mix(vec3(1.0), vec3(1.06, 1.02, 0.9), m);

    // Too much: washed-out, sickly warm colours.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.25 * k);
    col *= mix(vec3(1.0), vec3(1.08, 1.0, 0.85), k);

    // Tunnel vision.
    col *= 1.0 - smoothstep(0.3, 0.9, length(d * vec2(1.0, 0.8))) * 0.7 * k;

    // Afterimages: blend in the previous finished frame. Done last, so the steady picture is
    // exactly the processed one and only movement smears.
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    col = mix(col, prev, 0.12 * m + 0.6 * k);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
