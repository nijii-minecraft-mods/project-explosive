#version 150

uniform sampler2D DiffuseSampler;
uniform sampler2D BloomSampler;   // the scene's hot pixels, blurred (explosion_bright + box_blur)
uniform sampler2D WorldSampler;
uniform sampler2D DepthSampler;
uniform sampler2D CoreSampler;
uniform sampler2D ParticleSampler;
uniform mat4 InverseProjection;
uniform mat4 WorldProjection;
uniform vec3 LightPosition;
uniform vec3 StemPosition;
uniform vec3 BasePosition;
uniform vec3 SourceWeights;
uniform float CorePower;

in vec2 texCoord;
in vec2 oneTexel;

// Up to 3 shockwaves at once, each with its own centre, radius and strength.
uniform vec2 Center1;
uniform vec2 Center2;
uniform vec2 Center3;
uniform vec3 RingRadii;        // x = slot 1, y = slot 2, z = slot 3
uniform vec3 DistortStrengths; // same packing

uniform vec2 InSize;
uniform float GoggleExposure;
uniform float FlashAmount;
uniform vec3 FlashTint;     // the fireball's colour as it cools: blue, white-yellow, yellow, orange, red
uniform float DarkFlash;    // 1 = photosensitive: the flash may only darken the frame
uniform float Chromatic;
uniform float Time;

out vec4 fragColor;

// Cheap hash, 0..1, for grain.
float hash12(vec2 p) {
    vec3 p3 = fract(vec3(p.xyx) * 0.1031);
    p3 += dot(p3, p3.yzx + 33.33);
    return fract((p3.x + p3.y) * p3.z);
}

// One shock's warp: (offset.xy, wave). The caller uses wave to pick the dominant shock.
vec3 evalShock(vec2 uv, vec2 center, float radius, float strength) {
    if (strength < 0.001) return vec3(0.0);

    vec2 toCenter = uv - center;
    float dist = length(toCenter);

    // Gaussian pressure band, narrow enough to read as a line.
    float bandWidth = max(0.07 + strength * 0.06, radius * 0.22 + 0.05);
    float signedDist = dist - radius;        // >0 ahead of front, <0 behind it
    float ringT = exp(-(signedDist * signedDist) / (bandWidth * bandWidth));
    float wave = ringT * strength;

    vec2 dir = dist > 0.0001 ? normalize(toCenter) : vec2(0.0);
    // Reaches the whole screen, so a distant blast still shows.
    float edgeFalloff = 1.0 - smoothstep(0.0, 1.05, dist);

    // Refraction: pushed out behind the front (compression), pulled in ahead of it
    // (rarefaction). The sign flip across the front is what makes it read as a shock.
    float lens = clamp(-signedDist / max(bandWidth, 0.0001), -1.5, 1.5);
    vec2 offset = dir * lens * ringT * strength * 0.011 * edgeFalloff;
    return vec3(offset, wave);
}

vec3 viewPosition(vec2 uv, float depth) {
    vec4 p = InverseProjection * vec4(uv * 2.0 - 1.0, depth * 2.0 - 1.0, 1.0);
    return p.xyz / p.w;
}

vec3 viewPosition(vec2 uv) {
    return viewPosition(uv, texture(DepthSampler, uv).r);
}

vec3 surfaceNormal(vec2 uv, vec3 p) {
    vec3 left = p - viewPosition(uv - vec2(oneTexel.x, 0.0));
    vec3 right = viewPosition(uv + vec2(oneTexel.x, 0.0)) - p;
    vec3 down = p - viewPosition(uv - vec2(0.0, oneTexel.y));
    vec3 up = viewPosition(uv + vec2(0.0, oneTexel.y)) - p;
    vec3 dx = dot(left, left) < dot(right, right) ? left : right;
    vec3 dy = dot(down, down) < dot(up, up) ? down : up;
    vec3 normal = cross(dx, dy);
    normal *= inversesqrt(max(dot(normal, normal), 0.000000000001));
    return dot(normal, -p) < 0.0 ? -normal : normal;
}

