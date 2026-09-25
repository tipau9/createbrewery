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
uniform float Trip;    // LSD, mushrooms, peyote together: 0..1
uniform float Organic; // share of the trip that is mushrooms: green, melting
uniform float Desert;  // share that is peyote: warm, shimmering
uniform float BadTrip; // fear: 0..1
uniform float Break;   // DMT breakthrough: 0..1
uniform float Roll;    // MDMA: 0..1
uniform float Tweak;   // Meth: 0..1
uniform float Nod;     // Heroin: 0..1
uniform float Wah;     // Lachgas: 0..1
// Where each pixel is in the world, so trip patterns can stick to surfaces instead of the screen.
// World is 1 once DrunkClient could hand over the camera; without it those effects stay off.
uniform sampler2D DiffuseDepthSampler;
uniform mat4 InvViewProj;  // screen to camera-relative world, this frame
uniform mat4 PrevViewProj; // camera-relative world to screen, last frame
uniform vec3 CamPos;       // camera position, wrapped to keep float precision
uniform vec3 CamDelta;     // how far the camera moved since last frame
uniform float World;
uniform float Stare;       // 0..1: how long the view has rested on one spot
uniform float Harsh;       // 0..1: a bad place to trip (Nether, caves, night)
uniform float Afterglow;   // 0..1: the clear, bright day after a trip

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

// Turns a colour around the grey axis by angle a (radians): the hue shifts, brightness stays.
vec3 hueShift(vec3 c, float a) {
    const vec3 k = vec3(0.57735);
    float ca = cos(a);
    return c * ca + cross(k, c) * sin(a) + k * dot(k, c) * (1.0 - ca);
}

float hash(vec2 p) {
    return fract(sin(dot(p, vec2(12.9898, 78.233))) * 43758.5453);
}

