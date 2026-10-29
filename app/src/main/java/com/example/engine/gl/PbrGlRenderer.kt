package com.example.engine.gl

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLSurfaceView
import android.opengl.GLUtils
import android.opengl.Matrix
import android.util.Log
import com.example.data.preferences.NormalFormat
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.cos
import kotlin.math.sin

/**
 * Native Android OpenGL ES 3.0 Renderer for Real-Time PBR Material Preview.
 *
 * Renders Cook-Torrance microfacet BRDF on interactive 3D UV Sphere, Cube,
 * Cylinder, or Plane Quad.
 */
class PbrGlRenderer : GLSurfaceView.Renderer {

    private val tag = "PbrGlRenderer"

    // Shaders and Program
    private var programId = 0

    // Attribute Locations
    private var aPositionLoc = 0
    private var aNormalLoc = 1
    private var aTangentLoc = 2
    private var aTexCoordLoc = 3

    // Uniform Locations
    private var uModelMatrixLoc = -1
    private var uViewMatrixLoc = -1
    private var uProjectionMatrixLoc = -1
    private var uNormalMatrixLoc = -1
    private var uTilingLoc = -1

    private var uAlbedoMapLoc = -1
    private var uNormalMapLoc = -1
    private var uRoughnessMapLoc = -1
    private var uAoMapLoc = -1
    private var uMetallicMapLoc = -1

    private var uHasAlbedoLoc = -1
    private var uHasNormalLoc = -1
    private var uHasRoughnessLoc = -1
    private var uHasAoLoc = -1
    private var uHasMetallicLoc = -1

    private var uNormalFormatLoc = -1
    private var uNormalStrengthLoc = -1
    private var uRenderModeLoc = -1

    private var uCameraPosLoc = -1
    private var uLightPosLoc = -1
    private var uLightColorLoc = -1
    private var uLightIntensityLoc = -1
    private var uAmbientIntensityLoc = -1

    // Meshes
    private var sphereMesh: MeshData? = null
    private var cubeMesh: MeshData? = null
    private var planeMesh: MeshData? = null
    private var cylinderMesh: MeshData? = null

    // Textures IDs
    private var albedoTexId = 0
    private var normalTexId = 0
    private var roughnessTexId = 0
    private var aoTexId = 0
    private var metallicTexId = 0
    private var fallbackWhiteTexId = 0
    private var fallbackNormalTexId = 0

    // Texture Bitmaps queued for upload
    private val textureLock = Any()
    private var pendingAlbedo: Bitmap? = null
    private var pendingNormal: Bitmap? = null
    private var pendingRoughness: Bitmap? = null
    private var pendingAo: Bitmap? = null
    private var pendingMetallic: Bitmap? = null
    private var hasPendingTextures = false

    // Transform Matrices
    private val modelMatrix = FloatArray(16)
    private val viewMatrix = FloatArray(16)
    private val projectionMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(9)
    private val mvMatrix = FloatArray(16)

    // Interactive Camera Parameters
    @Volatile var orbitYaw = 0.0f
    @Volatile var orbitPitch = 15.0f
    @Volatile var cameraDistance = 2.8f
    @Volatile var autoRotate = true

    // Interactive Material & Lighting Parameters
    @Volatile var currentMeshType = PreviewMeshType.SPHERE
    @Volatile var normalFormat = NormalFormat.OPEN_GL
    @Volatile var normalStrength = 1.0f
    @Volatile var renderMode = 0 // 0=PBR, 1=Normal, 2=Roughness, 3=Metallic, 4=AO, 5=Albedo
    @Volatile var lightAzimuth = 45.0f
    @Volatile var lightElevation = 35.0f
    @Volatile var lightIntensity = 2.5f
    @Volatile var ambientIntensity = 1.0f
    @Volatile var uvTiling = 1.0f

    // Surface Viewport Dimensions
    private var surfaceWidth = 1
    private var surfaceHeight = 1

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Dark metallic studio background
        GLES30.glClearColor(0.08f, 0.09f, 0.11f, 1.0f)
        GLES30.glEnable(GLES30.GL_DEPTH_TEST)
        GLES30.glDepthFunc(GLES30.GL_LEQUAL)
        GLES30.glEnable(GLES30.GL_CULL_FACE)
        GLES30.glCullFace(GLES30.GL_BACK)

