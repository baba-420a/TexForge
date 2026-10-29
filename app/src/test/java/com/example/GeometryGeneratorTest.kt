package com.example

import com.example.engine.gl.GeometryGenerator
import com.example.engine.gl.MeshData
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Unit tests verifying pure-Kotlin 3D geometry math:
 * - UV Sphere vertex tessellation, surface normal normalization, and tangent orthogonality
 * - UV coordinate bounds ([0.0, 1.0])
 * - Cube, Plane, and Cylinder mesh integrity
 */
class GeometryGeneratorTest {

    @Test
    fun `uv sphere generates correct vertex count and normalized normals`() {
        val stacks = 32
        val slices = 32
        val radius = 1.0f

        val sphere = GeometryGenerator.generateUvSphere(radius = radius, stacks = stacks, slices = slices)

        val expectedVertices = (stacks + 1) * (slices + 1)
        val expectedIndices = stacks * slices * 6

        assertEquals(expectedVertices, sphere.vertexCount)
        assertEquals(expectedIndices, sphere.indexCount)

        val buffer = sphere.vertexBuffer
        buffer.position(0)

        for (i in 0 until sphere.vertexCount) {
            val base = i * MeshData.FLOATS_PER_VERTEX
            val px = buffer.get(base + MeshData.POSITION_OFFSET_FLOATS)
            val py = buffer.get(base + MeshData.POSITION_OFFSET_FLOATS + 1)
            val pz = buffer.get(base + MeshData.POSITION_OFFSET_FLOATS + 2)

            val nx = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS)
            val ny = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 1)
            val nz = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 2)

            val tx = buffer.get(base + MeshData.TANGENT_OFFSET_FLOATS)
            val ty = buffer.get(base + MeshData.TANGENT_OFFSET_FLOATS + 1)
            val tz = buffer.get(base + MeshData.TANGENT_OFFSET_FLOATS + 2)

            val u = buffer.get(base + MeshData.TEXCOORD_OFFSET_FLOATS)
            val v = buffer.get(base + MeshData.TEXCOORD_OFFSET_FLOATS + 1)

            // 1. Position radius check
            val posLen = sqrt(px * px + py * py + pz * pz)
            assertEquals("Position radius mismatch at vertex $i", radius, posLen, 1e-4f)

            // 2. Normal vector normalization check (|N| == 1.0)
            val normalLen = sqrt(nx * nx + ny * ny + nz * nz)
            assertEquals("Normal length should be 1.0 at vertex $i", 1.0f, normalLen, 1e-4f)

            // 3. Tangent vector normalization check (|T| == 1.0)
            val tangentLen = sqrt(tx * tx + ty * ty + tz * tz)
            assertEquals("Tangent length should be 1.0 at vertex $i", 1.0f, tangentLen, 1e-4f)

            // 4. Tangent and Normal orthogonality: N · T == 0
            val dotProduct = nx * tx + ny * ty + nz * tz
            assertTrue(
                "Normal and Tangent should be orthogonal at vertex $i (dot product was $dotProduct)",
                abs(dotProduct) < 0.05f
            )

            // 5. UV coordinates range check [0.0, 1.0]
            assertTrue("U coordinate out of range: $u", u in -1e-5f..1.00001f)
            assertTrue("V coordinate out of range: $v", v in -1e-5f..1.00001f)
        }
    }

    @Test
    fun `cube mesh generates 24 vertices and 36 indices with unit normals`() {
        val size = 1.5f
        val cube = GeometryGenerator.generateCube(size = size)

        assertEquals(24, cube.vertexCount)
        assertEquals(36, cube.indexCount)

        val buffer = cube.vertexBuffer
        buffer.position(0)

        for (i in 0 until cube.vertexCount) {
            val base = i * MeshData.FLOATS_PER_VERTEX
            val nx = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS)
            val ny = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 1)
            val nz = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 2)

            val normalLen = sqrt(nx * nx + ny * ny + nz * nz)
            assertEquals(1.0f, normalLen, 1e-4f)
        }
    }

    @Test
    fun `plane mesh generates 4 vertices and 6 indices facing positive Z`() {
        val size = 2.0f
        val plane = GeometryGenerator.generatePlane(size = size)

        assertEquals(4, plane.vertexCount)
        assertEquals(6, plane.indexCount)

        val buffer = plane.vertexBuffer
        buffer.position(0)

        for (i in 0 until plane.vertexCount) {
            val base = i * MeshData.FLOATS_PER_VERTEX
            val nz = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 2)
            assertEquals("Plane normal must face +Z", 1.0f, nz, 1e-4f)
        }
    }

    @Test
    fun `cylinder mesh generates valid vertices with normalized side normals`() {
        val cylinder = GeometryGenerator.generateCylinder(radius = 1.0f, height = 2.0f, segments = 24)

        assertTrue("Cylinder vertex count should be positive", cylinder.vertexCount > 0)
        assertTrue("Cylinder index count should be positive", cylinder.indexCount > 0)

        val buffer = cylinder.vertexBuffer
        buffer.position(0)

        for (i in 0 until cylinder.vertexCount) {
            val base = i * MeshData.FLOATS_PER_VERTEX
            val nx = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS)
            val ny = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 1)
            val nz = buffer.get(base + MeshData.NORMAL_OFFSET_FLOATS + 2)

            val normalLen = sqrt(nx * nx + ny * ny + nz * nz)
            assertEquals("Cylinder normal must be normalized", 1.0f, normalLen, 1e-3f)
        }
    }
}
