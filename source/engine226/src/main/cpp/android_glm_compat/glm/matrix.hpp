#pragma once

/*
 * Minimal GLM compatibility subset for the UT99DC Android bring-up.
 *
 * Upstream NOpenGLESDrv uses glm::mat4 mainly as a 4x4 float matrix with
 * column-major storage and &m[0][0] upload semantics. Shipping the full GLM
 * tree is unnecessary for this first Android/OUYA compile pass, so this header
 * provides the tiny subset the renderer needs.
 */

#include <cmath>
#include <cstddef>

namespace glm {

struct mat4 {
    float v[16];

    constexpr mat4()
        : v{1.f,0.f,0.f,0.f,
            0.f,1.f,0.f,0.f,
            0.f,0.f,1.f,0.f,
            0.f,0.f,0.f,1.f} {}

    explicit constexpr mat4(float diagonal)
        : v{diagonal,0.f,0.f,0.f,
            0.f,diagonal,0.f,0.f,
            0.f,0.f,diagonal,0.f,
            0.f,0.f,0.f,diagonal} {}

    constexpr mat4(
        float x0, float y0, float z0, float w0,
        float x1, float y1, float z1, float w1,
        float x2, float y2, float z2, float w2,
        float x3, float y3, float z3, float w3)
        : v{x0,y0,z0,w0, x1,y1,z1,w1, x2,y2,z2,w2, x3,y3,z3,w3} {}

    float* operator[](std::size_t column) { return &v[column * 4]; }
    const float* operator[](std::size_t column) const { return &v[column * 4]; }
};

inline mat4 operator*(const mat4& a, const mat4& b) {
    mat4 r(0.f);
    for (int c = 0; c < 4; ++c) {
        for (int row = 0; row < 4; ++row) {
            r[c][row] =
                a[0][row] * b[c][0] +
                a[1][row] * b[c][1] +
                a[2][row] * b[c][2] +
                a[3][row] * b[c][3];
        }
    }
    return r;
}

inline float radians(float degrees) {
    return degrees * 0.01745329251994329576923690768489f;
}

inline mat4 transpose(const mat4& m) {
    mat4 r(0.f);
    for (int c = 0; c < 4; ++c) {
        for (int row = 0; row < 4; ++row) {
            r[c][row] = m[row][c];
        }
    }
    return r;
}

} // namespace glm
