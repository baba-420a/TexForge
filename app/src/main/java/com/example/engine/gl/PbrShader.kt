package com.example.engine.gl

/**
 * OpenGL ES 3.0 Shaders implementing Cook-Torrance Microfacet PBR:
 * - Microfacet Normal Distribution: Trowbridge-Reitz GGX
 * - Geometric Shadowing/Masking: Smith GGX Correlated
 * - Fresnel Reflectance: Schlick Approximation
 * - Normal mapping with TBN frame (OpenGL +Y and DirectX -Y support)
 * - Channel isolation/solo modes for material inspection
 */
object PbrShader {

    const val VERTEX_SHADER_SOURCE = """#version 300 es
layout(location = 0) in vec3 a_Position;
layout(location = 1) in vec3 a_Normal;
layout(location = 2) in vec3 a_Tangent;
layout(location = 3) in vec2 a_TexCoord;

uniform mat4 u_ModelMatrix;
uniform mat4 u_ViewMatrix;
uniform mat4 u_ProjectionMatrix;
uniform mat3 u_NormalMatrix;
uniform float u_Tiling;

out vec3 v_FragPos;
out vec3 v_Normal;
out vec3 v_Tangent;
out vec3 v_Bitangent;
out vec2 v_TexCoord;

void main() {
    vec4 worldPos = u_ModelMatrix * vec4(a_Position, 1.0);
    v_FragPos = worldPos.xyz;
    
    // Transform normal and tangent to world space
    vec3 N = normalize(u_NormalMatrix * a_Normal);
    vec3 T = normalize(u_NormalMatrix * a_Tangent);
    // Gram-Schmidt orthogonalize
    T = normalize(T - dot(T, N) * N);
    vec3 B = cross(N, T);
    
    v_Normal = N;
    v_Tangent = T;
    v_Bitangent = B;
    v_TexCoord = a_TexCoord * u_Tiling;
    
    gl_Position = u_ProjectionMatrix * u_ViewMatrix * worldPos;
}
"""

