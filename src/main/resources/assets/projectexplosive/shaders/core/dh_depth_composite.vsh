#version 150

// Fullscreen pass: Position is already in clip space (NDC quad corners), so we
// pass it straight through. gl_FragDepth is written explicitly in the fragment
// shader, so this quad's own z is irrelevant.
in vec3 Position;

void main() {
    gl_Position = vec4(Position.xy, 0.0, 1.0);
}
