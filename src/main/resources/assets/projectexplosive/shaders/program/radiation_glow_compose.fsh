#version 150

// Irradiated glow, last pass (post/radiation_glow.json): the blurred silhouettes make
// the soft halo, a little of the sharp ones keeps the body itself lit. The result is
// added onto the scene by RadiationGlow.

uniform sampler2D DiffuseSampler;   // silhouettes after the blur
uniform sampler2D SharpSampler;     // silhouettes before it

in vec2 texCoord;

out vec4 fragColor;

void main() {
    vec3 halo = texture(DiffuseSampler, texCoord).rgb;
    vec3 body = texture(SharpSampler, texCoord).rgb;
    fragColor = vec4(halo * 1.7 + body * 0.35, 1.0);
}
