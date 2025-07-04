struct Uniforms {
    viewProjection: mat4x4f,
    cameraPos: vec3f,
    lightPos: vec3f,
    width: u32,
    height: u32
}

struct VertexOutput {
    @builtin(position) pos: vec4f,
    @location(0) tintColor: vec4f,
    @location(1) normal: vec3f,
    @location(2) worldPos: vec3f,
    @location(3) uv: vec2f,
    @location(4) iid: u32,
    @location(5) tintStrength: f32
}

@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var samp: sampler;
@group(0) @binding(2) var<storage, read> instances: array<Instance>;
@group(0) @binding(3) var <storage,read> instance_indices: array<u32>;
// SLOW: should have one large thing we can index when we have bindless resources, both for textures and skinning matrices.
@group(1) @binding(0) var texture: texture_2d<f32>;
// Single array per instance, and this stores the joint matrices for the entire model, so this has multiple arrays
@group(1) @binding(1) var<storage, read> joint_matrices: array<mat4x4f>;
// Single array per model
@group(1) @binding(2) var<storage, read> inverse_bind_matrices: array<mat4x4f>;
// The size of each element of the joint_matrices.
// Each instance accesses his own joint matrix by calculating instance_index * joint_matrices_stride
@group(1) @binding(3) var<uniform> modelUniforms: ModelUniforms;

struct ModelUniforms {
    joint_matrices_stride: u32,
    // Because the passed instance index is global, we need to offset it by the model's starting index to access the joint matrix.
    // SLOW: I won't need any of these 2 values (stride and start index) if I stored everything globally and then have a "instanceIndex -> joint matrix" buffer
    model_start_index: u32
}

struct SkinResult {
    transformedPos: vec4f,
    transformedNormal: vec3f
}


struct Instance {
    model: mat4x4f,
    normalMat: mat3x3f,
    tintColor: vec4f,
    tintStrength: f32,
    textured: u32, //SLOW won't need this with custom shaders
    animated: u32,
}

@vertex
fn vs_main(
  @location(0) position : vec3f,
  @location(1) normal: vec3f,
  @location(2) uv: vec2f,
  // SLOW: We definitely don't need these 2 for non-animated meshes, optionally include this when we have macros
  // We assume up to 4 joints affect the vertex, with a value from 0 to 1.
  // joints stores the index of the joints, and weights store how much that joint affects it.
  // Why 4 joints?  The nvidia gods said so.
  @location(3) joints: vec4f, //SLOW: should pass in as an int but that's annoying, passing as float and converting for now.
  @location(4) weights: vec4f,
  @builtin(instance_index) iiid: u32,
) -> VertexOutput {
    let globalInstanceIndex = instance_indices[iiid];
    let instance = instances[globalInstanceIndex];

    var skinning = false;

    var posNormal : SkinResult;
    if (instance.animated == 1u) {
         skinning = true;
        posNormal = skin(position, normal, joints, weights, (iiid - modelUniforms.model_start_index) * modelUniforms.joint_matrices_stride);// Animated - skin
//        posNormal = SkinResult(vec4f(position, 1.0), normal);
    } else {
        posNormal = SkinResult(vec4f(position, 1.0), normal);// Not animated - don't skin
    }


    let worldPos = instance.model * posNormal.transformedPos;

    let worldNormal = normalize(instance.normalMat * posNormal.transformedNormal);

    var output = VertexOutput(
        /* pos           */ uniforms.viewProjection * worldPos,
        /* tintColor     */ instance.tintColor,
        /* normal        */ worldNormal,
        /* worldPos      */ worldPos.xyz,
        /* uv            */ uv,
        /* iid           */ globalInstanceIndex,
        /* tintStrength  */ instance.tintStrength
    );

//    if (skinning) {
//        output.tintColor = vec4f(1,0,0,1);
//        output.tintStrength = 1;
//    }

    return output;
}


/**
* Transform the vertex according to the skeleton deformation
*/
fn skin(pos: vec3f, normal: vec3f, joints: vec4f, weights: vec4f, instance_offset: u32) -> SkinResult {
       let i0 = u32(joints[0]);
       let i1 = u32(joints[1]);
       let i2 = u32(joints[2]);
       let i3 = u32(joints[3]);

      // Compute joint_matrices * inverse_bind_matrices
      // Every instance has its own joint matrix, but they all use the same inverse bind matrix.
      let joint0 = joint_matrices[i0 + instance_offset] * inverse_bind_matrices[i0];
      let joint1 = joint_matrices[i1+ instance_offset] * inverse_bind_matrices[i1];
      let joint2 = joint_matrices[i2+ instance_offset] * inverse_bind_matrices[i2];
      let joint3 = joint_matrices[i3+ instance_offset] * inverse_bind_matrices[i3];

      let w = renormalize(weights);

      // Compute influence of joint based on weight
      let skin_matrix =  joint0 * w[0] +
                         joint1 * w[1] +
                         joint2 * w[2] +
                         joint3 * w[3];
      // Position of the vertex relative to our world
      let world_position = vec4f(pos, 1.0);
      // Vertex position with model rotation, skinning.
      let skinned_position = skin_matrix * world_position;

    return SkinResult(
        skinned_position,
        normal
    );
}

