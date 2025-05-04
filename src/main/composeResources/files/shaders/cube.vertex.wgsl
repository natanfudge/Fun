struct Uniforms {
    viewProjection: mat4x4f
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;

@vertex
fn main(
  @location(0) position : vec3f,
) -> @builtin(position) vec4f {
    if(uniforms.viewProjection[0][0] == 999) {
        return vec4f(0,0,0,0);
    }
    let mult = uniforms.viewProjection * vec4f(position.x, position.y , position.z  + 0.7, 4);
  return mult;
}