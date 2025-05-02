// Full-screen quad
const positions = array<f32, 12>(
    // x y
    -1,-1,
    -1,1,
    1,1,
    -1,-1,
    1,-1,
    1,1
);


struct VertexOutput {
  @builtin(position) Position : vec4f,
  @location(0) fragUV : vec2f,
}

@vertex
fn vs_main(@builtin(vertex_index) in_vertex_index: u32) -> VertexOutput {
    let i = in_vertex_index * 2;
    let x = positions[i];
    let y = positions[i + 1];

    var output: VertexOutput;
    output.Position = vec4<f32>(x,y, 0.0, 1);
    output.fragUV = 0.5 * vec2f(x,y) + vec2(0.5);
    return output;
}


@group(0) @binding(1) var texSampler: sampler;
@group(0) @binding(2) var texture: texture_2d<f32>;
@fragment
fn fs_main(@location(0) fragUV: vec2f) -> @location(0) vec4<f32> {
    return textureSample(texture, texSampler, fragUV);
}