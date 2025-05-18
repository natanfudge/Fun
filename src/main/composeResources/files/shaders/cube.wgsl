struct Uniforms {
    viewProjection: mat4x4f,
    cameraPos: vec3f,
    lightPos: vec3f
}

struct Instance {
    model: mat4x4f,
    normalMat: mat3x3f,
    color: vec4f
}

struct VertexOutput {
    @builtin(position) pos: vec4f,
    @location(0) color: vec4f,
    @location(1) normal: vec3f,
    @location(2) worldPos: vec3f
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var<storage,read> instances: array<Instance>;

@vertex
fn vs_main(
  @location(0) position : vec3f,
  @location(1) normal: vec3f,
  @builtin(instance_index) iid: u32,
) -> VertexOutput {
    let instance = instances[iid];
    let worldPos = (instance.model * vec4f(position, 1.0)).xyz;

//    let normalMat   = transpose(inverse(mat3x3f(inst.model)));
    let worldNormal = normalize(instance.normalMat * normal);

    var output: VertexOutput;
    output.pos = uniforms.viewProjection * vec4f(worldPos, 1);
    output.color = instance.color;
    output.normal = worldNormal;
    output.worldPos = worldPos;
    return output;
}

@fragment
fn fs_main(vertex: VertexOutput) -> @location(0) vec4<f32> {
        let N  = normalize(vertex.normal);
        let L  = normalize(uniforms.lightPos - vertex.worldPos);
        let V  = normalize(uniforms.cameraPos - vertex.worldPos);
        let R  = reflect(-L, N);

        // Phong terms
        let ambient   = 0.1;
        let diff      = max(dot(N, L), 0.0);
        let specPower = 32.0;                  // shininess
        let spec      = pow(max(dot(R, V), 0.0), specPower);

        let lightRGB  = vec3f(1,1,1);
        let phong = ambient +
                    diff * lightRGB;
//                     +
//                    spec * lightRGB;

        // Final colour: light * albedo (instance tint already holds alpha)
        return vec4f(phong * vertex.color.rgb, vertex.color.a);
//        return vec4f(vertex.normal, vertex.color.a);
//        return vec4f(vertex.normal * 0.5 + 0.5, vertex.color.a);
}