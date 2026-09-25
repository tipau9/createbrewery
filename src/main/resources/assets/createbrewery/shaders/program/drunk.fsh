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
uniform float Tired;   // Meth, awake too long (or psychotic): the edges of the view lie, 0..1
uniform float Nod;     // Heroin: 0..1
uniform float Flood;   // Heroin: the rush after a shot, warmth rolling up from below, 0..1
uniform float Dream;   // Heroin: on the nod, a dream behind the closed eyes, 0..1
uniform float Breath;  // Heroin: each slow breath, 0..1 (1 = breathing in)
uniform float Air;     // Heroin: the breath failing - blue and dark between breaths, 0..1
uniform float Sick;    // Heroin withdrawal (cold turkey): cold, clammy, gooseflesh, 0..1
uniform float Wah;     // Lachgas: 0..1
uniform float Rush;    // MDMA: a wave of euphoria washing over, 0..1
uniform float Beat;    // MDMA: how much the music is in the body, 0..1
uniform float Kick;    // the kick drum heard right now, 0..1 (see MusicPulse); 0 without music
uniform float Level;   // how loud the song is right now, against its own loudest, 0..1
uniform float Hats;    // hi-hats, claps and snares heard right now, 0..1
uniform float Wiggle;  // MDMA: the eyes flicker (nystagmus), in bursts, 0..1
uniform float Zap;     // the Tiefpunkt after MDMA: a brain zap, a jolt of a few frames, 0..1
uniform float Faded;   // MDMA wearing off: the closed-eye patterns of the offset, 0..1
uniform float Scene;   // MDMA, very high: the place turns into a dance floor for a while, 0..1
uniform float Tension; // the song's build-up: the kick is gone, everyone waits, 0..1 (see MusicPulse)
uniform float Drop;    // the drop: 1 when the kick comes back after a build-up, dying away
uniform float Peak;    // MDMA, the peak stage (third dose): the music takes over, 0..1
uniform float Beats;   // kicks counted, 0..63: the club light colour moves on with each
uniform float Tempo;   // beats a minute of what is heard (120 until learnt)
uniform float Heat;    // MDMA: overheating from dancing, 0..1
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
vec3 hash3(vec3 p) {
    return vec3(hash(p.xy + p.z * 7.13), hash(p.yz + p.x * 3.31), hash(p.zx + p.y * 5.97));
}

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
    uv.y += sin(uv.x * 20.0 + t * 0.4) * 0.004 * Trip * Organic * (1.0 - World);
    uv.x += sin(uv.y * 60.0 + t * 3.0) * 0.0015 * Trip * Desert * (1.0 - World);
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
    // Mushrooms: the breath follows a slow heartbeat, about fifty a minute.
    float beat = 1.0 + 0.8 * Organic * pow(0.5 + 0.5 * sin(t * 5.2), 8.0);
    uv += vec2(breathe, 0.7 * breathe) * px * (1.5 + 4.0 * smoothstep(4.0, 40.0, dist)) * Trip * solid * beat;
    // ...and surfaces run downwards like wet paint, in streaks that stay on their blocks.
    float run = 0.5 + 0.5 * sin(wp.x * 3.1 + 2.0 * sin(wp.z * 2.3) + t * 0.3);
    uv.y += run * run * 0.006 * Trip * Organic * solid;
    // Peyote: desert heat shimmer rising from the ground, waving upward and stronger at a distance.
    float heatDist = smoothstep(3.0, 35.0, dist);
    float groundShimmer = sin(wp.x * 3.0 + wp.z * 3.0 + t * 4.5 - wp.y * 5.0) * cos(wp.x * 2.0 - t * 2.5);
    float groundFade = clamp(1.2 - rel.y * 0.2, 0.2, 1.5);
    uv.x += groundShimmer * px.x * 4.0 * heatDist * groundFade * Trip * Desert * solid;
    uv.y += abs(groundShimmer) * px.y * 2.5 * heatDist * groundFade * Trip * Desert * solid;
    // Stare at one spot and it starts to melt: the middle of the view slowly turns and drips.
    float st = Stare * Trip;
    vec2 sc = (uv - 0.5) * vec2(aspect, 1.0);
    float held = smoothstep(0.35, 0.0, length(sc));
    float swirl = st * 0.9 * held * sin(t * 0.4);
    sc = mat2(cos(swirl), -sin(swirl), sin(swirl), cos(swirl)) * sc;
    uv = 0.5 + sc * vec2(1.0 / aspect, 1.0);
    uv.y -= st * held * 0.012 * (1.0 + sin(uv.x * 40.0 + t * 0.7));

    // MDMA: now and then the eyes flicker side to side for a few seconds (nystagmus).
    uv.x += sin(t * 60.0) * 0.004 * Wiggle;
    // At high doses surfaces drift and breathe a little too - much less than on a trip.
    uv += vec2(breathe, 0.7 * breathe) * px * 1.5 * smoothstep(0.6, 1.0, Roll) * solid;
    // A brain zap: the picture jumps sideways for a moment.
    uv.x += (hash(vec2(floor(t * 30.0), 3.7)) - 0.5) * 0.04 * Zap;
    // The beat is in the picture: it pumps with every kick of the song playing.
    float kick = Kick * Beat;
    // Techno (from about 125 to the minute): harder, darker, colder - short, sharp hits instead
    // of a big soft pump, which would only turn into seasickness at four kicks a second.
    float techno = smoothstep(118.0, 132.0, Tempo) * min(1.0, Beat * 2.0);
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.025 * (1.0 - 0.4 * techno) * kick);
    // The peak: every kick slams the whole picture - a hard jolt, a new way each beat.
    float slam = Peak * min(kick, 1.5);
    vec2 jolt = fract(sin(vec2(Beats * 12.9898, Beats * 78.233)) * 43758.5453) - 0.5;
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.04 * slam) + jolt * 0.035 * slam;
    // The build-up draws you in, and the drop throws it all wide open.
    float raving = min(1.0, Beat * 2.0);
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.04 * Tension * raving + 0.05 * Drop * Drop * raving);
    // A rush pulls you in, gently.
    uv = 0.5 + (uv - 0.5) * (1.0 - 0.02 * Rush);
    // Overheated: the air itself wobbles.
    uv += vec2(sin(uv.y * 50.0 + t * 6.0), cos(uv.x * 40.0 + t * 5.0)) * 0.0015 * Heat;
    // Meth: the picture twitches - now and then it jumps for a frame.
    float tick8 = floor(t * 8.0);
    float twitch = step(0.96, fract(sin(tick8 * 91.7) * 43758.5453));
    uv += (vec2(fract(sin(tick8 * 12.9) * 437.58), fract(sin(tick8 * 78.2) * 437.58)) - 0.5) * 0.012 * twitch * Tweak;
    // Cold turkey: the legs will not keep still, and neither does the view.
    uv += (vec2(hash(vec2(floor(t * 20.0), 1.0)), hash(vec2(floor(t * 20.0), 2.0))) - 0.5) * 0.002 * Sick;
    // ...and the eyes vibrate: a fine, fast buzz up and down, all the time (clenched jaw, nystagmus).
    uv.y += sin(t * 95.0) * 0.0012 * Tweak;
    // Sleepless, surfaces start to crawl: tiny patches shift about, a few times a second.
    uv += (vec2(hash(floor(uv * 90.0) + floor(t * 7.0)), hash(floor(uv * 90.0) + floor(t * 7.0) + 5.3)) - 0.5) * px * 2.5 * Tired * solid;

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
    // MDMA, high doses: the eyes no longer quite agree - a faint second image, just beside.
    float mdmaDouble = smoothstep(0.7, 1.0, Roll);
    if (mdmaDouble > 0.0) col = mix(col, tap(uv + vec2(0.005 + 0.002 * sin(t * 0.4), 0.0012)), 0.3 * mdmaDouble);
    // Meth, sleepless: the eyes drift apart, a second picture up and to the side.
    if (Tired > 0.0) col = mix(col, tap(uv + vec2(0.006, -0.004) * (0.7 + 0.3 * sin(t * 0.35))), 0.3 * Tired);

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
    // Everything electric here is LSD's; mushrooms (Organic) and peyote (Desert) have their own look.
    float lsdLook = clamp(1.0 - Organic - Desert, 0.0, 1.0);
    float shift = Trip * lsdLook * (0.6 * sin(t * 0.25) + 1.2 * peak * sin(t * 0.11 + lum * 4.0));
    col = mix(col, hueShift(col, shift), clamp(sat * 3.0, 0.0, 1.0));
    col = mix(vec3(lum), col, 1.0 + (0.8 - 0.5 * Organic + 0.3 * Desert) * Trip);
    // Mushrooms: soft focus, and every light blooms into a warm, gentle glow.
    float soft = Organic * Trip;
    col = mix(col, ring(uv, px * 3.0), 0.3 * soft);
    col += (spill(uv, px * 5.0, 0.2) + spill(uv, px * 10.0, 0.6) * 1.3) * vec3(1.0, 0.92, 0.7) * 1.2 * soft;
    // Peyote: wide pupils create cross-shaped starburst spill from bright lights with slow turning rays.
    float mescBloom = Desert * Trip;
    if (mescBloom > 0.01) {
        vec3 starSpill = vec3(0.0);
        float starRot = t * 0.06;
        vec2 dir1 = vec2(cos(starRot), sin(starRot));
        vec2 dir2 = vec2(-dir1.y, dir1.x);
        for (int s = 1; s <= 5; s++) {
            float off = float(s) * 3.5;
            starSpill += max(tap(uv + dir1 * px * off) - 0.75, 0.0);
            starSpill += max(tap(uv - dir1 * px * off) - 0.75, 0.0);
            starSpill += max(tap(uv + dir2 * px * off) - 0.75, 0.0);
            starSpill += max(tap(uv - dir2 * px * off) - 0.75, 0.0);
        }
        starSpill *= 0.1;
        col += starSpill * vec3(1.0, 0.88, 0.55) * 1.4 * mescBloom;
    }
    // At the peak, geometry: a slowly turning kaleidoscope of the picture itself shines through,
    // with a fine lattice of rainbow lines.
    vec2 kc = uv - 0.5;
    float kr = length(kc);
    float seg = 6.2831853 / 8.0;
    float ka = abs(mod(atan(kc.y, kc.x) + t * 0.05, seg) - seg * 0.5);
    // Mushrooms fold it into a slow spiral instead, like a snail shell or a fern.
    float sa = atan(kc.y, kc.x) + log(kr + 0.001) * 2.0 - t * 0.1;
    col = mix(col, tap(0.5 + mix(vec2(cos(ka), sin(ka)), vec2(cos(sa), sin(sa)), Organic) * kr), 0.3 * peak);
    float lattice = smoothstep(0.92, 1.0, abs(sin(kr * 60.0 - t * 1.5) * sin(ka * 16.0)));
    vec3 rainbow = 0.5 + 0.5 * sin(vec3(t, t * 1.3 + 2.0, t * 0.7 + 4.0) + kr * 20.0);
    col += rainbow * lattice * 0.12 * peak * lsdLook;
    // Flowing lines drawn onto the surfaces themselves (not the screen), fading with distance.
    float wpat = sin(wp.x * 2.0 + 2.0 * sin(wp.z * 1.3 + t * 0.5)) + sin(wp.z * 2.0 + 2.0 * sin(wp.y * 1.7 - t * 0.4))
               + sin(wp.y * 2.0 + 2.0 * sin(wp.x * 1.1 + t * 0.3));
    float contour = smoothstep(0.8, 1.0, abs(sin(wpat * 3.14159)));
    vec3 wrainbow = 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.33, 0.67) + wpat * 0.3 + t * 0.05));
    col += wrainbow * contour * 0.16 * Trip * lsdLook * solid * exp(-dist / 40.0);
    // Peyote: Native American weaving patterns on surfaces - stepped zigzags and diamonds in red, orange, yellow, turquoise.
    float weave = Desert * Trip * solid;
    if (weave > 0.01) {
        vec2 wCoord = vec2(wp.x + wp.z, wp.y * 1.4 + (wp.x - wp.z) * 0.5);
        vec2 wFrac = abs(fract(wCoord) - 0.5);
        float diamond = abs(wFrac.x + wFrac.y - 0.5);
        float zigzag = abs(fract(wCoord.x * 2.0 + floor(wCoord.y * 2.0) * 0.5) - 0.5);
        float wPattern = smoothstep(0.09, 0.02, min(diamond, zigzag));
        float pIdx = fract((wp.x + wp.y * 0.5 + wp.z) * 0.25 + t * 0.03);
        vec3 wCol = pIdx < 0.25 ? vec3(0.85, 0.18, 0.12)
                  : pIdx < 0.5 ? vec3(0.96, 0.52, 0.12)
                  : pIdx < 0.75 ? vec3(1.0, 0.82, 0.22)
                  : vec3(0.12, 0.75, 0.68);
        col += wCol * wPattern * 0.24 * weave * exp(-dist / 35.0);
    }
    // Mushrooms: a glowing mycelium grows over everything - thin veins between cells anchored in
    // the world, with light pulsing along them.
    float fungal = Organic * Trip * solid;
    if (fungal > 0.01) {
        vec3 cell = wp * 1.3;
        vec3 cid = floor(cell);
        float f1 = 9.0, f2 = 9.0;
        for (int x = -1; x <= 1; x++) for (int y = -1; y <= 1; y++) for (int z = -1; z <= 1; z++) {
            vec3 o = vec3(float(x), float(y), float(z));
            vec3 p = o + hash3(cid + o) - fract(cell);
            float dd = dot(p, p);
            if (dd < f1) { f2 = f1; f1 = dd; } else if (dd < f2) f2 = dd;
        }
        float vein = 1.0 - smoothstep(0.0, 0.07, sqrt(f2) - sqrt(f1));
        float pulse = 0.5 + 0.5 * sin(dot(wp, vec3(0.7, 0.3, 0.5)) * 2.0 - t * 3.0);
        vec3 glowCol = mix(vec3(0.3, 0.95, 1.0), vec3(0.75, 0.4, 1.0), 0.5 + 0.5 * sin(t * 0.2 + wp.y));
        col += glowCol * vein * (0.3 + 0.7 * pulse) * 0.35 * fungal * exp(-dist / 25.0);
    }
    // At the peak, every edge of the world glows in neon.
    float dc = distAt(uv);
    float dx = abs(distAt(uv + vec2(px.x, 0.0)) + distAt(uv - vec2(px.x, 0.0)) - 2.0 * dc);
    float dy = abs(distAt(uv + vec2(0.0, px.y)) + distAt(uv - vec2(0.0, px.y)) - 2.0 * dc);
    float outline = smoothstep(0.02, 0.12, (dx + dy) / max(dc, 0.5));
    col = mix(col, wrainbow * 1.2, outline * 0.55 * peak * lsdLook * solid);
    // Peyote: "Istigkeit" - things within ~5 blocks get sharper with a thin gold rim; distant things get a warm golden haze.
    float nearObj = smoothstep(5.5, 1.2, dist) * solid;
    col += clamp((col - ring(uv, px * 2.0)) * 0.7, -0.05, 0.2) * nearObj * Trip * Desert;
    col = mix(col, vec3(1.0, 0.82, 0.25) * 1.3, outline * 0.65 * nearObj * Trip * Desert);
    float farObj = smoothstep(8.0, 45.0, dist) * solid;
    col = mix(col, mix(col, vec3(0.95, 0.75, 0.45), 0.35), farObj * 0.5 * Trip * Desert);
    // Grass, leaves and water wander through other colours.
    float foliage = clamp((col.g - max(col.r, col.b)) * 6.0, 0.0, 1.0) + clamp((col.b - max(col.r, col.g)) * 4.0, 0.0, 1.0);
    col = mix(col, hueShift(col, 2.5 * sin(t * 0.12 + (wp.x + wp.z) * 0.04)), min(1.0, foliage) * 0.8 * Trip * lsdLook * solid);
    // Mushrooms: plants look alive instead - lush, deep and glowing from within, pulsing slowly.
    float alive = min(1.0, foliage) * Organic * Trip * solid * (0.75 + 0.25 * sin(t * 1.1 + wp.x * 0.3 + wp.z * 0.2));
    col = mix(col, col * vec3(0.8, 1.3, 0.85) + vec3(0.02, 0.07, 0.03), 0.7 * alive);
    // The sky cycles through colours, and stars come out even by day.
    vec3 dir = normalize(rel);
    vec3 starCell = floor(dir * 150.0);
    float star = step(0.996, hash(starCell.xy + starCell.z * 17.0)) * (0.5 + 0.5 * sin(t * 3.0 + hash(starCell.yz) * 30.0));
    col = mix(col, hueShift(col, t * 0.3 + dir.y * 3.0) * 1.1, 0.7 * Trip * lsdLook * sky * World);
    col += vec3(star) * Trip * lsdLook * sky * World;
    // Mushrooms: soft veils of green and violet light drift across the sky, like an aurora.
    float aurora = smoothstep(0.05, 0.5, dir.y) * pow(0.5 + 0.5 * sin(dir.x * 5.0 + 2.0 * sin(dir.z * 4.0 + t * 0.25) + t * 0.15), 3.0);
    col += mix(vec3(0.15, 0.9, 0.55), vec3(0.6, 0.3, 0.9), 0.5 + 0.5 * sin(dir.z * 3.0 + t * 0.1)) * aurora * 0.45 * Organic * Trip * sky * World;
    // Peyote: golden sunset gradient by day, clear starry sky at night.
    float horizon = smoothstep(0.45, 0.0, abs(dir.y));
    vec3 sunsetSky = mix(vec3(1.15, 0.75, 0.35), vec3(1.25, 0.52, 0.2), horizon);
    float daySky = smoothstep(0.1, 0.4, dot(col, vec3(0.333)));
    col = mix(col, col * sunsetSky, 0.65 * daySky * Desert * Trip * sky * World);
    col += vec3(star) * 1.2 * (1.0 - daySky) * Desert * Trip * sky * World;
    // At the peak the picture mirrors itself, and the two halves slowly drift.
    float mirror = peak * smoothstep(0.3, 0.9, 0.5 + 0.5 * sin(t * 0.06));
    col = mix(col, tap(vec2(1.0 - uv.x + 0.03 * sin(t * 0.3), uv.y)), 0.45 * mirror * lsdLook);
    // Visual snow: fine coloured grain over everything.
    vec2 grainAt = floor(uv * OutSize / 2.0) + mod(floor(t * 24.0), 251.0) * 7.0;
    col += (vec3(hash(grainAt), hash(grainAt + 3.1), hash(grainAt + 5.7)) - 0.5) * 0.07 * Trip * lsdLook;
    // Once in a while at the peak, colours flip for a heartbeat (soft, never a full flash).
    float blink = fract(t / 23.0);
    col = mix(col, 1.0 - col, 0.45 * peak * lsdLook * smoothstep(0.0, 0.008, blink) * smoothstep(0.03, 0.012, blink));
    // A bad place makes it harsh: hard contrast, bloody light.
    col = (col - 0.5) * (1.0 + 0.3 * Harsh * Trip) + 0.5;
    col *= mix(vec3(1.0), vec3(1.12, 0.9, 0.85), Harsh * Trip);
    // Palettes: mushrooms earthy - shadows sink into violet, highlights go gold-green. Peyote: turquoise shadows, orange-gold highlights.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col *= mix(vec3(1.0), mix(vec3(0.95, 0.85, 1.12), vec3(1.0, 1.1, 0.88), smoothstep(0.2, 0.7, lum)), Organic * Trip);
    vec3 desertGrade = mix(vec3(0.78, 1.05, 1.05), vec3(1.2, 0.98, 0.75), smoothstep(0.15, 0.7, lum));
    col *= mix(vec3(1.0), desertGrade, Desert * Trip);
    // Peyote: Klüver's form constants & cathedral stained glass blooming in darkness.
    float darkEye = smoothstep(0.18, 0.01, lum) * Desert * Trip;
    if (darkEye > 0.01) {
        vec2 cGrid = uv * vec2(aspect, 1.0) * 14.0;
        vec2 cHex = abs(fract(cGrid) - 0.5);
        float hex = smoothstep(0.08, 0.01, abs(max(cHex.x * 1.5 + cHex.y, cHex.y * 2.0) - 1.0));
        float facetIdx = fract(sin(dot(floor(cGrid), vec2(12.9898, 78.233))) * 43758.5453 + t * 0.02);
        vec3 stainedCol = facetIdx < 0.25 ? vec3(0.85, 0.1, 0.15)
                        : facetIdx < 0.5 ? vec3(0.1, 0.35, 0.9)
                        : facetIdx < 0.75 ? vec3(0.1, 0.85, 0.35)
                        : vec3(1.0, 0.82, 0.2);
        col += stainedCol * hex * 0.45 * darkEye;
    }
    // Tatewari fire trance: staring deepens into a warm charcoal vignette with pulsing embers.
    float trance = Stare * Desert * Trip;
    if (trance > 0.01) {
        float firePulse = 0.5 + 0.5 * sin(t * 3.14159);
        col = mix(col, col * vec3(1.15, 0.88, 0.65), trance * 0.35);
        col *= 1.0 - smoothstep(0.25, 0.8, length(d)) * (0.4 + 0.15 * firePulse) * trance;
    }
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
    // The glow breathes with the loudness of the music.
    col += (spill(uv, px * 3.0, 0.3) + spill(uv, px * 8.0, 0.7) * 1.2) * vec3(1.0, 0.75, 0.85) * 1.2 * Roll * (1.0 + 0.8 * Beat * Level);
    col *= mix(vec3(1.0), vec3(1.08, 1.0, 1.04), Roll);
    vec2 cell = floor(uv * OutSize / 5.0);
    float cellHash = fract(sin(dot(cell, vec2(12.9898, 78.233))) * 43758.5453);
    float twinkle = pow(max(0.0, sin(t * 3.0 + cellHash * 40.0)), 16.0) * step(0.9, cellHash);
    // With music the sparkles go off on the hi-hats instead, a different handful each time.
    float hatSpark = step(0.9, cellHash) * step(0.6, fract(cellHash * 17.0 + floor(t * 6.0) * 0.37)) * Hats * 1.5;
    twinkle = mix(twinkle, hatSpark, Beat);
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col += vec3(1.0, 0.9, 1.0) * twinkle * smoothstep(0.5, 0.9, lum) * 0.8 * Roll;
    // A rush: a warm, bright wave of pink light rolls out from the middle, colours swell.
    float wave = smoothstep(0.15, 0.0, abs(length(d * vec2(aspect, 1.0)) - (1.0 - Rush) * 1.2));
    col += vec3(1.0, 0.6, 0.85) * (wave * 0.35 + 0.12) * Rush;
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.0 + 0.4 * Rush);
    // Wide pupils: lights are too bright, with long thin starburst rays, flaring on every kick.
    if (Roll > 0.01) {
        vec3 rays = vec3(0.0);
        float rayLen = (0.025 + 0.04 * kick) * Roll;
        for (int i = 1; i <= 4; i++) {
            float f = float(i) / 4.0;
            vec2 o = vec2(rayLen * f / aspect, rayLen * f);
            rays += (max(tap(uv + vec2(o.x, 0.0)) - 0.85, 0.0) + max(tap(uv - vec2(o.x, 0.0)) - 0.85, 0.0)
                   + max(tap(uv + vec2(0.0, o.y)) - 0.85, 0.0) + max(tap(uv - vec2(0.0, o.y)) - 0.85, 0.0)) * (1.0 - f);
        }
        col += rays * vec3(1.0, 0.85, 0.95) * 0.9 * Roll;
    }
    // Every kick: the lights flare in club colours that move on with each beat, the picture
    // brightens, colours punch.
    // In techno the colour moves on once a bar, and the light is colder, whiter.
    float lightStep = mix(Beats, floor(Beats / 4.0), techno);
    vec3 club = mix(vec3(1.0, 0.7, 0.9), 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.33, 0.67) + lightStep * 0.17)), 0.7);
    club = mix(club, vec3(0.85, 0.92, 1.0), 0.5 * techno);
    col += spill(uv, px * 10.0, 0.4) * club * 2.5 * kick;
    // Techno: the room sits in the dark between the kicks and each one lights it like a strobe
    // hit - once per kick, never faster.
    col *= 1.0 - 0.25 * techno * (1.0 - min(kick, 1.0));
    col += vec3(0.9, 0.95, 1.0) * 0.14 * pow(min(kick, 1.0), 3.0) * techno;
    // At the peak a white strobe hit on every kick, whatever the tempo.
    col += vec3(1.0) * 0.16 * pow(min(kick, 1.0), 3.0) * Peak;
    col *= 1.0 + 0.2 * kick;
    // Colours are richer, and more so the louder the music - above all bright, neon colours.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    float chroma = max(col.r, max(col.g, col.b)) - min(col.r, min(col.g, col.b));
    col = mix(vec3(lum), col, 1.0 + (0.25 * Roll + 0.5 * Beat * Level + 0.4 * kick) * smoothstep(0.05, 0.4, chroma));
    // The build-up: colour drains, the edges close in, the middle gets brighter and brighter...
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.35 * Tension * raving) * (1.0 + 0.25 * Tension * raving);
    col *= 1.0 - smoothstep(0.25, 0.8, length(d)) * 0.5 * Tension * raving;
    // ...and the drop: a burst of light rolling out, and every colour at once.
    float burst = smoothstep(0.2, 0.0, abs(length(d * vec2(aspect, 1.0)) - (1.0 - Drop) * 1.4));
    col += (club * burst * 0.6 + vec3(1.0, 0.85, 0.95) * 0.35 * Drop) * Drop * raving;
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(vec3(lum), col, 1.0 + 0.8 * Drop * raving);
    // In the quiet bits the room closes in a little; when it gets loud it opens up.
    col *= 1.0 - smoothstep(0.3, 0.9, length(d)) * 0.25 * Beat * (1.0 - Level);
    // Eye wiggles: the picture blurs into a faint flickering double.
    col = mix(col, tap(uv + vec2(0.006 * sin(t * 60.0 + 1.5), 0.0)), 0.35 * Wiggle);
    if (Roll > 0.01) {
        lum = dot(col, vec3(0.299, 0.587, 0.114));
        float dark = 1.0 - smoothstep(0.05, 0.35, lum);
        // Wide pupils: more light gets in - the dark opens up, bright things glare.
        col = pow(max(col, 0.0), vec3(1.0 - 0.2 * Roll)) * (1.0 + 0.08 * Roll);
        // Hard to focus: now and then everything goes soft for a few seconds.
        float blur = smoothstep(0.5, 1.0, Roll) * smoothstep(0.3, 0.9, sin(t * 0.37) * sin(t * 0.23 + 1.0) * 2.0);
        col = mix(col, ring(uv, px * 3.5), 0.6 * blur);

        // Symmetrical texture repetition: at high doses rough surfaces (grass, bark, carpet)
        // mirror over themselves, most at the edge of the view, and finer the longer you stare.
        float detail = length(col - ring(uv, px * 2.0));
        float symm = smoothstep(0.55, 1.0, Roll) * smoothstep(0.2, 0.55, length(d)) * (0.35 + 0.65 * Stare)
            * smoothstep(0.03, 0.12, detail) * solid;
        if (symm > 0.01) {
            vec2 cellSize = px * 24.0 / (1.0 + 1.5 * Stare);
            vec2 cellId = floor(uv / cellSize);
            vec2 mirrored = (cellId + 0.5 - abs(fract(uv / cellSize) - 0.5)) * cellSize;
            col = mix(col, tap(mirrored) * (1.0 + 0.1 * Roll), 0.6 * symm);
        }

        // Geometry: a flat veil just before the eyes - dim, organic, blue-grey, mostly seen in the
        // dark. Faint at the height of high doses; it comes on as it wears off, like closed-eye
        // visuals. Below it, noise and slow clouds of colour.
        float geo = Roll * (0.3 * smoothstep(0.6, 1.0, Roll) + Faded) * (0.3 + 0.7 * dark);
        if (geo > 0.01) {
            vec2 g = (uv - 0.5) * vec2(aspect, 1.0) * 7.0;
            g += 0.6 * vec2(sin(g.y * 1.3 + t * 0.2), cos(g.x * 1.1 - t * 0.17));
            float lines = abs(sin(g.x) * sin(g.y) + 0.5 * sin(length(g) * 2.0 - t * 0.3));
            col += smoothstep(0.12, 0.0, lines) * vec3(0.35, 0.45, 0.7) * 0.35 * geo;
            float cloud = smoothstep(0.5, 1.0, sin(g.x * 0.4 + t * 0.3) * sin(g.y * 0.5 - t * 0.25));
            col += cloud * (0.5 + 0.5 * cos(6.2831853 * (vec3(0.6, 0.7, 0.8) + t * 0.05))) * 0.12 * geo;
            col += (hash(uv * OutSize + floor(t * 20.0)) - 0.5) * 0.06 * geo;
        }

        // Very high doses: the place turns into a dance floor for a while - the floor under you
        // lights up in tiles that change and flash with the music.
        if (Scene > 0.01) {
            vec3 n = normalize(cross(dFdx(rel), dFdy(rel)));
            float floorMask = smoothstep(0.8, 0.95, abs(n.y)) * step(rel.y, -0.5) * exp(-dist / 30.0);
            vec2 tile = floor(wp.xz + 0.001);
            float h = hash(tile + floor(t * 2.0) * 1.37);
            vec3 tileCol = 0.5 + 0.5 * cos(6.2831853 * (vec3(0.0, 0.33, 0.67) + h));
            vec2 inTile = abs(fract(wp.xz) - 0.5);
            float lit = step(0.5, h) * smoothstep(0.5, 0.44, max(inTile.x, inTile.y));
            col += tileCol * lit * floorMask * solid * Scene * (0.25 + 0.5 * Kick);
        }
    }
    // Brain zap: a grey-white flash, like a shock.
    col = mix(col, vec3(dot(col, vec3(0.299, 0.587, 0.114)) * 1.6 + 0.1), 0.6 * Zap);
    // Overheated: washed out, flushed red, and the edges throb with a racing pulse.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum) * vec3(1.2, 0.9, 0.85), 0.35 * Heat);
    col *= 1.0 - smoothstep(0.2, 0.8, length(d)) * (0.45 + 0.25 * sin(t * 14.0)) * Heat;
    // Meth: cold, clinical light, and colours tear apart at the edges.
    col *= mix(vec3(1.0), vec3(0.92, 1.0, 1.1), Tweak);
    col.r = mix(col.r, tap(uv + d * 0.01).r, 0.6 * Tweak);
    col.b = mix(col.b, tap(uv - d * 0.01).b, 0.6 * Tweak);
    // Hyperfocus: what you look at is razor-sharp, the rest of the world falls away - grey and dim.
    float aside = smoothstep(0.2, 0.7, length(d * vec2(aspect, 1.0)));
    col += (col - ring(uv, px)) * 0.8 * Tweak * (1.0 - aside);
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum), 0.4 * aside * Tweak) * (1.0 - 0.25 * aside * Tweak);
    // Wide pupils: bright things glare, hard and cold.
    col += spill(uv, px * 4.0, 0.2) * vec3(0.8, 0.95, 1.1) * 1.5 * Tweak;
    if (Tired > 0.0) {
        // Sleepless: the picture fizzles with grain, like a dead TV channel.
        col += (hash(uv * OutSize + fract(t * 13.0) * 97.0) - 0.5) * 0.09 * Tired;
        // Something at the edge of the view - a dark shape, there for a blink, never where you look.
        float slot = floor(t * 1.7);
        float life = fract(t * 1.7);
        float side = hash(vec2(slot, 1.3)) < 0.5 ? -1.0 : 1.0;
        vec2 at = vec2(0.5 + side * (0.36 + 0.1 * hash(vec2(slot, 2.1))), 0.3 + 0.4 * hash(vec2(slot, 3.7)));
        at.x += side * (life - 0.5) * 0.08; // gliding away, out of the view
        vec2 off = (uv - at) * vec2(aspect, 1.0) * vec2(1.0, 0.45);
        float shape = smoothstep(0.07, 0.02, length(off)) * step(1.0 - 0.6 * Tired, hash(vec2(slot, 4.4)));
        col *= 1.0 - 0.75 * shape * sin(3.14159 * life) * Tired;
        // Meth mites: little black bugs, scuttling in fits and starts, mostly low and at the edges.
        float bugs = 0.0;
        for (int i = 0; i < 12; i++) {
            float fi = float(i);
            if (hash(vec2(fi, 9.1)) > Tired) continue;
            // Each bug keeps to one spot for a few seconds, mostly low and at the edges of the
            // view, and fades away before it turns up somewhere else.
            float stay = t / 4.0 + hash(vec2(fi, 6.6));
            float spot = floor(stay), here = fract(stay);
            vec2 home = vec2(hash(vec2(fi, spot)), 0.05 + 0.45 * hash(vec2(fi, spot + 0.5)) * hash(vec2(fi, 7.7)));
            home.x = mix(home.x, step(0.5, home.x), 0.6);
            // Around it, it scuttles in fits and starts: each dash ends where the next begins.
            float dash = t * (2.0 + 3.0 * hash(vec2(fi, 2.2)));
            float n = floor(dash), go = fract(dash);
            vec2 a = home + (vec2(hash(vec2(fi, n)), hash(vec2(fi, n + 0.5))) - 0.5) * 0.07;
            vec2 b = home + (vec2(hash(vec2(fi, n + 1.0)), hash(vec2(fi, n + 1.5))) - 0.5) * 0.07;
            vec2 bug = mix(a, b, smoothstep(0.0, 0.4, go));
            vec2 dir = normalize((b - a) * vec2(aspect, 1.0) + 1e-5);
            // Measured in screen heights, so the bugs are as big on any screen.
            vec2 rel = (uv - bug) * vec2(aspect, 1.0) / 0.0035;
            float along = dot(rel, dir), across = dot(rel, vec2(-dir.y, dir.x));
            // A body, and little legs twitching as it runs.
            float body = smoothstep(1.6, 1.0, length(vec2(along * 0.6, across)));
            float legs = step(abs(across), 3.5) * step(abs(along), 2.0) * step(0.5, fract(along * 0.5 + go * 8.0)) * 0.6;
            float seen = smoothstep(0.0, 0.1, here) * smoothstep(1.0, 0.9, here);
            bugs = max(bugs, max(body, legs * step(1.0, abs(across))) * seen);
        }
        col *= 1.0 - 0.85 * bugs * Tired;
    }

    // Heroin: soft, warm and dim - pinpoint pupils let little light in, and the edges sink
    // into a warm dark like a blanket pulled up.
    col = mix(col, ring(uv, px * 3.0), 0.5 * Nod);
    col *= mix(vec3(1.0), vec3(1.1, 0.95, 0.8), Nod) * (1.0 - 0.25 * Nod);
    col *= 1.0 - smoothstep(0.2, 0.8, length(d)) * 0.6 * Nod;
    // Pinpoint pupils: light no longer glares - the bright things sink.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col *= 1.0 - 0.35 * smoothstep(0.55, 1.0, lum) * Nod;
    // Every slow breath swells warm through the picture.
    col *= 1.0 + 0.08 * Breath * Nod;
    // The rush: warmth rolls up from the belly - golden, soft, everything melts into it.
    float warmth = clamp(Flood * 1.6 - uv.y, 0.0, 1.0);
    if (warmth > 0.0) {
        col = mix(col, ring(uv, px * 5.0), 0.5 * warmth);
        col += vec3(1.0, 0.68, 0.32) * 0.35 * warmth + spill(uv, px * 9.0, 0.3) * vec3(1.0, 0.75, 0.4) * 2.0 * warmth;
        lum = dot(col, vec3(0.299, 0.587, 0.114));
        col = mix(vec3(lum), col, 1.0 + 0.4 * warmth);
    }
    // The breath failing: between breaths the colour drains to a cold blue and the dark closes in.
    float starve = Air * (1.0 - Breath);
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum) * vec3(0.6, 0.72, 1.0), 0.6 * starve);
    col *= 1.0 - smoothstep(0.1, 0.7, length(d)) * 0.8 * starve;
    // Cold turkey: pale, cold and clammy, the skin all gooseflesh.
    lum = dot(col, vec3(0.299, 0.587, 0.114));
    col = mix(col, vec3(lum) * vec3(0.88, 0.96, 1.05), 0.4 * Sick) * (1.0 + 0.05 * Sick);
    col *= 1.0 - 0.07 * Sick * step(0.85, hash(floor(uv * OutSize / 3.0)));
    // On the nod: a dream behind the closed eyes - the world turning slowly, soft and golden,
    // like a memory, with lights drifting through it.
    if (Dream > 0.0) {
        vec2 q = (uv - 0.5) * vec2(aspect, 1.0);
        float turn = 0.15 * sin(t * 0.2);
        q = mat2(cos(turn), -sin(turn), sin(turn), cos(turn)) * q * (0.8 + 0.05 * sin(t * 0.3));
        vec3 memory = ring(0.5 + q / vec2(aspect, 1.0), px * 8.0);
        lum = dot(memory, vec3(0.299, 0.587, 0.114));
        memory = mix(vec3(lum), memory, 0.4) * vec3(1.15, 0.95, 0.7);
        for (int i = 0; i < 5; i++) {
            float fi = float(i);
            vec2 p = vec2(0.5 + 0.35 * sin(t * 0.13 * (1.0 + fi * 0.3) + fi * 2.0), 0.5 + 0.3 * cos(t * 0.11 * (1.0 + fi * 0.2) + fi * 1.3));
            memory += vec3(1.0, 0.8, 0.5) * 0.25 * smoothstep(0.2, 0.0, length((uv - p) * vec2(aspect, 1.0)));
        }
        memory *= 1.0 - smoothstep(0.2, 0.75, length(d)) * 0.7;
        col = mix(col, memory, Dream);
    }

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
    // MDMA: lights leave glowing streaks, like glowsticks swung in the dark.
    col = max(col, prev * 0.92 * smoothstep(0.65, 0.95, dot(prev, vec3(0.299, 0.587, 0.114))) * Roll * Trail);
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
    // MDMA: tracers too, shorter - distinct from medium doses on.
    col = mix(col, texture(PrevSampler, wasUv).rgb, min(0.9, (0.55 + 0.3 * Desert) * Trip + 0.45 * smoothstep(0.4, 1.0, Roll)) * Trail * World * onScreen);

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
