#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;
uniform sampler2D DhDepthSampler;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
uniform vec4 FogColor;
uniform vec2 ScreenSize;
uniform int DhOcclusionEnabled;
uniform int DhReverseZ;
uniform float DhNear;
uniform float DhFar;
// DhGate = (proj[0][0], proj[1][1], Minecraft's render distance in blocks). Nearer than that, Minecraft
// drew its own terrain and depth-tested it already; DH's LOD there can be stale (a crater it has
// not rebuilt yet keeps the old hills), so it must not occlude.
uniform vec3 DhGate;

in float vertexDistance;
in vec2 texCoord0;
in vec4 vertexColor;
in float eyeZ;

out vec4 fragColor;

void main() {
    // Distant Horizons composites its far LOD terrain as a screen-space colour
    // blit and never writes real depth into Minecraft's depth buffer, so the
    // hardware depth test can't occlude anything drawn after it against DH's
    // terrain. This samples DH's OWN depth texture and discards fragments that
    // sit behind it.
    //
    // The comparison is done in WORLD DISTANCE, not raw window depth: DH's depth
    // buffer and MC's use different projection far planes, so raw depths aren't
    // comparable. DH's depth is linearised to an eye-space distance with DH's
    // own near/far (same formula DH uses in its ssao/apply.frag), and compared
    // against the particle's eye-space distance from the vertex shader. A DH
    // normalized depth of 1.0 means background and never occludes.
    //
    // Mirrors the percentage bias the depth-composite pass applies (see
    // dh_depth_composite.fsh: eyeZ *= 0.985) so the in-shader test agrees in
    // DIRECTION with the bias already on terrain in MC's depth buffer. The
    // hardware depth test is the authoritative check when the composite is
    // running; this in-shader test fires if the composite is skipped (LOD not
    // loaded yet, shader pack active, dhDepth==1.0 at this pixel) so the
    // fragment still gets correctly occluded. We only mirror the percentage
    // bias (no constant block subtraction): translating the composite's
    // 16-ULP NDC bias to world units has no single correct block value at all
    // distances and would over-discard close-range particles.
    if (DhOcclusionEnabled != 0) {
        vec2 dhUv = gl_FragCoord.xy / ScreenSize;
        float dhDepth = texture(DhDepthSampler, dhUv).r;
        if (DhReverseZ != 0) dhDepth = (1.0 - dhDepth) * float(DhReverseZ);
        if (dhDepth < 1.0) {
            float dhEyeZ = (DhNear * DhFar) / (dhDepth * (DhNear - DhFar) + DhFar);
            float dhDist = dhEyeZ * length(vec3((dhUv * 2.0 - 1.0) / DhGate.xy, 1.0));
            if (dhDist > DhGate.z && eyeZ > dhEyeZ * 0.985) {   // see DhGate
                discard;
            }
        }
    }

    vec4 color = texture(Sampler0, texCoord0) * vertexColor * ColorModulator;
    if (color.a < 0.1) {
        discard;
    }
    fragColor = linear_fog(color, vertexDistance, FogStart, FogEnd, FogColor);
}
