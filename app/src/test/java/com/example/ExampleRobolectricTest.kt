package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.TexForgeDatabase
import com.example.data.local.entity.ProjectEntity
import com.example.data.preferences.AppSettings
import com.example.data.preferences.EnginePreference
import com.example.data.preferences.ExportFormat
import com.example.data.preferences.NormalFormat
import com.example.data.preferences.QualityPreset
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ExampleRobolectricTest {

    private lateinit var db: TexForgeDatabase

    @Before
    fun createDb() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, TexForgeDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun closeDb() {
        db.close()
    }

    @Test
    fun `app name is TexForge`() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        val appName = context.getString(R.string.app_name)
        assertEquals("TexForge", appName)
    }

    @Test
    fun `room skeleton can insert and retrieve project`() = runBlocking {
        val projectDao = db.projectDao()
        val project = ProjectEntity(
            name = "Test Stone Tile",
            resolution = 2048
        )
        val insertedId = projectDao.insertProject(project)
        assertTrue(insertedId > 0)

        val retrieved = projectDao.getProjectById(insertedId)
        assertNotNull(retrieved)
        assertEquals("Test Stone Tile", retrieved?.name)
        assertEquals(2048, retrieved?.resolution)

        val all = projectDao.getAllProjects().first()
        assertEquals(1, all.size)
    }

    @Test
    fun `default app settings are sound`() {
        val settings = AppSettings()
        assertEquals(QualityPreset.STANDARD_2048, settings.qualityPreset)
        assertEquals(NormalFormat.OPEN_GL, settings.normalFormat)
        assertEquals(EnginePreference.LOCAL_ONLY, settings.enginePreference)
        assertEquals(16, settings.heightBitDepth)
        assertEquals(512, settings.tileSize)
        assertEquals(16, settings.aoSamples)
        assertEquals(ExportFormat.PNG, settings.exportFormat)
        assertEquals(90, settings.jpgQuality)
        assertEquals(1.0f, settings.normalScale1)
        assertEquals(0.75f, settings.normalScale2)
        assertEquals(0.5f, settings.normalScale3)
        assertEquals(0.25f, settings.normalScale4)
    }
}
