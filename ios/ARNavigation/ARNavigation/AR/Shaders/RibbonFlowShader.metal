#include <metal_stdlib>
#include <RealityKit/RealityKit.h>

using namespace metal;

[[visible]]
void ribbonFlowSurface(realitykit::surface_parameters params) {
    float2 uv = params.geometry().uv0();
    float time = params.uniforms().time();

    // Scrolling pulse bands along path (v direction)
    float phase = (uv.y - time * 2.5f) / 3.0f;  // 2.5 m/s, 3m wavelength
    float wave = pow(max(0.0f, sin(phase * M_PI_F * 2.0f)), 3.0f);

    // Edge falloff (bright center, transparent edges)
    float edgeFade = 1.0f - pow(abs(uv.x - 0.5f) * 2.0f, 1.5f);

    half3 color = mix(half3(0.3h, 0.9h, 0.4h), half3(0.4h, 1.0h, 0.6h), half(wave * 0.5f));
    half opacity = half(mix(0.15f, 0.55f, wave) * edgeFade);

    params.surface().set_emissive_color(color);
    params.surface().set_base_color(color);
    params.surface().set_opacity(opacity);
}