    const val FRAGMENT_SHADER_SOURCE = """#version 300 es
precision highp float;

in vec3 v_FragPos;
in vec3 v_Normal;
in vec3 v_Tangent;
in vec3 v_Bitangent;
in vec2 v_TexCoord;

uniform sampler2D u_AlbedoMap;
uniform sampler2D u_NormalMap;
uniform sampler2D u_RoughnessMap;
uniform sampler2D u_AoMap;
uniform sampler2D u_MetallicMap;

uniform int u_HasAlbedo;
uniform int u_HasNormal;
uniform int u_HasRoughness;
uniform int u_HasAo;
uniform int u_HasMetallic;

// 0: OpenGL (+Y), 1: DirectX (-Y)
uniform int u_NormalFormat;
uniform float u_NormalStrength;

// Render Mode: 0=PBR, 1=Normal Solo, 2=Roughness Solo, 3=Metallic Solo, 4=AO Solo, 5=Albedo Solo
uniform int u_RenderMode;

uniform vec3 u_CameraPos;
uniform vec3 u_LightPos;
uniform vec3 u_LightColor;
uniform float u_LightIntensity;
uniform float u_AmbientIntensity;

out vec4 fragColor;

const float PI = 3.14159265359;

// GGX / Trowbridge-Reitz Normal Distribution Function
float distributionGGX(vec3 N, vec3 H, float roughness) {
    float a = roughness * roughness;
    float a2 = a * a;
    float NdotH = max(dot(N, H), 0.0);
    float NdotH2 = NdotH * NdotH;
    
    float num = a2;
    float denom = (NdotH2 * (a2 - 1.0) + 1.0);
    denom = PI * denom * denom;
    
    return num / max(denom, 0.000001);
}

// Geometry Smith masking-shadowing function
float geometrySchlickGGX(float NdotV, float roughness) {
    float r = (roughness + 1.0);
    float k = (r * r) / 8.0;
    
    float num = NdotV;
    float denom = NdotV * (1.0 - k) + k;
    
    return num / max(denom, 0.000001);
}

float geometrySmith(vec3 N, vec3 V, vec3 L, float roughness) {
    float NdotV = max(dot(N, V), 0.0);
    float NdotL = max(dot(N, L), 0.0);
    float ggx2 = geometrySchlickGGX(NdotV, roughness);
    float ggx1 = geometrySchlickGGX(NdotL, roughness);
    
    return ggx1 * ggx2;
}

// Fresnel Schlick approximation
vec3 fresnelSchlick(float cosTheta, vec3 F0) {
    return F0 + (1.0 - F0) * pow(clamp(1.0 - cosTheta, 0.0, 1.0), 5.0);
}

void main() {
    // 1. Sample Maps with Fallbacks
    vec3 albedo = u_HasAlbedo == 1 ? texture(u_AlbedoMap, v_TexCoord).rgb : vec3(0.75, 0.72, 0.70);
    // Convert sRGB albedo to linear space
    albedo = pow(albedo, vec3(2.2));
    
    float roughness = u_HasRoughness == 1 ? texture(u_RoughnessMap, v_TexCoord).r : 0.5;
    roughness = clamp(roughness, 0.04, 1.0);
    
    float metallic = u_HasMetallic == 1 ? texture(u_MetallicMap, v_TexCoord).r : 0.0;
    metallic = clamp(metallic, 0.0, 1.0);
    
    float ao = u_HasAo == 1 ? texture(u_AoMap, v_TexCoord).r : 1.0;
    ao = clamp(ao, 0.0, 1.0);
    
    // Normal Map calculation
    vec3 N = normalize(v_Normal);
    vec3 T = normalize(v_Tangent);
    vec3 B = normalize(v_Bitangent);
    mat3 TBN = mat3(T, B, N);
    
    if (u_HasNormal == 1) {
        vec3 normalSample = texture(u_NormalMap, v_TexCoord).rgb * 2.0 - 1.0;
        if (u_NormalFormat == 1) {
            normalSample.y = -normalSample.y; // Invert green for DirectX
        }
        normalSample.xy *= u_NormalStrength;
        normalSample = normalize(normalSample);
        N = normalize(TBN * normalSample);
    }

    // Channel Solo Render Modes
    if (u_RenderMode == 1) {
        // Normal Map solo visualization (in 0..1 color space)
        fragColor = vec4(N * 0.5 + 0.5, 1.0);
        return;
    } else if (u_RenderMode == 2) {
        // Roughness solo
        fragColor = vec4(vec3(roughness), 1.0);
        return;
    } else if (u_RenderMode == 3) {
        // Metallic solo
        fragColor = vec4(vec3(metallic), 1.0);
        return;
    } else if (u_RenderMode == 4) {
        // AO solo
        fragColor = vec4(vec3(ao), 1.0);
        return;
    } else if (u_RenderMode == 5) {
        // Albedo solo
        fragColor = vec4(pow(albedo, vec3(1.0 / 2.2)), 1.0);
        return;
    }

    // 2. Full PBR Lighting Evaluation
    vec3 V = normalize(u_CameraPos - v_FragPos);
    vec3 L = normalize(u_LightPos - v_FragPos);
    vec3 H = normalize(V + L);

    // Dielectric F0 is ~0.04; metals use their albedo as F0
    vec3 F0 = vec3(0.04);
    F0 = mix(F0, albedo, metallic);

    // Direct lighting calculation
    float distance = length(u_LightPos - v_FragPos);
    float attenuation = 1.0 / (1.0 + 0.04 * distance * distance);
    vec3 radiance = u_LightColor * u_LightIntensity * attenuation;

    // Cook-Torrance BRDF terms
    float NDF = distributionGGX(N, H, roughness);
    float G = geometrySmith(N, V, L, roughness);
    vec3 F = fresnelSchlick(max(dot(H, V), 0.0), F0);

    vec3 numerator = NDF * G * F;
    float denominator = 4.0 * max(dot(N, V), 0.0) * max(dot(N, L), 0.0) + 0.0001;
    vec3 specular = numerator / denominator;

    // Energy conservation: diffuse ratio kD = 1.0 - kS (where kS = F)
    vec3 kS = F;
    vec3 kD = vec3(1.0) - kS;
    kD *= (1.0 - metallic); // Metals have no diffuse reflection

    float NdotL = max(dot(N, L), 0.0);
    vec3 Lo = (kD * albedo / PI + specular) * radiance * NdotL;

    // 3. Ambient Lighting with Spherical Environment Approximation
    // Sky gradient (cool sky above, warm ground below)
    vec3 skyColor = vec3(0.45, 0.52, 0.65);
    vec3 groundColor = vec3(0.20, 0.18, 0.17);
    vec3 envAmbient = mix(groundColor, skyColor, N.y * 0.5 + 0.5);
    
    // Specular environment reflection approximation
    vec3 R = reflect(-V, N);
    vec3 envReflectColor = mix(groundColor, skyColor, R.y * 0.5 + 0.5);
    vec3 ambientSpecular = envReflectColor * F0 * (1.0 - roughness);
    
    vec3 ambient = (kD * albedo * envAmbient + ambientSpecular) * ao * u_AmbientIntensity;

    // 4. Combined Color, Tone Mapping & Gamma Correction
    vec3 color = ambient + Lo;

    // Reinhard tone mapping
    color = color / (color + vec3(1.0));
    // Convert back from linear space to sRGB gamma 2.2
    color = pow(color, vec3(1.0 / 2.2));

    fragColor = vec4(color, 1.0);
}
"""
}
