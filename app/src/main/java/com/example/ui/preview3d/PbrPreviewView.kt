package com.example.ui.preview3d

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.viewinterop.AndroidView
import com.example.engine.gl.PbrGlRenderer

/**
 * Jetpack Compose wrapper for Android OpenGL ES 3.0 GLSurfaceView.
 *
 * Provides real-time touch handling:
 * - 1-finger drag: orbits camera around the 3D model (yaw & pitch)
 * - Pinch-to-zoom: adjusts camera distance
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun PbrPreviewView(
    renderer: PbrGlRenderer,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val glSurfaceView = remember {
        GLSurfaceView(context).apply {
            setEGLContextClientVersion(3)
            setRenderer(renderer)
            renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY
            preserveEGLContextOnPause = true
        }
    }

    DisposableEffect(glSurfaceView) {
        glSurfaceView.onResume()
        onDispose {
            glSurfaceView.onPause()
        }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(renderer) {
                detectTransformGestures { _, pan, zoom, _ ->
                    // Touch interaction pauses auto-rotation during drag
                    renderer.orbitYaw -= pan.x * 0.35f
                    renderer.orbitPitch = (renderer.orbitPitch - pan.y * 0.35f).coerceIn(-85f, 85f)
                    renderer.cameraDistance = (renderer.cameraDistance / zoom).coerceIn(1.4f, 6.5f)
                }
            }
    ) {
        AndroidView(
            factory = { glSurfaceView },
            modifier = Modifier.fillMaxSize()
        )
    }
}
