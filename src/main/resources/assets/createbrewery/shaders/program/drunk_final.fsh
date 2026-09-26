#version 150

// The last pass over the drug vision (after drunk.fsh): real bloom, depth of field and motion
// blur. The strengths come from the same uniforms drunk.fsh gets; only Focus is its own.
uniform sampler2D DiffuseSampler;      // the picture from drunk.fsh
uniform sampler2D BloomSampler;        // its bright parts, blurred (drunk_bright, drunk_blur)
uniform sampler2D DiffuseDepthSampler;
uniform vec2 OutSize;
uniform float Intensity;
uniform float Mood;
uniform float Coke;
uniform float High;
uniform float Trip;
uniform float Organic;
uniform float Roll;
uniform float Beat;
uniform float Level;
uniform float Tweak;
uniform float Nod;
uniform float Calm;
uniform float Focus;                   // how far off the spot looked at is, smoothed, in blocks
uniform mat4 InvViewProj;
uniform mat4 PrevViewProj;
uniform vec3 CamDelta;
uniform float World;
uniform float Trail;
in vec2 texCoord;
out vec4 fragColor;

vec3 tap(vec2 uv) {
    return texture(DiffuseSampler, clamp(uv, vec2(0.001), vec2(0.999))).rgb;
}

vec3 worldAt(vec2 uv) {
    vec4 w = InvViewProj * vec4(uv * 2.0 - 1.0, texture(DiffuseDepthSampler, uv).r * 2.0 - 1.0, 1.0);
    return w.xyz / w.w;
}

void main() {
    vec2 px = 1.0 / OutSize;
    vec3 col = tap(texCoord);

    // Depth of field: the eyes hold one distance, and what is nearer or further goes soft.
    // Xanax most, then drunk, heroin, stoned. Needs the world (not under an Iris pack).
    float dof = max(max(0.9 * Calm, 0.7 * Intensity), max(0.6 * Nod, 0.4 * High)) * World;
    if (dof > 0.01) {
        float dist = length(worldAt(texCoord));
        float off = smoothstep(0.1, 0.8, abs(dist - Focus) / (Focus + 2.0)) * dof;
        vec3 soft = vec3(0.0);
        for (int i = 0; i < 12; i++) {
            float a = float(i) * 0.5236;
            float r = (i % 2 == 0 ? 3.0 : 6.0) * off;
            soft += tap(texCoord + vec2(cos(a), sin(a)) * r * px);
        }
        col = mix(col, soft / 12.0, min(1.0, off * 1.5));
    }

    // Motion blur: meth and Koks - every turn of the head smears. Where this spot was on screen
    // last frame gives the way it moved; the picture is averaged along it.
    float smear = max(Tweak, Coke) * World * Trail;
    if (smear > 0.01) {
        vec4 was = PrevViewProj * vec4(worldAt(texCoord) + CamDelta, 1.0);
        vec2 vel = (texCoord - (was.xy / was.w * 0.5 + 0.5)) * step(0.0, was.w) * 0.6 * smear;
        float len = length(vel);
        if (len > 0.08) vel *= 0.08 / len;
        vec3 sum = col;
        for (int i = 1; i < 8; i++) sum += tap(texCoord - vel * float(i) / 7.0);
        col = sum / 8.0;
    }

    // Bloom: bright light spills over, in the colour of the high - wide pupils on beer, weed,
    // Koks, mushrooms, MDMA and meth; the MDMA glow breathes with the music.
    // ponytail: strengths eyeballed against the old ring glow in drunk.fsh; tune here.
    vec3 bloom = texture(BloomSampler, texCoord).rgb;
    vec3 tint = vec3(1.0, 0.8, 0.5) * 1.8 * Mood
              + vec3(1.0, 1.0, 1.05) * 0.5 * Coke
              + vec3(1.0, 0.94, 0.8) * 1.4 * High
              + vec3(1.0, 0.92, 0.7) * 1.0 * Organic * Trip
              + vec3(1.0, 0.75, 0.85) * 1.0 * Roll * (1.0 + 0.8 * Beat * Level)
              + vec3(0.8, 0.95, 1.1) * 0.6 * Tweak;
    col += bloom * tint;

    fragColor = vec4(clamp(col, 0.0, 1.0), 1.0);
}
