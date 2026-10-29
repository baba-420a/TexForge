package com.example.engine.gl

import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import java.nio.ShortBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * Pure Kotlin generator for 3D PBR preview meshes:
 * - High-tessellation UV Sphere with smooth normals and tangents
 * - Rounded/Flat Cube with sharp edge normals
 * - Cylinder with side and cap vertices
 * - Flat Tile Quad for architectural and floor texture inspection
 */
object GeometryGenerator {

    /**
     * Generates a UV Sphere with the specified radius and tessellation.
     */
    fun generateUvSphere(
        radius: Float = 1.0f,
        stacks: Int = 48,
        slices: Int = 48
    ): MeshData {
        val vertexCount = (stacks + 1) * (slices + 1)
        val indexCount = stacks * slices * 6

        val vertexArray = FloatArray(vertexCount * MeshData.FLOATS_PER_VERTEX)
        val indexArray = ShortArray(indexCount)

        var vIdx = 0
        for (i in 0..stacks) {
            val v = i.toFloat() / stacks.toFloat()
            val phi = (v - 0.5f) * PI.toFloat() // from -PI/2 to +PI/2
            val cosPhi = cos(phi)
            val sinPhi = sin(phi)

            for (j in 0..slices) {
                val u = j.toFloat() / slices.toFloat()
                val theta = u * 2.0f * PI.toFloat() // from 0 to 2*PI
                val cosTheta = cos(theta)
                val sinTheta = sin(theta)

                // Normal (unit sphere vector)
                val nx = cosPhi * sinTheta
                val ny = sinPhi
                val nz = cosPhi * cosTheta

                // Position
                val px = nx * radius
                val py = ny * radius
                val pz = nz * radius

                // Tangent vector pointing in direction of increasing U (azimuth theta)
                // Derivative of (cosPhi * sinTheta, sinPhi, cosPhi * cosTheta) with respect to theta:
                // (cosPhi * cosTheta, 0, -cosPhi * sinTheta)
                val tx = cosTheta
                val ty = 0.0f
                val tz = -sinTheta

                // Normalize tangent
                val tLen = sqrt(tx * tx + tz * tz)
                val (ntx, ntz) = if (tLen > 1e-6f) {
                    Pair(tx / tLen, tz / tLen)
                } else {
                    Pair(1.0f, 0.0f) // Fallback at poles
                }

                // Pack interleaved vertex attributes
                vertexArray[vIdx++] = px
                vertexArray[vIdx++] = py
                vertexArray[vIdx++] = pz

                vertexArray[vIdx++] = nx
                vertexArray[vIdx++] = ny
                vertexArray[vIdx++] = nz

                vertexArray[vIdx++] = ntx
                vertexArray[vIdx++] = ty
                vertexArray[vIdx++] = ntz

                vertexArray[vIdx++] = u
                vertexArray[vIdx++] = 1.0f - v // Invert V so 0 is top
            }
        }

        // Indices for two triangles per quad
        var iIdx = 0
        val rowStride = slices + 1
        for (i in 0 until stacks) {
            for (j in 0 until slices) {
                val first = (i * rowStride + j).toShort()
                val second = (first + rowStride).toShort()

                // Triangle 1
                indexArray[iIdx++] = first
                indexArray[iIdx++] = second
                indexArray[iIdx++] = (first + 1).toShort()

                // Triangle 2
                indexArray[iIdx++] = (first + 1).toShort()
                indexArray[iIdx++] = second
                indexArray[iIdx++] = (second + 1).toShort()
            }
        }

        val vBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexArray)
                position(0)
            }

        val iBuffer = ByteBuffer.allocateDirect(indexArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexArray)
                position(0)
            }

        return MeshData(
            vertexBuffer = vBuffer,
            indexBuffer = iBuffer,
            vertexCount = vertexCount,
            indexCount = indexCount
        )
    }

    /**
     * Generates a 6-sided Cube centered at origin.
     */
    fun generateCube(size: Float = 1.4f): MeshData {
        val h = size / 2.0f
        // 6 faces * 4 vertices = 24 vertices
        // 6 faces * 2 triangles * 3 indices = 36 indices
        val vertexCount = 24
        val indexCount = 36

        val vertexArray = FloatArray(vertexCount * MeshData.FLOATS_PER_VERTEX)
        val indexArray = ShortArray(indexCount)

        data class FaceDef(
            val p0: FloatArray, val p1: FloatArray, val p2: FloatArray, val p3: FloatArray,
            val normal: FloatArray, val tangent: FloatArray
        )

        val faces = listOf(
            // Front (+Z)
            FaceDef(floatArrayOf(-h, -h,  h), floatArrayOf( h, -h,  h), floatArrayOf( h,  h,  h), floatArrayOf(-h,  h,  h),
                floatArrayOf(0f, 0f, 1f), floatArrayOf(1f, 0f, 0f)),
            // Back (-Z)
            FaceDef(floatArrayOf( h, -h, -h), floatArrayOf(-h, -h, -h), floatArrayOf(-h,  h, -h), floatArrayOf( h,  h, -h),
                floatArrayOf(0f, 0f, -1f), floatArrayOf(-1f, 0f, 0f)),
            // Top (+Y)
            FaceDef(floatArrayOf(-h,  h,  h), floatArrayOf( h,  h,  h), floatArrayOf( h,  h, -h), floatArrayOf(-h,  h, -h),
                floatArrayOf(0f, 1f, 0f), floatArrayOf(1f, 0f, 0f)),
            // Bottom (-Y)
            FaceDef(floatArrayOf(-h, -h, -h), floatArrayOf( h, -h, -h), floatArrayOf( h, -h,  h), floatArrayOf(-h, -h,  h),
                floatArrayOf(0f, -1f, 0f), floatArrayOf(1f, 0f, 0f)),
            // Right (+X)
            FaceDef(floatArrayOf( h, -h,  h), floatArrayOf( h, -h, -h), floatArrayOf( h,  h, -h), floatArrayOf( h,  h,  h),
                floatArrayOf(1f, 0f, 0f), floatArrayOf(0f, 0f, -1f)),
            // Left (-X)
            FaceDef(floatArrayOf(-h, -h, -h), floatArrayOf(-h, -h,  h), floatArrayOf(-h,  h,  h), floatArrayOf(-h,  h, -h),
                floatArrayOf(-1f, 0f, 0f), floatArrayOf(0f, 0f, 1f))
        )

        var vIdx = 0
        var iIdx = 0
        var baseVertex = 0

        val uvs = listOf(
            floatArrayOf(0f, 1f),
            floatArrayOf(1f, 1f),
            floatArrayOf(1f, 0f),
            floatArrayOf(0f, 0f)
        )

        for (face in faces) {
            val pts = listOf(face.p0, face.p1, face.p2, face.p3)
            for (v in 0 until 4) {
                val p = pts[v]
                val uv = uvs[v]
                // Position
                vertexArray[vIdx++] = p[0]
                vertexArray[vIdx++] = p[1]
                vertexArray[vIdx++] = p[2]
                // Normal
                vertexArray[vIdx++] = face.normal[0]
                vertexArray[vIdx++] = face.normal[1]
                vertexArray[vIdx++] = face.normal[2]
                // Tangent
                vertexArray[vIdx++] = face.tangent[0]
                vertexArray[vIdx++] = face.tangent[1]
                vertexArray[vIdx++] = face.tangent[2]
                // UV
                vertexArray[vIdx++] = uv[0]
                vertexArray[vIdx++] = uv[1]
            }

            // Two triangles for this face
            indexArray[iIdx++] = baseVertex.toShort()
            indexArray[iIdx++] = (baseVertex + 1).toShort()
            indexArray[iIdx++] = (baseVertex + 2).toShort()

            indexArray[iIdx++] = baseVertex.toShort()
            indexArray[iIdx++] = (baseVertex + 2).toShort()
            indexArray[iIdx++] = (baseVertex + 3).toShort()

            baseVertex += 4
        }

        val vBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexArray)
                position(0)
            }

        val iBuffer = ByteBuffer.allocateDirect(indexArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexArray)
                position(0)
            }

        return MeshData(
            vertexBuffer = vBuffer,
            indexBuffer = iBuffer,
            vertexCount = vertexCount,
            indexCount = indexCount
        )
    }

    /**
     * Generates a flat tile Quad centered on XY plane facing +Z.
     */
    fun generatePlane(size: Float = 2.0f): MeshData {
        val h = size / 2.0f
        val vertexCount = 4
        val indexCount = 6

        val vertexArray = floatArrayOf(
            // P(x,y,z), N(x,y,z), T(x,y,z), UV(u,v)
            -h, -h, 0f,   0f, 0f, 1f,   1f, 0f, 0f,   0f, 1f,
             h, -h, 0f,   0f, 0f, 1f,   1f, 0f, 0f,   1f, 1f,
             h,  h, 0f,   0f, 0f, 1f,   1f, 0f, 0f,   1f, 0f,
            -h,  h, 0f,   0f, 0f, 1f,   1f, 0f, 0f,   0f, 0f
        )

        val indexArray = shortArrayOf(
            0, 1, 2,
            0, 2, 3
        )

        val vBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexArray)
                position(0)
            }

        val iBuffer = ByteBuffer.allocateDirect(indexArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexArray)
                position(0)
            }

        return MeshData(
            vertexBuffer = vBuffer,
            indexBuffer = iBuffer,
            vertexCount = vertexCount,
            indexCount = indexCount
        )
    }

    /**
     * Generates a 3D Cylinder with smooth sides and flat caps.
     */
    fun generateCylinder(
        radius: Float = 0.8f,
        height: Float = 1.8f,
        segments: Int = 40
    ): MeshData {
        val halfH = height / 2.0f
        val sideVertexCount = (segments + 1) * 2
        val capVertexCount = (segments + 2) * 2
        val totalVertices = sideVertexCount + capVertexCount

        val sideIndices = segments * 6
        val capIndices = segments * 3 * 2
        val totalIndices = sideIndices + capIndices

        val vertexArray = FloatArray(totalVertices * MeshData.FLOATS_PER_VERTEX)
        val indexArray = ShortArray(totalIndices)

        var vIdx = 0
        var iIdx = 0

        // 1. Side cylinder wall
        for (i in 0..segments) {
            val u = i.toFloat() / segments.toFloat()
            val theta = u * 2.0f * PI.toFloat()
            val cosT = cos(theta)
            val sinT = sin(theta)

            val nx = cosT
            val ny = 0.0f
            val nz = sinT

            val tx = -sinT
            val ty = 0.0f
            val tz = cosT

            // Bottom vertex
            vertexArray[vIdx++] = nx * radius
            vertexArray[vIdx++] = -halfH
            vertexArray[vIdx++] = nz * radius
            vertexArray[vIdx++] = nx
            vertexArray[vIdx++] = ny
            vertexArray[vIdx++] = nz
            vertexArray[vIdx++] = tx
            vertexArray[vIdx++] = ty
            vertexArray[vIdx++] = tz
            vertexArray[vIdx++] = u
            vertexArray[vIdx++] = 1.0f

            // Top vertex
            vertexArray[vIdx++] = nx * radius
            vertexArray[vIdx++] = halfH
            vertexArray[vIdx++] = nz * radius
            vertexArray[vIdx++] = nx
            vertexArray[vIdx++] = ny
            vertexArray[vIdx++] = nz
            vertexArray[vIdx++] = tx
            vertexArray[vIdx++] = ty
            vertexArray[vIdx++] = tz
            vertexArray[vIdx++] = u
            vertexArray[vIdx++] = 0.0f
        }

        for (i in 0 until segments) {
            val b1 = (i * 2).toShort()
            val t1 = (b1 + 1).toShort()
            val b2 = (b1 + 2).toShort()
            val t2 = (b1 + 3).toShort()

            indexArray[iIdx++] = b1
            indexArray[iIdx++] = b2
            indexArray[iIdx++] = t1

            indexArray[iIdx++] = t1
            indexArray[iIdx++] = b2
            indexArray[iIdx++] = t2
        }

        // 2. Top cap
        val topCapCenter = (vIdx / MeshData.FLOATS_PER_VERTEX).toShort()
        vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = halfH; vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = 1f;    vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 1f; vertexArray[vIdx++] = 0f;    vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 0.5f; vertexArray[vIdx++] = 0.5f

        for (i in 0..segments) {
            val u = i.toFloat() / segments.toFloat()
            val theta = u * 2.0f * PI.toFloat()
            val cosT = cos(theta)
            val sinT = sin(theta)

            vertexArray[vIdx++] = cosT * radius
            vertexArray[vIdx++] = halfH
            vertexArray[vIdx++] = sinT * radius
            vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = 1f; vertexArray[vIdx++] = 0f
            vertexArray[vIdx++] = 1f; vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = 0f
            vertexArray[vIdx++] = 0.5f + 0.5f * cosT
            vertexArray[vIdx++] = 0.5f + 0.5f * sinT
        }

        for (i in 0 until segments) {
            indexArray[iIdx++] = topCapCenter
            indexArray[iIdx++] = (topCapCenter + 1 + i).toShort()
            indexArray[iIdx++] = (topCapCenter + 2 + i).toShort()
        }

        // 3. Bottom cap
        val bottomCapCenter = (vIdx / MeshData.FLOATS_PER_VERTEX).toShort()
        vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = -halfH; vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = -1f;    vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 1f; vertexArray[vIdx++] = 0f;     vertexArray[vIdx++] = 0f
        vertexArray[vIdx++] = 0.5f; vertexArray[vIdx++] = 0.5f

        for (i in 0..segments) {
            val u = i.toFloat() / segments.toFloat()
            val theta = u * 2.0f * PI.toFloat()
            val cosT = cos(theta)
            val sinT = sin(theta)

            vertexArray[vIdx++] = cosT * radius
            vertexArray[vIdx++] = -halfH
            vertexArray[vIdx++] = sinT * radius
            vertexArray[vIdx++] = 0f; vertexArray[vIdx++] = -1f; vertexArray[vIdx++] = 0f
            vertexArray[vIdx++] = 1f; vertexArray[vIdx++] = 0f;  vertexArray[vIdx++] = 0f
            vertexArray[vIdx++] = 0.5f + 0.5f * cosT
            vertexArray[vIdx++] = 0.5f + 0.5f * sinT
        }

        for (i in 0 until segments) {
            indexArray[iIdx++] = bottomCapCenter
            indexArray[iIdx++] = (bottomCapCenter + 2 + i).toShort()
            indexArray[iIdx++] = (bottomCapCenter + 1 + i).toShort()
        }

        val vBuffer = ByteBuffer.allocateDirect(vertexArray.size * 4)
            .order(ByteOrder.nativeOrder())
            .asFloatBuffer()
            .apply {
                put(vertexArray)
                position(0)
            }

        val iBuffer = ByteBuffer.allocateDirect(indexArray.size * 2)
            .order(ByteOrder.nativeOrder())
            .asShortBuffer()
            .apply {
                put(indexArray)
                position(0)
            }

        return MeshData(
            vertexBuffer = vBuffer,
            indexBuffer = iBuffer,
            vertexCount = totalVertices,
            indexCount = totalIndices
        )
    }
}