float visibility(vec3 p, vec3 normal, vec3 light) {
    vec3 start = p + normal * 0.5;
    for (int i = 1; i <= 8; i++) {
        float t = float(i) / 9.0;
        vec3 probe = mix(start, light, t * t);
        vec4 clip = WorldProjection * vec4(probe, 1.0);
        if (clip.w <= 0.0) break;
        vec2 uv = clip.xy / clip.w * 0.5 + 0.5;
        if (any(lessThan(uv, vec2(0.002))) || any(greaterThan(uv, vec2(0.998)))) break;
        vec3 blocker = viewPosition(uv);
        if (blocker.z > probe.z + max(0.4, abs(probe.z) * 0.004)) return 0.0;
    }
    return 1.0;
}

float sourceLight(vec3 p, vec3 normal, vec3 light, float weight) {
    if (weight < 0.001) return 0.0;
    vec3 toLight = light - p;
    float distanceSquared = dot(toLight, toLight);
    if (distanceSquared >= 360000.0) return 0.0;
    float facing = clamp((dot(normal, toLight * inversesqrt(max(distanceSquared, 0.001))) + 0.35) / 1.35, 0.0, 1.0);
    float attenuation = 1.0 / (1.0 + distanceSquared / 25600.0);
    float reach = 1.0 - smoothstep(450.0, 600.0, sqrt(distanceSquared));
    float direct = facing > 0.001 ? facing * visibility(p, normal, light) : 0.0;
    return (0.15 + direct * 0.85) * attenuation * reach * weight;
}