// Camera-relative world position of a screen point at the given depth.
vec3 worldAt(vec2 uv, float depth) {
    vec4 w = InvViewProj * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

float distAt(vec2 uv) {
    return length(worldAt(uv, texture(DiffuseDepthSampler, uv).r));
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

    // DMT: the world folds into a twelvefold kaleidoscope and streams towards you down a tunnel.
    // Done first, so every other effect is drawn on the folded picture.
    float aspect = OutSize.x / OutSize.y;
    vec2 bc = (uv - 0.5) * vec2(aspect, 1.0);
    float br = length(bc);
    float bseg = 6.2831853 / 12.0;
    float ba = abs(mod(atan(bc.y, bc.x) + t * 0.2, bseg) - bseg * 0.5);
    float depth = fract(0.15 / (br + 0.05) + t * 0.3);
    vec2 buv = vec2(cos(ba), sin(ba)) * mix(br, depth * 0.5, 0.6);
    uv = mix(uv, 0.5 + buv * vec2(1.0 / aspect, 1.0), smoothstep(0.0, 0.7, Break));

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

    // Psychedelics: surfaces breathe and crawl. Brightness steers the flow, so the patterns seem
    // to grow out of the textures instead of floating over them.
    float peak = smoothstep(0.55, 1.0, Trip);
    float luma0 = dot(tap(uv), vec3(0.299, 0.587, 0.114));
    vec2 flow = vec2(sin(uv.y * 14.0 + t * 0.8 + luma0 * 6.0), cos(uv.x * 12.0 + t * 0.7 + luma0 * 6.0));
    uv += flow * px * (2.0 + 5.0 * peak) * Trip;
    // Mushrooms: the world slowly melts downwards. Peyote: heat shimmer, like over desert sand.
    uv.y += sin(uv.x * 20.0 + t * 0.4) * 0.004 * Trip * Organic;
    uv.x += sin(uv.y * 60.0 + t * 3.0) * 0.0015 * Trip * Desert;
    // A bad trip: the picture pulls nervously towards the middle, again and again.
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.015 * BadTrip * (0.5 + 0.5 * sin(t * 4.0)));
    // ...and the walls lean in: the edges of the picture are pulled towards the middle.
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.07 * BadTrip * smoothstep(0.2, 0.7, length(uv - 0.5)));

    // Tripping, the world itself breathes: the waves are tied to places in the world, so a wall
    // keeps its own slow swell as you walk past it, and far walls swell more than near ones.
    float depth0 = texture(DiffuseDepthSampler, uv).r;
    vec3 rel = worldAt(uv, depth0);
    vec3 wp = rel + CamPos;
    float dist = length(rel);
    float sky = step(0.99999, depth0);
    float solid = (1.0 - sky) * World;
    float breathe = sin(wp.x * 0.8 + t * 0.9) * sin(wp.z * 0.8 - t * 0.7) + 0.5 * sin(wp.y * 1.1 + t * 0.6);
    uv += vec2(breathe, 0.7 * breathe) * px * (1.5 + 4.0 * smoothstep(4.0, 40.0, dist)) * Trip * solid;
    // Stare at one spot and it starts to melt: the middle of the view slowly turns and drips.
    float st = Stare * Trip;
    vec2 sc = (uv - 0.5) * vec2(aspect, 1.0);
    float held = smoothstep(0.35, 0.0, length(sc));
    float swirl = st * 0.9 * held * sin(t * 0.4);
    sc = mat2(cos(swirl), -sin(swirl), sin(swirl), cos(swirl)) * sc;
    uv = 0.5 + sc * vec2(1.0 / aspect, 1.0);
    uv.y -= st * held * 0.012 * (1.0 + sin(uv.x * 40.0 + t * 0.7));

    // MDMA: at its height the eyes flicker side to side (nystagmus), fast and tiny.
    uv.x += sin(t * 38.0) * 0.0012 * smoothstep(0.5, 1.0, Roll);
    // Meth: the picture twitches - now and then it jumps for a frame.
    float tick8 = floor(t * 8.0);
    float twitch = step(0.96, fract(sin(tick8 * 91.7) * 43758.5453));
    uv += (vec2(fract(sin(tick8 * 12.9) * 437.58), fract(sin(tick8 * 78.2) * 437.58)) - 0.5) * 0.012 * twitch * Tweak;

    // Lachgas: the picture pumps in and out with the wah-wah, about three times a second.
    float wahPulse = 0.5 + 0.5 * sin(t * 18.0);
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.04 * Wah * wahPulse);

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

    // Psychedelics: colours intensify and slowly cycle, most in what is already colourful.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    sat = length(col - vec3(lum));
    float shift = Trip * (0.6 * sin(t * 0.25) + 1.2 * peak * sin(t * 0.11 + lum * 4.0));
    col = mix(col, hueShift(col, shift), clamp(sat * 3.0, 0.0, 1.0));
    col = mix(vec3(lum), col, 1.0 + 0.8 * Trip);
    // At the peak, geometry: a slowly turning kaleidoscope of the picture itself shines through,
    // with a fine lattice of rainbow lines.
    vec2 kc = uv - 0.5;
    float kr = length(kc);
    float seg = 6.2831853 / 8.0;
    float ka = abs(mod(atan(kc.y, kc.x) + t * 0.05, seg) - seg * 0.5);
    col = mix(col, tap(0.5 + vec2(cos(ka), sin(ka)) * kr), 0.3 * peak);
    float lattice = smoothstep(0.92, 1.0, abs(sin(kr * 60.0 - t * 1.5) * sin(ka * 16.0)));
    vec3 rainbow = 0.5 + 0.5 * sin(vec3(t, t * 1.3 + 2.0, t * 0.7 + 4.0) + kr * 20.0);
    col += rainbow * lattice * 0.12 * peak;
    // Flowing lines drawn onto the surfaces themselves (not the screen), fading with distance.
    float wpat = sin(wp.x * 2.0 + 2.0 * sin(wp.z * 1.3 + t * 0.5)) + sin(wp.z * 2.0 + 2.0 * sin(wp.y * 1.7 - t * 0.4))
               + sin(wp.y * 2.0 + 2.0 * sin(wp.x * 1.1 + t * 0.3));
    float contour = smoothstep(0.8, 1.0, abs(sin(wpat * 3.14159)));
    vec3 wrainbow = 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.33, 0.67) + wpat * 0.3 + t * 0.05));
    col += wrainbow * contour * 0.16 * Trip * solid * exp(-dist / 40.0);
    // At the peak, every edge of the world glows in neon.
    float dc = distAt(uv);
    float dx = abs(distAt(uv + vec2(px.x, 0.0)) + distAt(uv - vec2(px.x, 0.0)) - 2.0 * dc);
    float dy = abs(distAt(uv + vec2(0.0, px.y)) + distAt(uv - vec2(0.0, px.y)) - 2.0 * dc);
    float outline = smoothstep(0.02, 0.12, (dx + dy) / max(dc, 0.5));
    col = mix(col, wrainbow * 1.2, outline * 0.55 * peak * solid);
    // Grass, leaves and water wander through other colours.
    float foliage = clamp((col.g - max(col.r, col.b)) * 6.0, 0.0, 1.0) + clamp((col.b - max(col.r, col.g)) * 4.0, 0.0, 1.0);
    col = mix(col, hueShift(col, 2.5 * sin(t * 0.12 + (wp.x + wp.z) * 0.04)), min(1.0, foliage) * 0.8 * Trip * solid);
    // The sky cycles through colours, and stars come out even by day.
    vec3 dir = normalize(rel);
    vec3 starCell = floor(dir * 150.0);
    float star = step(0.996, hash(starCell.xy + starCell.z * 17.0)) * (0.5 + 0.5 * sin(t * 3.0 + hash(starCell.yz) * 30.0));
    col = mix(col, hueShift(col, t * 0.3 + dir.y * 3.0) * 1.1, 0.7 * Trip * sky * World);
    col += vec3(star) * Trip * sky * World;
    // At the peak the picture mirrors itself, and the two halves slowly drift.
    float mirror = peak * smoothstep(0.3, 0.9, 0.5 + 0.5 * sin(t * 0.06));
    col = mix(col, tap(vec2(1.0 - uv.x + 0.03 * sin(t * 0.3), uv.y)), 0.45 * mirror);
    // Visual snow: fine coloured grain over everything.
    vec2 grainAt = floor(uv * OutSize / 2.0) + mod(floor(t * 24.0), 251.0) * 7.0;
    col += (vec3(hash(grainAt), hash(grainAt + 3.1), hash(grainAt + 5.7)) - 0.5) * 0.07 * Trip;
    // Once in a while at the peak, colours flip for a heartbeat (soft, never a full flash).
    float blink = fract(t / 23.0);
    col = mix(col, 1.0 - col, 0.45 * peak * smoothstep(0.0, 0.008, blink) * smoothstep(0.03, 0.012, blink));
    // A bad place makes it harsh: hard contrast, bloody light.
    col = (col - 0.5) * (1.0 + 0.3 * Harsh * Trip) + 0.5;
    col *= mix(vec3(1.0), vec3(1.12, 0.9, 0.85), Harsh * Trip);
    // Palettes: mushrooms lean green and earthy, peyote warm and golden.
    col *= mix(vec3(1.0), vec3(0.9, 1.1, 0.95), Organic * Trip);
    col *= mix(vec3(1.0), vec3(1.12, 1.0, 0.82), Desert * Trip);
    // The bad trip: drained and reddish, with the edges throbbing dark like a pulse.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.5 * BadTrip) * mix(vec3(1.0), vec3(1.1, 0.85, 0.85), BadTrip);
    col *= 1.0 - smoothstep(0.15, 0.75, length(d)) * (0.5 + 0.3 * sin(t * 5.0)) * BadTrip;

    // DMT: impossible, saturated colour, and a chrysanthemum of light blooming in the middle.
    float petals = sin(br * 40.0 - t * 4.0) * sin(ba * 24.0 + t);
    vec3 neon = 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.33, 0.67) + br * 2.0 - t * 0.2 + petals * 0.2));
    col = mix(col, hueShift(col, t * 0.8) * 1.3, 0.6 * Break);
    col = mix(col, neon, 0.35 * Break * smoothstep(0.6, 0.0, br));
    col += neon * smoothstep(0.85, 1.0, petals) * 0.25 * Break;

    // MDMA: everything soft and warm, lights bloom pink-gold, and bright things sparkle.
    col += (spill(uv, px * 3.0, 0.3) + spill(uv, px * 8.0, 0.7) * 1.2) * vec3(1.0, 0.75, 0.85) * 1.2 * Roll;
    col *= mix(vec3(1.0), vec3(1.08, 1.0, 1.04), Roll);
    vec2 cell = floor(uv * OutSize / 5.0);
    float cellHash = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
    float twinkle = pow(max(0.0, sin(t * 3.0 + cellHash * 40.0)), 16.0) * step(0.9, cellHash);
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col += vec3(1.0, 0.9, 1.0) * twinkle * smoothstep(0.5, 0.9, lum) * 0.8 * Roll;
    // Meth: cold, clinical light, and colours tear apart at the edges.
    col *= mix(vec3(1.0), vec3(0.92, 1.0, 1.1), Tweak);
    col.r = mix(col.r, tap(uv + d * 0.01).r, 0.6 * Tweak);
    col.b = mix(col.b, tap(uv - d * 0.01).b, 0.6 * Tweak);

    // Heroin: soft, warm and dim - pinpoint pupils let little light in, and the edges sink
    // into a warm dark like a blanket pulled up.
    col = mix(col, ring(uv, px * 3.0), 0.5 * Nod);
    col *= mix(vec3(1.0), vec3(1.1, 0.95, 0.8), Nod) * (1.0 - 0.25 * Nod);
    col *= 1.0 - smoothstep(0.2, 0.8, length(d)) * 0.6 * Nod;

    // Lachgas: the world shrinks to a bright tunnel, far away, echoing.
    col = mix(col, tap(0.5 + (uv - 0.5) * 0.92), 0.35 * Wah * wahPulse);
    col *= 1.0 - smoothstep(0.15, 0.6, length(d)) * 0.8 * Wah;

    // Greening out: the colour drains out of everything, pale and sick green, and the edges go dark.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.55 * Green) * mix(vec3(1.0), vec3(0.82, 1.06, 0.72), Green);
    col *= 1.0 - smoothstep(0.2, 0.8, length(d)) * 0.6 * Green;

    // The afterglow: the day after a trip, everything looks clear and freshly washed.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.0 + 0.3 * Afterglow);
    col += (col - ring(uv, px * 4.0)) * 0.35 * Afterglow;

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
    // Tripping: long tracers behind everything that moves.
    col = mix(col, prev, min(0.85, 0.6 * k * heavy + 0.35 * Dissoc + 0.1 * High + 0.18 * stoned + 0.4 * Trip * (1.0 - World) + 0.3 * Break) * Trail);
    // Tripping: tracers behind everything that moves - mobs, drops, other players. Last frame is
    // looked up where this spot of the world was on screen then, so standing things stay sharp
    // while you look around, and only real movement leaves ghost copies behind.
    vec4 was = PrevViewProj * vec4(worldAt(texCoord, texture(DiffuseDepthSampler, texCoord).r) + CamDelta, 1.0);
    vec2 wasUv = was.xy / was.w * 0.5 + 0.5;
    float onScreen = step(0.0, was.w) * step(0.0, wasUv.x) * step(wasUv.x, 1.0) * step(0.0, wasUv.y) * step(wasUv.y, 1.0);
    col = mix(col, texture(PrevSampler, wasUv).rgb, min(0.8, 0.55 * Trip) * Trail * World * onScreen);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
