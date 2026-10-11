#version 150

#moj_import <fog.glsl>

uniform sampler2D Sampler0;

uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;

in float vertexDistance;
in vec4 vertexColor;
in vec2 texCoord0;

out vec4 fragColor;

void main() {
    vec4 tex = texture(Sampler0, texCoord0);
    if (tex.a < 0.1) {
        discard;                                   // follow the model's cut-outs
    }
    // The vertex colour only carries the strength, as grey: some model renderers
    // (Embeddium's fast path) reorder the colour channels, which turned a green
    // vertex colour purple. min() survives any channel order.
    float strength = min(vertexColor.r, min(vertexColor.g, vertexColor.b));
    // One even fluorescent green over the silhouette (added on top of the entity);
    // a quarter of the texture's brightness keeps eyes and seams readable.
    float lum = dot(tex.rgb, vec3(0.299, 0.587, 0.114));
    fragColor = vec4(vec3(0.22, 1.0, 0.12) * strength * (0.75 + 0.25 * lum), 1.0) * ColorModulator
            * linear_fog_fade(vertexDistance, FogStart, FogEnd);
}
