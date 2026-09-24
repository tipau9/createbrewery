#version 150

// Drunk vision. Two inputs from DrunkClient:
//   Mood      0..1  the party zone (a beer or two): "beer goggles" - glow, halos around lights,
//                   richer, warmer colours. The world simply looks nicer.
//   (High is weed: its own look - vivid, clear colours, cool light glow, dreamy edges,
//    breathing surfaces and faint trails when strong. Deliberately unlike the beer look.)
//   Intensity 0..1  how drunk: wobble, double vision, blurred edges, tunnel, washed-out colours,
//                   and afterimages - the picture drags behind when you turn.
// PrevSampler is last frame's output (see post/drunk.json), which makes the afterimages once drunk.

uniform sampler2D DiffuseSampler;
uniform sampler2D PrevSampler;
uniform vec2 OutSize;
uniform float Intensity;
uniform float Mood;
uniform float DrunkTime;
uniform float Trail;
uniform float Stim;    // Koks: 0..1
uniform float Gray;    // the crash after Koks: 0..1
uniform float Dissoc;  // Keta: 0..1, 1 = K-Loch
uniform float High;    // Weed: 0..1
uniform float Green;   // greening out: 0..1

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

    // Keta: the world slowly swirls and drifts away from you. Very slow - under 0.1 Hz.
    vec2 c = uv - 0.5;
    float ang = Dissoc * 0.35 * sin(t * 0.15) * length(c) * 2.0;
    c = mat2(cos(ang), -sin(ang), sin(ang), cos(ang)) * c;
    uv = 0.5 + c * (1.0 - 0.08 * Dissoc);
    // Weed, from a strong high on: surfaces seem to breathe - a wide, slow swell across the
    // picture, about one breath every 10 s. Nothing like the beer wobble, and no double vision.
    float stoned = smoothstep(0.45, 1.0, High);
    uv += vec2(sin(uv.y * 3.0 + t * 0.6), sin(uv.x * 2.5 + t * 0.5 + 1.3)) * 0.0035 * stoned;
    // Greening out: the stomach turns and the picture heaves with it, slowly (about 0.15 Hz).
    uv.y += sin(t * 0.9) * 0.006 * Green * (0.5 + uv.x);

    // Only once properly drunk (not in the party zone): wobble and double vision. Both move
    // slowly; nothing here changes faster than about once a second.
    float heavy = smoothstep(0.45, 0.8, k);

    // The world wobbles like looking through water.
    uv += vec2(sin(uv.y * 9.0 + t * 1.3), cos(uv.x * 7.0 + t * 1.1)) * 0.005 * k * heavy;

    // Double vision: a second image drifts apart and back together.
    vec2 ghost = vec2(sin(t * 0.7) + 0.35 * sin(t * 1.9), 0.4 * cos(t * 0.53)) * 0.022 * k;
    vec3 col = mix(tap(uv), tap(uv + ghost), 0.5 * heavy);

    // Edges lose focus first.
    vec2 d = uv - 0.5;
    float edge = smoothstep(0.12, 0.7, length(d)) * k;
    col = mix(col, ring(uv, px * (1.0 + 7.0 * edge)), edge);

    // Beer goggles: soft focus, light bleeding into halos, rich warm colours.
    col = mix(col, ring(uv, px * 2.5), 0.25 * m);
    vec3 halo = spill(uv, px * 2.0, 0.0) * 0.8 + spill(uv, px * 4.0, 0.39) * 0.9 + spill(uv, px * 6.5, 0.2)
              * 1.0 + spill(uv, px * 9.5, 0.59) * 1.1 + spill(uv, px * 13.0, 0.1) * 1.2;
    col += halo * vec3(1.0, 0.8, 0.5) * m;
    float lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.0 + 0.35 * m);
    col *= mix(vec3(1.0), vec3(1.06, 1.02, 0.9), m);

    // Koks: everything over-sharp, bright and hard-edged.
    col += (col - ring(uv, px * 1.5)) * 0.9 * Stim;
    col = (col - 0.5) * (1.0 + 0.25 * Stim) + 0.5 + 0.03 * Stim;

    // The crash: grey and dull.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.65 * Gray) * (1.0 - 0.15 * Gray);

    // Keta: cold, faded, far away; in the K-Loch the edges close in to a dark hole.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.45 * Dissoc) * mix(vec3(1.0), vec3(0.85, 0.93, 1.08), Dissoc);
    float hole = clamp((Dissoc - 0.6) / 0.4, 0.0, 1.0);
    col *= 1.0 - smoothstep(0.12, 0.7, length(d)) * 0.85 * hole;

    // Weed: colours pop and details look more interesting (broad local contrast, not the hard
    // Koks sharpening), wide pupils let lights glow in a warm, sunny white, the whole world gets
    // a summery warmth, and the eyes fix on the middle while the edges go dreamy - soft, with a
    // faint colour fringe. Beer's gold halo is softer and blurrier; this stays crisp.
    // Clamped, so small bright lights do not get a dark ring around them.
    col += clamp((col - ring(uv, px * 7.0)) * 0.6, -0.01, 0.16) * High;
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    float sat = length(col - vec3(lum));
    col = mix(vec3(lum), col, 1.0 + (0.9 - 1.0 * clamp(sat, 0.0, 0.5)) * High); // vibrance: dull colours gain most
    vec3 glow = spill(uv, px * 3.0, 0.1) + spill(uv, px * 7.0, 0.5) * 1.2 + spill(uv, px * 12.0, 0.3) * 1.4;
    col += glow * vec3(1.0, 0.94, 0.8) * 1.1 * High;
    col *= mix(vec3(1.0), vec3(1.05, 1.02, 0.94), High); // summer warmth
    col += (1.0 - col) * col * 0.12 * High; // shadows open up a little
    float dream = smoothstep(0.25, 0.75, length(d)) * High;
    col = mix(col, ring(uv, px * 4.0), 0.45 * dream);
    col.r = mix(col.r, tap(uv + d * 0.006).r, 0.5 * dream);
    col.b = mix(col.b, tap(uv - d * 0.006).b, 0.5 * dream);

    // Greening out: the colour drains out of everything, pale and sick green, and the edges go dark.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.55 * Green) * mix(vec3(1.0), vec3(0.82, 1.06, 0.72), Green);
    col *= 1.0 - smoothstep(0.2, 0.8, length(d)) * 0.6 * Green;

    // Too much: washed-out, sickly warm colours.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.25 * k);
    col *= mix(vec3(1.0), vec3(1.08, 1.0, 0.85), k);

    // Tunnel vision.
    col *= 1.0 - smoothstep(0.3, 0.9, length(d * vec2(1.0, 0.8))) * 0.7 * k;

    // Afterimages: blend in the previous finished frame. Done last, so the steady picture is
    // exactly the processed one and only movement smears.
    vec3 prev = texture(PrevSampler, texCoord).rgb;
    // Trail is 0 on the first frames of a fresh chain, whose previous frame is still black.
    // Only when properly drunk: in the party zone the camera never rests (aim drift, sway, view
    // bobbing), and trailing ghost copies of every edge would look like constant trembling.
    // Weed: faint trails behind movement from a mild high on, stronger when stoned.
    col = mix(col, prev, (0.6 * k * heavy + 0.35 * Dissoc + 0.1 * High + 0.18 * stoned) * Trail);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
