struct Uniforms {
    viewProjection: mat4x4f,
    cameraPos: vec3f,
    lightPos: vec3f,
    width: f32,
    height: f32,
}



struct VertexOutput {
    @builtin(position) pos: vec4f,
    @location(0) color: vec4f,
    @location(1) normal: vec3f,
    @location(2) worldPos: vec3f,
    @location(3) uv: vec2f,
    @location(4) index: u32,
    @location(5) iid: u32
//    @location(5) textured: f32
}



@group(0) @binding(0) var<uniform> uniforms: Uniforms;
@group(0) @binding(1) var samp: sampler;
@group(1) @binding(0) var<storage,read> instances: array<Instance>;
@group(1) @binding(1) var texture: texture_2d<f32>;

struct Instance {
    model: mat4x4f,
    normalMat: mat3x3f,
    color: vec4f,
    textured: f32 // This is a bool, but we'll use a float to keep it all floats
}

@vertex
fn vs_main(
  @location(0) position : vec3f,
  @location(1) normal: vec3f,
  @location(2) uv: vec2f,
  @builtin(instance_index) iid: u32,
) -> VertexOutput {
    let instance = instances[iid];
    let worldPos = (instance.model * vec4f(position, 1.0)).xyz;

//    let normalMat   = transpose(inverse(mat3x3f(inst.model)));
    let worldNormal = normalize(instance.normalMat * normal);
//    let worldNormal = normalize((instance.model * vec4f(normal,0)).xyz);
//
    var output: VertexOutput;
    output.pos = uniforms.viewProjection * vec4f(worldPos, 1);
    output.color = instance.color;
    output.normal = worldNormal;
    output.worldPos = worldPos;
    output.uv = uv;
    output.index = iid;
    output.iid = iid;
    return output;
}



@fragment
fn fs_main(vertex: VertexOutput) -> @location(0) vec4<f32> {
    var dims = vec2(uniforms.width, uniforms.height);
    var view_coords = get_view_coords(vertex.pos.xy, dims);

//    if is_in_number(vertex.pos.xy, array<u32,10>(u32(uniforms.lightPos.x), u32(abs(uniforms.lightPos.y)), u32(uniforms.lightPos.z), 0,0,0,0,0,0,0), vec2(200, 200), 3.0) {
//        return vec4(1.0, 0.0, 0.0, 1.0);
//    }



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
                    diff * lightRGB
                    +
                    spec * lightRGB;

        // Final colour: light * albedo (instance tint already holds alpha)

        let baseColor = select(vertex.color, textureSample(texture, samp, vertex.uv), instances[vertex.iid].textured == 1);;

         if is_in_number(vertex.pos.xy, array<u32,10>(u32(vertex.iid), u32(abs(baseColor.g * 5)), u32(baseColor.b * 5), u32(baseColor.a),0,0,0,0,0,0), vec2(200, 200), 3.0) {
                    return vec4(1.0, 0.0, 0.0, 1.0);
                }


        return vec4f(phong * baseColor.rgb, baseColor.a);
}

const font = array(
    0xebfbe7fcu,
    0xa89b21b4u,
    0xa93fb9fcu,
    0xaa1269a4u,
    0xebf3f9e4u
);
fn is_in_digit(frag_position: vec2<f32>, char: u32, position: vec2<u32>, scale: f32) -> bool {
    let offset = char * 3u;
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

fn get_view_coords(coords: vec2<f32>, screen_dims: vec2<f32>) -> vec2<f32>{
    return ((coords / screen_dims) * 2) - 1;
}