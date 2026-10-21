#pragma once

#include "../matrix.hpp"

namespace glm {

inline mat4 ortho(float left, float right, float bottom, float top) {
    mat4 r(1.f);
    r[0][0] = 2.f / (right - left);
    r[1][1] = 2.f / (top - bottom);
    r[2][2] = -1.f;
    r[3][0] = -(right + left) / (right - left);
    r[3][1] = -(top + bottom) / (top - bottom);
    return r;
}

inline mat4 ortho(float left, float right, float bottom, float top, float zNear, float zFar) {
    mat4 r(1.f);
    r[0][0] = 2.f / (right - left);
    r[1][1] = 2.f / (top - bottom);
    r[2][2] = -2.f / (zFar - zNear);
    r[3][0] = -(right + left) / (right - left);
    r[3][1] = -(top + bottom) / (top - bottom);
    r[3][2] = -(zFar + zNear) / (zFar - zNear);
    return r;
}

inline mat4 frustum(float left, float right, float bottom, float top, float nearVal, float farVal) {
    mat4 r(0.f);
    r[0][0] = (2.f * nearVal) / (right - left);
    r[1][1] = (2.f * nearVal) / (top - bottom);
    r[2][0] = (right + left) / (right - left);
    r[2][1] = (top + bottom) / (top - bottom);
    r[2][2] = -(farVal + nearVal) / (farVal - nearVal);
    r[2][3] = -1.f;
    r[3][2] = -(2.f * farVal * nearVal) / (farVal - nearVal);
    return r;
}

inline mat4 perspective(float fovy, float aspect, float zNear, float zFar) {
    const float tanHalfFovy = std::tan(fovy / 2.f);
    mat4 r(0.f);
    r[0][0] = 1.f / (aspect * tanHalfFovy);
    r[1][1] = 1.f / tanHalfFovy;
    r[2][2] = -(zFar + zNear) / (zFar - zNear);
    r[2][3] = -1.f;
    r[3][2] = -(2.f * zFar * zNear) / (zFar - zNear);
    return r;
}

} // namespace glm
