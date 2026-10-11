#version 150

uniform sampler2D DhDepthSampler;
uniform vec2 ScreenSize;
uniform float DhNear;
uniform float DhFar;
uniform float McNear;
uniform float McFar;
uniform int DhDebug;
uniform int DhReverseZ;
uniform vec3 DhGate;

out vec4 fragColor;

// Writes Distant Horizons' LOD depth into Minecraft's depth buffer (see DhDepthComposite),
// so later geometry - particles above all - is occluded by LOD terrain.
// DhDebug != 0 also tints LOD terrain red, to check the pass runs.
void main() {
    vec2 uv = gl_FragCoord.xy / ScreenSize;
    float dhDepth = texture(DhDepthSampler, uv).r;
    if (DhReverseZ != 0) dhDepth = (1.0 - dhDepth) * float(DhReverseZ);

    if (dhDepth >= 1.0) {
        discard; // no LOD terrain here: leave Minecraft's depth alone
    }
    float eyeZ = (DhNear * DhFar) / (dhDepth * (DhNear - DhFar) + DhFar);
    // DhGate = (proj[0][0], proj[1][1], Minecraft's render distance in blocks). Nearer than that, Minecraft
    // drew its own terrain and depth-tested it already; DH's LOD there can be stale (a crater it has
    // not rebuilt yet keeps the old hills), so it must not occlude.
    if (eyeZ * length(vec3((uv * 2.0 - 1.0) / DhGate.xy, 1.0)) < DhGate.z) {
        discard;
    }

    // Bias the terrain slightly nearer. With a LOD renderer the far plane sits tens of
    // thousands of blocks out, so past ~1000 blocks terrain and a particle 200 blocks
    // behind it encode to nearly the same 24-bit depth, and GL_LEQUAL lets the
    // particle win. 1.5 % closer covers mid range (~125 ULPs at 100 blocks, ~3 at
    // 4000); a constant 16-ULP floor covers the far end, where the percentage alone
    // falls under the rasteriser's rounding noise.
    eyeZ *= 0.985;
    float mcDepth = (McFar / (McFar - McNear)) * (1.0 - McNear / eyeZ);
    gl_FragDepth = clamp(mcDepth - 16.0 / 16777216.0, 0.0, 1.0);

    fragColor = DhDebug != 0 ? vec4(1.0, 0.0, 0.0, 0.35) : vec4(0.0);
}
