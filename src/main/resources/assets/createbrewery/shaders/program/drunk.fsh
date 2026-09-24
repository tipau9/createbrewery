#version 150

// Drunk vision. Intensity 0..1 comes from the blood level (see DrunkClient).
// Wavy warp, a drifting double image, blurred edges, a narrowing tunnel and a warm, washed-out tint.

uniform sampler2D DiffuseSampler;
uniform vec2 OutSize;
uniform float Intensity;
uniform float DrunkTime;

in vec2 texCoord;

out vec4 fragColor;

vec3 tap(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

void main() {
    float k = Intensity;
    float t = DrunkTime;
    vec2 uv = texCoord;

    // The world wobbles like looking through water.
    uv += vec2(sin(uv.y * 9.0 + t * 1.3), cos(uv.x * 7.0 + t * 1.1)) * 0.005 * k;

    // Double vision: a second image drifts apart and back together.
    vec2 ghost = vec2(sin(t * 0.7) + 0.35 * sin(t * 1.9), 0.4 * cos(t * 0.53)) * 0.022 * k;
    vec3 col = mix(tap(uv), tap(uv + ghost), 0.5 * smoothstep(0.0, 0.6, k));

    // Edges lose focus first.
    vec2 d = uv - 0.5;
    float edge = smoothstep(0.12, 0.7, length(d)) * k;
    vec2 px = (1.0 + 7.0 * edge) / OutSize;
    vec3 blur = (tap(uv + vec2(px.x, 0.0)) + tap(uv - vec2(px.x, 0.0))
               + tap(uv + vec2(0.0, px.y)) + tap(uv - vec2(0.0, px.y))
               + tap(uv + px) + tap(uv - px)
               + tap(uv + vec2(px.x, -px.y)) + tap(uv + vec2(-px.x, px.y))) / 8.0;
    col = mix(col, blur, edge);

    // Washed-out, warm colours.
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.25 * k);
    col *= mix(vec3(1.0), vec3(1.08, 1.0, 0.85), k);

    // Tunnel vision.
    col *= 1.0 - smoothstep(0.3, 0.9, length(d * vec2(1.0, 0.8))) * 0.7 * k;

    fragColor = vec4(col, 1.0);
}
