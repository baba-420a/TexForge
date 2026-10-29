package com.example.engine.gl

import java.nio.FloatBuffer
import java.nio.ShortBuffer

/**
 * Encapsulates vertex and index buffer data for 3D rendering.
 *
 * Vertex format (interleaved, 11 floats per vertex):
 * - Position: [x, y, z] (3 floats)
 * - Normal: [nx, ny, nz] (3 floats)
 * - Tangent: [tx, ty, tz] (3 floats)
 * - Texture Coords: [u, v] (2 floats)
 */
data class MeshData(
    val vertexBuffer: FloatBuffer,
    val indexBuffer: ShortBuffer,
    val vertexCount: Int,
    val indexCount: Int,
    val strideBytes: Int = 11 * 4 // 11 floats * 4 bytes/float = 44 bytes
) {
    companion object {
        const val FLOATS_PER_VERTEX = 11
        const val POSITION_OFFSET_FLOATS = 0
        const val POSITION_SIZE = 3
        const val NORMAL_OFFSET_FLOATS = 3
        const val NORMAL_SIZE = 3
        const val TANGENT_OFFSET_FLOATS = 6
        const val TANGENT_SIZE = 3
        const val TEXCOORD_OFFSET_FLOATS = 9
        const val TEXCOORD_SIZE = 2
    }
}

enum class PreviewMeshType(val displayName: String) {
    SPHERE("Sphere"),
    CUBE("Cube"),
    CYLINDER("Cylinder"),
    PLANE("Plane Tile")
}
