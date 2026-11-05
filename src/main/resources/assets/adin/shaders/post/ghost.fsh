#version 330

uniform sampler2D BodySampler;
uniform sampler2D CoverageSampler;
uniform sampler2D ShadowSampler;

in vec2 texCoord;

out vec4 fragColor;

const float BODY_OPACITY = 0.82;
const float SHADOW_OPACITY = 0.9;
const float SHADOW_GAIN = 1.7;

float coverage(sampler2D mask) {
#if SEPARATE_MASK
    return texture(mask, texCoord).r;
#else
    return texture(mask, texCoord).a;
#endif
}

void main() {
    vec3 body = texture(BodySampler, texCoord).rgb * BODY_OPACITY;
    float cover = coverage(CoverageSampler) * BODY_OPACITY;
    float shadow = clamp(coverage(ShadowSampler) * SHADOW_GAIN, 0.0, 1.0) * SHADOW_OPACITY;
    fragColor = vec4(body, cover + shadow * (1.0 - cover));
}
