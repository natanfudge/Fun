struct Uniforms {
    viewProjection: mat4x4f
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn main(
  @location(0) position : vec3f,
) -> @builtin(position) vec4f {
    let mult = uniforms.viewProjection * vec4f(position, 1);
  return mult;
}