void main() {
    vec2 uv = texCoord;

    vec3 s1 = evalShock(uv, Center1, RingRadii.x, DistortStrengths.x);
    vec3 s2 = evalShock(uv, Center2, RingRadii.y, DistortStrengths.y);
    vec3 s3 = evalShock(uv, Center3, RingRadii.z, DistortStrengths.z);

    vec2 offset = s1.xy + s2.xy + s3.xy;
    float maxWave = max(max(s1.z, s2.z), s3.z);

    // The strongest shock here drives the chromatic split and the heat push.
    vec2 dominantCenter = Center1;
    if (s2.z > s1.z && s2.z >= s3.z) dominantCenter = Center2;
    else if (s3.z > s1.z && s3.z > s2.z) dominantCenter = Center3;

    vec2 toDom = uv - dominantCenter;
    float distDom = length(toDom);
    vec2 dominantDir = distDom > 0.0001 ? normalize(toDom) : vec2(0.0);

    float dominantRadius = RingRadii.x;
    if (s2.z > s1.z && s2.z >= s3.z) dominantRadius = RingRadii.y;
    else if (s3.z > s1.z && s3.z > s2.z) dominantRadius = RingRadii.z;

    float trailT = smoothstep(0.0, 0.10, dominantRadius - distDom)
                 * exp(-(dominantRadius - distDom) * 18.0);
    // Faint static push behind the front. No time term: the screen must not wobble.
    vec2 staticHeatPush = dominantDir * trailT * maxWave * 0.0008;
    offset += staticHeatPush;

    vec2 sampleUv = clamp(uv + offset, vec2(0.001), vec2(0.999));

    // Chromatic split, at most 0.00375 UV. Away from the front it is sub-pixel, so
    // sample once there instead of three times.
    float ca = Chromatic * maxWave * 0.025;
    float caShift = ca * 0.15;
    vec3 color;
    if (caShift > 0.0006) {
        float r = texture(DiffuseSampler, clamp(sampleUv + dominantDir * caShift, vec2(0.001), vec2(0.999))).r;
        float g = texture(DiffuseSampler, sampleUv).g;
        float b = texture(DiffuseSampler, clamp(sampleUv - dominantDir * caShift, vec2(0.001), vec2(0.999))).b;
        color = vec3(r, g, b);
    } else {
        color = texture(DiffuseSampler, sampleUv).rgb;
    }

    // Faint warm tint on the crest. Anything brighter reads as a flat white ring;
    // a steeper exponent would hide it on small bombs.
    color += vec3(0.70, 0.48, 0.30) * pow(maxWave, 4.5) * 0.18;

    // Nuclear flash: a frame of test-site film. Over-exposed, drained to grey,
    // grainy; the explosion glows orange through it.
    if (GoggleExposure > 0.0) {
        vec3 filtered = color * 0.006;
        if (DarkFlash < 0.5) {
            vec3 world = texture(WorldSampler, sampleUv).rgb;
            vec3 bloom = texture(BloomSampler, sampleUv).rgb;
            float heat = max(bloom.r, max(bloom.g, bloom.b));
            vec3 orange = vec3(1.0, 0.36, 0.035);
            vec4 particles = texture(ParticleSampler, sampleUv);
            float depth = texture(DepthSampler, sampleUv).r;
            if (depth < 0.999999 && particles.a < 1.0 && CorePower > 0.0
                    && any(greaterThanEqual(SourceWeights, vec3(0.001)))) {
                vec3 p = viewPosition(sampleUv, depth);
                vec3 normal = surfaceNormal(sampleUv, p);
                float light = sourceLight(p, normal, BasePosition, SourceWeights.x)
                            + sourceLight(p, normal, StemPosition, SourceWeights.y)
                            + sourceLight(p, normal, LightPosition, SourceWeights.z);
                vec3 radiance = world * orange * light * CorePower * 7.0;
                filtered += radiance / (1.0 + max(radiance.r, max(radiance.g, radiance.b)));
            }
            filtered = filtered * (1.0 - clamp(particles.a, 0.0, 1.0)) + particles.rgb;
            vec3 glow = texture(CoreSampler, sampleUv).rgb;
            vec3 glare = glow * 0.12 + orange * heat * 0.45;
            filtered += glare * max(vec3(0.0), vec3(1.0) - filtered);
        }
        color = mix(color, filtered, clamp(GoggleExposure, 0.0, 1.0));
    } else if (FlashAmount > 0.02 && DarkFlash > 0.5) {
        // Photosensitive: the frame only dims. No white, no glare, no grain.
        color *= 1.0 - 0.6 * clamp(FlashAmount, 0.0, 1.0);
    } else if (FlashAmount > 0.02) {
        // Over-exposure: anything bright clips to white.
        color *= 1.0 + FlashAmount * 1.6;
        // Black-and-white stock.
        float luma = dot(color, vec3(0.299, 0.587, 0.114));
        color = mix(color, vec3(luma), FlashAmount * 0.95);
        // Hard S-curve: blacks crushed, highlights blown.
        color = (color - 0.5) * (1.0 + FlashAmount * 0.9) + 0.5;
        // Lifted floor: hazy mid-greys, never a deep black.
        color = max(color, vec3(0.28 * FlashAmount));
        // Film vignette.
        color *= 1.0 - smoothstep(0.45, 1.05, length(uv - 0.5)) * FlashAmount * 0.45;
        // Keep film grain subtle even at full flash strength.
        float grain = hash12(uv * vec2(1600.0, 900.0) + fract(Time * 7.3) * 191.0) - 0.5;
        color += grain * 0.035 * clamp(FlashAmount, 0.0, 1.0);

        // The explosion glows through the grey in the colour of the cooling fireball: its hot
        // pixels, blurred into a halo and added back after the film has drained the colour.
        // Whiter where hottest.
        vec3 bloom = texture(BloomSampler, sampleUv).rgb;
        float heat = max(bloom.r, max(bloom.g, bloom.b));
        color += mix(FlashTint, mix(FlashTint, vec3(1.0), 0.5), clamp(heat - 0.4, 0.0, 1.0))
                * heat * 2.2 * clamp(FlashAmount, 0.0, 1.0);
    } else {
        // Shockwave only: a whisper of static grain where the front passes.
        float maxDistort = max(max(DistortStrengths.x, DistortStrengths.y), DistortStrengths.z);
        if (maxDistort > 0.02) {
            color += (hash12(uv * vec2(2400.0, 1350.0)) - 0.5) * 0.006 * maxDistort;
        }
    }

    fragColor = vec4(color, 1.0);
}
