#extension GL_OES_standard_derivatives : enable

precision mediump float;

uniform bool u_UseVaryingNormal;
uniform bool u_UseTexAmbient;
uniform bool u_UseTexDiffuse;
uniform bool u_UseTexSpecular;

uniform sampler2D u_TexAmbient;
uniform sampler2D u_TexDiffuse;
uniform sampler2D u_TexSpecular;

uniform vec3 u_MtlAmbient;
uniform vec3 u_MtlDiffuse;
uniform vec3 u_MtlSpecular;

uniform float u_D;
uniform float u_SpecularPower;

uniform vec4 u_LightingParameters;

varying vec3 v_ViewPosition;
varying vec3 v_ViewNormal;
varying vec2 v_TexCoord;

vec4 getColor(bool useTex, sampler2D tex, vec3 mtl);
vec3 computeDiffuse(vec3 viewNormal, vec3 rawDiffuseColor);
vec3 computeSpecular(vec3 viewNormal, vec3 viewFragmentDirection, vec3 rawSpecularColor);

void main() {
    const float kGamma = 0.4545454;
    vec3 viewFragmentDirection = normalize(v_ViewPosition);
    vec3 dX = dFdx(v_ViewPosition);
    vec3 dY = dFdy(v_ViewPosition);
    vec3 viewNormal;
    if (u_UseVaryingNormal) {
        viewNormal = normalize(v_ViewNormal);
    } else {
        viewNormal = normalize(cross(dX, dY));
    }

    vec4 rawAmbientColor = getColor(u_UseTexAmbient, u_TexAmbient, u_MtlAmbient) * 0.1;
    vec4 rawDiffuseColor = getColor(u_UseTexDiffuse, u_TexDiffuse, u_MtlDiffuse);
    vec4 rawSpecularColor = getColor(u_UseTexSpecular, u_TexSpecular, u_MtlSpecular);

    vec3 diffuse = computeDiffuse(viewNormal, rawDiffuseColor.rgb);
    vec3 specular = computeSpecular(viewNormal, viewFragmentDirection, rawSpecularColor.rgb);

    gl_FragColor.a = rawDiffuseColor.a * u_D;
    gl_FragColor.rgb = pow(rawAmbientColor.rgb + diffuse + specular, vec3(kGamma));
}

vec4 getColor(bool useTex, sampler2D tex, vec3 mtl) {
    const float kInverseGamma = 2.2;
    vec4 color;
    if (useTex) {
        color = texture2D(tex, vec2(v_TexCoord.x, 1.0 - v_TexCoord.y));
    } else {
        color = vec4(mtl.r, mtl.g, mtl.b, 1.0);
    }
    color.rgb = pow(color.rgb, vec3(kInverseGamma));
    return color;
}

vec3 computeDiffuse(vec3 viewNormal, vec3 rawDiffuseColor) {
    vec3 diffuse = u_LightingParameters.w * rawDiffuseColor
            * max(abs(dot(u_LightingParameters.xyz, viewNormal)), 0.0);
    return clamp(diffuse, 0.0, 1.0);
}

vec3 computeSpecular(vec3 viewNormal, vec3 viewFragmentDirection, vec3 rawSpecularColor) {
    vec3 reflectedLightDirection = reflect(-u_LightingParameters.xyz, viewNormal);
    float specularStrength = clamp(dot(-viewFragmentDirection, reflectedLightDirection), 0.0, 1.0);
    vec3 specular = u_LightingParameters.w * rawSpecularColor *
            pow(specularStrength, u_SpecularPower);
    return clamp(specular, 0.0, 1.0);
}