        initShaders()
        initMeshes()
        initFallbackTextures()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        GLES30.glViewport(0, 0, width, height)

        val aspect = width.toFloat() / height.toFloat().coerceAtLeast(1.0f)
        Matrix.perspectiveM(projectionMatrix, 0, 45.0f, aspect, 0.1f, 50.0f)
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT or GLES30.GL_DEPTH_BUFFER_BIT)

        // Process any queued textures
        uploadPendingTexturesIfAvailable()

        if (programId == 0) return

        GLES30.glUseProgram(programId)

        // Auto-rotation increment
        if (autoRotate) {
            orbitYaw = (orbitYaw + 0.35f) % 360f
        }

        // Camera Eye Position
        val radPitch = Math.toRadians(orbitPitch.toDouble())
        val radYaw = Math.toRadians(orbitYaw.toDouble())
        val eyeX = (cameraDistance * cos(radPitch) * sin(radYaw)).toFloat()
        val eyeY = (cameraDistance * sin(radPitch)).toFloat()
        val eyeZ = (cameraDistance * cos(radPitch) * cos(radYaw)).toFloat()

        Matrix.setLookAtM(
            viewMatrix, 0,
            eyeX, eyeY, eyeZ,
            0.0f, 0.0f, 0.0f, // Center target
            0.0f, 1.0f, 0.0f  // Up vector
        )

        // Model Matrix (identity or plane rotation)
        Matrix.setIdentityM(modelMatrix, 0)
        if (currentMeshType == PreviewMeshType.PLANE) {
            // Angle flat tile towards camera for better inspection
            Matrix.rotateM(modelMatrix, 0, -25.0f, 1.0f, 0.0f, 0.0f)
        }

        // Normal Matrix = inverse transpose of upper 3x3 of Model Matrix
        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, modelMatrix, 0)
        computeNormalMatrix(modelMatrix, normalMatrix)

        // Light Position in World Space
        val radLightElev = Math.toRadians(lightElevation.toDouble())
        val radLightAzim = Math.toRadians(lightAzimuth.toDouble())
        val lightDist = 5.0f
        val lightX = (lightDist * cos(radLightElev) * sin(radLightAzim)).toFloat()
        val lightY = (lightDist * sin(radLightElev)).toFloat()
        val lightZ = (lightDist * cos(radLightElev) * cos(radLightAzim)).toFloat()

        // Bind Uniforms
        GLES30.glUniformMatrix4fv(uModelMatrixLoc, 1, false, modelMatrix, 0)
        GLES30.glUniformMatrix4fv(uViewMatrixLoc, 1, false, viewMatrix, 0)
        GLES30.glUniformMatrix4fv(uProjectionMatrixLoc, 1, false, projectionMatrix, 0)
        GLES30.glUniformMatrix3fv(uNormalMatrixLoc, 1, false, normalMatrix, 0)
        GLES30.glUniform1f(uTilingLoc, uvTiling)

        GLES30.glUniform3f(uCameraPosLoc, eyeX, eyeY, eyeZ)
        GLES30.glUniform3f(uLightPosLoc, lightX, lightY, lightZ)
        GLES30.glUniform3f(uLightColorLoc, 1.0f, 0.98f, 0.95f) // Crisp natural light
        GLES30.glUniform1f(uLightIntensityLoc, lightIntensity)
        GLES30.glUniform1f(uAmbientIntensityLoc, ambientIntensity)

        GLES30.glUniform1i(uNormalFormatLoc, if (normalFormat == NormalFormat.DIRECT_X) 1 else 0)
        GLES30.glUniform1f(uNormalStrengthLoc, normalStrength)
        GLES30.glUniform1i(uRenderModeLoc, renderMode)

        // Bind Texture Samplers to Texture Units 0 to 4
        // Unit 0: Albedo
        GLES30.glActiveTexture(GLES30.GL_TEXTURE0)
        val activeAlbedo = if (albedoTexId > 0) albedoTexId else fallbackWhiteTexId
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeAlbedo)
        GLES30.glUniform1i(uAlbedoMapLoc, 0)
        GLES30.glUniform1i(uHasAlbedoLoc, if (albedoTexId > 0) 1 else 0)

        // Unit 1: Normal
        GLES30.glActiveTexture(GLES30.GL_TEXTURE1)
        val activeNormal = if (normalTexId > 0) normalTexId else fallbackNormalTexId
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeNormal)
        GLES30.glUniform1i(uNormalMapLoc, 1)
        GLES30.glUniform1i(uHasNormalLoc, if (normalTexId > 0) 1 else 0)

        // Unit 2: Roughness
        GLES30.glActiveTexture(GLES30.GL_TEXTURE2)
        val activeRoughness = if (roughnessTexId > 0) roughnessTexId else fallbackWhiteTexId
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeRoughness)
        GLES30.glUniform1i(uRoughnessMapLoc, 2)
        GLES30.glUniform1i(uHasRoughnessLoc, if (roughnessTexId > 0) 1 else 0)

        // Unit 3: AO
        GLES30.glActiveTexture(GLES30.GL_TEXTURE3)
        val activeAo = if (aoTexId > 0) aoTexId else fallbackWhiteTexId
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeAo)
        GLES30.glUniform1i(uAoMapLoc, 3)
        GLES30.glUniform1i(uHasAoLoc, if (aoTexId > 0) 1 else 0)

        // Unit 4: Metallic
        GLES30.glActiveTexture(GLES30.GL_TEXTURE4)
        val activeMetallic = if (metallicTexId > 0) metallicTexId else fallbackWhiteTexId
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, activeMetallic)
        GLES30.glUniform1i(uMetallicMapLoc, 4)
        GLES30.glUniform1i(uHasMetallicLoc, if (metallicTexId > 0) 1 else 0)

        // Draw Active Mesh
        val mesh = when (currentMeshType) {
            PreviewMeshType.SPHERE -> sphereMesh
            PreviewMeshType.CUBE -> cubeMesh
            PreviewMeshType.PLANE -> planeMesh
            PreviewMeshType.CYLINDER -> cylinderMesh
        }

        mesh?.let { drawMesh(it) }
    }

    private fun drawMesh(mesh: MeshData) {
        val stride = mesh.strideBytes

        // Enable attributes
        GLES30.glEnableVertexAttribArray(aPositionLoc)
        GLES30.glEnableVertexAttribArray(aNormalLoc)
        GLES30.glEnableVertexAttribArray(aTangentLoc)
        GLES30.glEnableVertexAttribArray(aTexCoordLoc)

        // Position: 3 floats, offset 0
        mesh.vertexBuffer.position(MeshData.POSITION_OFFSET_FLOATS)
        GLES30.glVertexAttribPointer(
            aPositionLoc, MeshData.POSITION_SIZE, GLES30.GL_FLOAT, false, stride, mesh.vertexBuffer
        )

        // Normal: 3 floats, offset 3
        mesh.vertexBuffer.position(MeshData.NORMAL_OFFSET_FLOATS)
        GLES30.glVertexAttribPointer(
            aNormalLoc, MeshData.NORMAL_SIZE, GLES30.GL_FLOAT, false, stride, mesh.vertexBuffer
        )

        // Tangent: 3 floats, offset 6
        mesh.vertexBuffer.position(MeshData.TANGENT_OFFSET_FLOATS)
        GLES30.glVertexAttribPointer(
            aTangentLoc, MeshData.TANGENT_SIZE, GLES30.GL_FLOAT, false, stride, mesh.vertexBuffer
        )

        // TexCoord: 2 floats, offset 9
        mesh.vertexBuffer.position(MeshData.TEXCOORD_OFFSET_FLOATS)
        GLES30.glVertexAttribPointer(
            aTexCoordLoc, MeshData.TEXCOORD_SIZE, GLES30.GL_FLOAT, false, stride, mesh.vertexBuffer
        )

        mesh.indexBuffer.position(0)
        GLES30.glDrawElements(
            GLES30.GL_TRIANGLES,
            mesh.indexCount,
            GLES30.GL_UNSIGNED_SHORT,
            mesh.indexBuffer
        )

        GLES30.glDisableVertexAttribArray(aPositionLoc)
        GLES30.glDisableVertexAttribArray(aNormalLoc)
        GLES30.glDisableVertexAttribArray(aTangentLoc)
        GLES30.glDisableVertexAttribArray(aTexCoordLoc)
    }

    /**
     * Updates the active PBR map bitmaps. Safe to call from UI or ViewModel coroutine thread.
     */
    fun updateBitmaps(
        albedo: Bitmap?,
        normal: Bitmap?,
        roughness: Bitmap?,
        ao: Bitmap?,
        metallic: Bitmap?
    ) {
        synchronized(textureLock) {
            pendingAlbedo = albedo
            pendingNormal = normal
            pendingRoughness = roughness
            pendingAo = ao
            pendingMetallic = metallic
            hasPendingTextures = true
        }
    }

    private fun uploadPendingTexturesIfAvailable() {
        if (!hasPendingTextures) return

        var alb: Bitmap?
        var nrm: Bitmap?
        var rgh: Bitmap?
        var aoc: Bitmap?
        var met: Bitmap?

        synchronized(textureLock) {
            alb = pendingAlbedo
            nrm = pendingNormal
            rgh = pendingRoughness
            aoc = pendingAo
            met = pendingMetallic
            hasPendingTextures = false
        }

        albedoTexId = uploadOrUpdateTexture(albedoTexId, alb)
        normalTexId = uploadOrUpdateTexture(normalTexId, nrm)
        roughnessTexId = uploadOrUpdateTexture(roughnessTexId, rgh)
        aoTexId = uploadOrUpdateTexture(aoTexId, aoc)
        metallicTexId = uploadOrUpdateTexture(metallicTexId, met)
    }

    private fun uploadOrUpdateTexture(existingTexId: Int, bitmap: Bitmap?): Int {
        if (bitmap == null || bitmap.isRecycled) {
            return existingTexId
        }

        val texId = if (existingTexId > 0) {
            existingTexId
        } else {
            val ids = IntArray(1)
            GLES30.glGenTextures(1, ids, 0)
            ids[0]
        }

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texId)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_LINEAR_MIPMAP_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_LINEAR)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_S, GLES30.GL_REPEAT)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_WRAP_T, GLES30.GL_REPEAT)

        GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
        GLES30.glGenerateMipmap(GLES30.GL_TEXTURE_2D)

        return texId
    }

    private fun initShaders() {
        val vertShader = compileShader(GLES30.GL_VERTEX_SHADER, PbrShader.VERTEX_SHADER_SOURCE)
        val fragShader = compileShader(GLES30.GL_FRAGMENT_SHADER, PbrShader.FRAGMENT_SHADER_SOURCE)

        programId = GLES30.glCreateProgram()
        GLES30.glAttachShader(programId, vertShader)
        GLES30.glAttachShader(programId, fragShader)
        GLES30.glLinkProgram(programId)

        val linkStatus = IntArray(1)
        GLES30.glGetProgramiv(programId, GLES30.GL_LINK_STATUS, linkStatus, 0)
        if (linkStatus[0] == 0) {
            val info = GLES30.glGetProgramInfoLog(programId)
            Log.e(tag, "Shader program link failed: $info")
            GLES30.glDeleteProgram(programId)
            programId = 0
            return
        }

        // Get Locations
        aPositionLoc = 0
        aNormalLoc = 1
        aTangentLoc = 2
        aTexCoordLoc = 3

        uModelMatrixLoc = GLES30.glGetUniformLocation(programId, "u_ModelMatrix")
        uViewMatrixLoc = GLES30.glGetUniformLocation(programId, "u_ViewMatrix")
        uProjectionMatrixLoc = GLES30.glGetUniformLocation(programId, "u_ProjectionMatrix")
        uNormalMatrixLoc = GLES30.glGetUniformLocation(programId, "u_NormalMatrix")
        uTilingLoc = GLES30.glGetUniformLocation(programId, "u_Tiling")

        uAlbedoMapLoc = GLES30.glGetUniformLocation(programId, "u_AlbedoMap")
        uNormalMapLoc = GLES30.glGetUniformLocation(programId, "u_NormalMap")
        uRoughnessMapLoc = GLES30.glGetUniformLocation(programId, "u_RoughnessMap")
        uAoMapLoc = GLES30.glGetUniformLocation(programId, "u_AoMap")
        uMetallicMapLoc = GLES30.glGetUniformLocation(programId, "u_MetallicMap")

        uHasAlbedoLoc = GLES30.glGetUniformLocation(programId, "u_HasAlbedo")
        uHasNormalLoc = GLES30.glGetUniformLocation(programId, "u_HasNormal")
        uHasRoughnessLoc = GLES30.glGetUniformLocation(programId, "u_HasRoughness")
        uHasAoLoc = GLES30.glGetUniformLocation(programId, "u_HasAo")
        uHasMetallicLoc = GLES30.glGetUniformLocation(programId, "u_HasMetallic")

        uNormalFormatLoc = GLES30.glGetUniformLocation(programId, "u_NormalFormat")
        uNormalStrengthLoc = GLES30.glGetUniformLocation(programId, "u_NormalStrength")
        uRenderModeLoc = GLES30.glGetUniformLocation(programId, "u_RenderMode")

        uCameraPosLoc = GLES30.glGetUniformLocation(programId, "u_CameraPos")
        uLightPosLoc = GLES30.glGetUniformLocation(programId, "u_LightPos")
        uLightColorLoc = GLES30.glGetUniformLocation(programId, "u_LightColor")
        uLightIntensityLoc = GLES30.glGetUniformLocation(programId, "u_LightIntensity")
        uAmbientIntensityLoc = GLES30.glGetUniformLocation(programId, "u_AmbientIntensity")
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES30.glCreateShader(type)
        GLES30.glShaderSource(shader, source)
        GLES30.glCompileShader(shader)

        val compiled = IntArray(1)
        GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            val log = GLES30.glGetShaderInfoLog(shader)
            Log.e(tag, "Could not compile shader $type: $log")
            GLES30.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun initMeshes() {
        sphereMesh = GeometryGenerator.generateUvSphere(radius = 1.05f, stacks = 56, slices = 56)
        cubeMesh = GeometryGenerator.generateCube(size = 1.4f)
        planeMesh = GeometryGenerator.generatePlane(size = 2.0f)
        cylinderMesh = GeometryGenerator.generateCylinder(radius = 0.85f, height = 1.8f, segments = 48)
    }

    private fun initFallbackTextures() {
        // Fallback 1x1 white texture
        val whiteBuffer = ByteBuffer.allocateDirect(4).apply {
            put(255.toByte()); put(255.toByte()); put(255.toByte()); put(255.toByte())
            position(0)
        }
        val texIds = IntArray(2)
        GLES30.glGenTextures(2, texIds, 0)
        fallbackWhiteTexId = texIds[0]
        fallbackNormalTexId = texIds[1]

        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fallbackWhiteTexId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, whiteBuffer)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)

        // Fallback 1x1 flat normal (128, 128, 255)
        val normalBuffer = ByteBuffer.allocateDirect(4).apply {
            put(128.toByte()); put(128.toByte()); put(255.toByte()); put(255.toByte())
            position(0)
        }
        GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, fallbackNormalTexId)
        GLES30.glTexImage2D(GLES30.GL_TEXTURE_2D, 0, GLES30.GL_RGBA, 1, 1, 0, GLES30.GL_RGBA, GLES30.GL_UNSIGNED_BYTE, normalBuffer)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MIN_FILTER, GLES30.GL_NEAREST)
        GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAG_FILTER, GLES30.GL_NEAREST)
    }

    private fun computeNormalMatrix(modelM: FloatArray, outNormalM: FloatArray) {
        // For uniform scaling and rotation, the upper 3x3 of the model matrix is identical to the normal matrix
        outNormalM[0] = modelM[0]; outNormalM[1] = modelM[1]; outNormalM[2] = modelM[2]
        outNormalM[3] = modelM[4]; outNormalM[4] = modelM[5]; outNormalM[5] = modelM[6]
        outNormalM[6] = modelM[8]; outNormalM[7] = modelM[9]; outNormalM[8] = modelM[10]
    }
}