fn renormalize(w : vec4f) -> vec4f {
    let s = max(0.0001, w.x + w.y + w.z + w.w); // avoid /0
    return w / s;
}


@fragment
fn fs_main(vertex: VertexOutput) -> @location(0) vec4f {
//    var dims = vec2(uniforms.width, uniforms.height);/**/
//    var view_coords = get_view_coords(vertex.pos.xy, dims);

    let lighting = light(vertex.worldPos, vertex.normal);

    // Final colour: light * albedo (only use texture if it exists, otherwise just use the tint color)
    let baseColor = select(vertex.tintColor, textureSample(texture, samp, vertex.uv), instances[vertex.iid].textured == 1u);
    let litColor = vec4f(lighting * baseColor.rgb, baseColor.a);

    // Tint texture (if no texture is used this is a no-op)
    let finalColor = mix(litColor, vertex.tintColor,vertex.tintStrength);

//let digits = number_to_digits(f32(vertex.uv.x * 1000));
//if is_in_number(
//    vertex.pos.xy,
//    digits,
//    vec2(200,200),
//    3.0
//) {
//    return vec4f(1.0,0.0,0.0,1.0);
//}




    return finalColor;
}

fn light(pos: vec3f, normal: vec3f) -> vec3f {
    let n  = normalize(normal);
    let l  = normalize(uniforms.lightPos - pos);
    let v  = normalize(uniforms.cameraPos - pos);
    let r  = reflect(-l, n);

    // Phong terms
    let ambient   = 0.1;
    let diff      = max(dot(n, l), 0.0);
    let specPower = 2.0;                  // shininess
    let spec      = pow(max(dot(r, v), 0.0), specPower);

    let lightRGB  = vec3f(1,1,1);
    return ambient +
                diff * lightRGB
                + spec * lightRGB;
}

const font = array(
    0xebfbe7fcu,
    0xa89b21b4u,
    0xa93fb9fcu,
    0xaa1269a4u,
    0xebf3f9e4u
);
fn is_in_digit(frag_position: vec2<f32>, chara: u32, position: vec2<u32>, scale: f32) -> bool {
    let offset = chara * 3u;
    let rows = array(
        (font[0] >> (29 - offset)) & 0x07,
        (font[1] >> (29 - offset)) & 0x07,
        (font[2] >> (29 - offset)) & 0x07,
        (font[3] >> (29 - offset)) & 0x07,
        (font[4] >> (29 - offset)) & 0x07
    );

    let bump = -0.0001; //this make fractions like 3/3 fall under a whole number.
    let x = i32(floor(((frag_position.x - f32(position.x)) - bump) / scale));
    let y = i32(floor(((frag_position.y - f32(position.y)) - bump) / scale));

    if x > 2 || x < 0 { return false; }
    if y > 4 || y < 0 { return false; }

    return ((rows[y] >> (2 - u32(x))) & 0x01) == 1;
}
const max_number_length: u32 = 10;
fn is_in_number(frag_position: vec2<f32>, digits: array<u32, max_number_length>, position: vec2<u32>, scale: f32) -> bool {
    var i: u32 = 0;
    var current_position = position.xy;

    loop {
        if i > max_number_length - 1 { return false; }
        let digit_size = u32(3 * scale);
        let spacing_size = u32(f32(i) * scale);
        if is_in_digit(frag_position, digits[i], vec2(current_position.x + (i * digit_size) + spacing_size, current_position.y), scale) {
            return true;
        }
        i = i + 1;
    }
    return false;
}

fn number_to_digits(value: f32) -> array<u32, max_number_length> {
    var digits = array<u32, max_number_length>();
    var num = value;

    if(num == 0){
        return digits;
    }

    var i: u32 = 0;
    loop{
        if num < 0 || i >= max_number_length { break; }
        digits[max_number_length - i - 1] = u32(num % 10);
        num = floor(num / 10);
        i = i + 1;
    }
    return digits;
}

fn get_sd_circle(pos: vec2<f32>, r: f32) -> f32 {
    return length(pos) - r;
}

fn get_view_coords(coords: vec2<f32>, screen_dims: vec2<u32>) -> vec2<f32>{
    return ((coords / vec2f(screen_dims)) * 2) - 1;
}