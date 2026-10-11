#version 150

// Nuclear flash glow, first pass (post/explosion_effect.json): keeps only the scene's hot
// pixels - bright and warm, i.e. the fireball and whatever burns - for the blur to spread
// into a halo. Sky, snow and sand fail the warmth test. The glow follows the explosion
// itself, whatever its shape, and fades as it cools.

uniform sampler2D DiffuseSampler;
uniform sampler2D CoreSampler;
uniform float GoggleExposure;

in vec2 texCoord;

out vec4 fragColor;

void main() {
    if (GoggleExposure > 0.0) {
        fragColor = vec4(texture(CoreSampler, texCoord).rgb, 1.0);
        return;
    }
    vec3 c = texture(DiffuseSampler, texCoord).rgb;
    float hot = smoothstep(0.62, 0.9, c.r) * smoothstep(0.22, 0.45, c.r - c.b);
    fragColor = vec4(c * hot, 1.0);
}
