struct Uniforms {
    viewProjection: mat4x4f
}

struct Instance {
    model: mat4x4f
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage,read> instances: array<Instance>;

@vertex
fn main(
  @location(0) position : vec3f,
  @builtin(instance_index) iid: u32,
) -> @builtin(position) vec4f {
   let model = instances[iid].model;
  return uniforms.viewProjection * model * vec4f(position, 1);;
}