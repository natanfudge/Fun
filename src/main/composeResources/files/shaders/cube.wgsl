struct Uniforms {
    viewProjection: mat4x4f
}

struct Instance {
    model: mat4x4f,
    color: vec4f
}

struct VertexOutput {
    @builtin(position) pos: vec4f,
    @location(0) color: vec4f
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage,read> instances: array<Instance>;

@vertex
fn vs_main(
  @location(0) position : vec3f,
  @builtin(instance_index) iid: u32,
) -> VertexOutput {
    let instance = instances[iid];
    var output: VertexOutput;
    output.pos = uniforms.viewProjection * instance.model * vec4f(position, 1);
    output.color = instance.color;
    return output;
}

@fragment
fn fs_main(vertex: VertexOutput) -> @location(0) vec4<f32> {
    return vertex.color;
//    return vec4f(1,0,0,1);
}