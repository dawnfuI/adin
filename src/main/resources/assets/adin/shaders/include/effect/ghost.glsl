#version 330

vec3 ghost(vec3 skin) {
    vec3 normal = normalize(viewNormal);
    float light = 0.45 + 0.55 * clamp(dot(normal, normalize(vec3(-0.45, 0.70, 0.55))), 0.0, 1.0);
    float rim = pow(1.0 - clamp(abs(dot(normal, normalize(-viewPosition))), 0.0, 1.0), 2.0);
    return skin * light * mix(0.62, 0.3, rim);
}
