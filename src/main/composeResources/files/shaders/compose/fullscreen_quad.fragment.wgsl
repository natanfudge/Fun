@group(0) @binding(0) var texSampler: sampler;
@group(0) @binding(1) var texture: texture_2d<f32>;
@fragment
fn fs_main(@location(0) fragUV: vec2f) -> @location(0) vec4<f32> {
    return textureSample(texture, texSampler, /*vec2f(1,1)-*/ fragUV);
}