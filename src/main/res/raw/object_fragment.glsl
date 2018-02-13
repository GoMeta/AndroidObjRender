/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

precision mediump float;

uniform bool u_TextureValid;
uniform sampler2D u_Texture;

uniform vec4 u_LightingParameters;
uniform vec2 u_MaterialParameters;
uniform vec3 u_MaterialAmbientLighting;
uniform vec3 u_MaterialDiffuseLighting;
uniform vec3 u_MaterialSpecularLighting;

varying vec3 v_ViewPosition;
varying vec3 v_ViewNormal;
varying vec2 v_TexCoord;

void main() {
    // We support approximate sRGB gamma.
    const float kGamma = 0.4545454;
    const float kInverseGamma = 2.2;

    // Unpack lighting and material parameters for better naming.
    vec3 viewLightDirection = u_LightingParameters.xyz;
    float lightIntensity = u_LightingParameters.w;

    float materialSpecularPower = u_MaterialParameters.x;
    float materialAlpha = u_MaterialParameters.y;
    vec3 materialDiffuse = u_MaterialDiffuseLighting;
    vec3 materialSpecular = u_MaterialSpecularLighting;

    // Normalize varying parameters, because they are linearly interpolated in the vertex shader.
    vec3 viewFragmentDirection = normalize(v_ViewPosition);
    vec3 viewNormal = normalize(v_ViewNormal);

    // Ambient light is unaffected by the light intensity.
    vec3 ambient = u_MaterialAmbientLighting;

    // Approximate a hemisphere light (not a harsh directional light).
    vec3 diffuse = lightIntensity * materialDiffuse *
            0.5 * (dot(viewNormal, viewLightDirection) + 1.0);

    // Compute specular light.
    vec3 reflectedLightDirection = reflect(viewLightDirection, viewNormal);
    float specularStrength = max(0.0, dot(viewFragmentDirection, reflectedLightDirection));
    vec3 specular = lightIntensity * materialSpecular *
            pow(specularStrength, materialSpecularPower);

    // Apply inverse SRGB gamma to the texture before making lighting calculations.
    // Flip the y-texture coordinate to address the texture from top-left.
    vec4 objectColor;
    if (u_TextureValid) {
        objectColor = texture2D(u_Texture, vec2(v_TexCoord.x, 1.0 - v_TexCoord.y));
        objectColor.rgb = pow(objectColor.rgb, vec3(kInverseGamma));
    } else {
        gl_FragColor.a = materialAlpha;
        gl_FragColor.rgb = ambient + diffuse + specular;
        return;
    }

    // Apply SRGB gamma before writing the fragment color.
    gl_FragColor.a = objectColor.a * materialAlpha;
    gl_FragColor.rgb = pow(objectColor.rgb * (ambient + diffuse) + specular, vec3(kGamma));
}
