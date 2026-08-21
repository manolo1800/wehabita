package com.example

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.example.data.local.WeHabitaDatabase
import com.example.data.local.entities.*
import com.example.data.repository.WeHabitaRepository
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class WeHabitaDatabaseTest {
    private lateinit var db: WeHabitaDatabase
    private lateinit var repository: WeHabitaRepository

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, WeHabitaDatabase::class.java)
            .allowMainThreadQueries()
            .build()
        repository = WeHabitaRepository(db)
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun `test user registration and home creation`() = runBlocking {
        val userId = repository.registerUser("Test User", "test@wehabita.app", "testuser", "pass123").toInt()
        val user = repository.getUserById(userId)
        assertNotNull(user)
        assertEquals("Test User", user?.nombre)
        assertTrue(user?.codigo_usuario?.startsWith("USR-") == true)

        val homeId = repository.createHome("Casa Test", "familia", userId).toInt()
        val home = repository.getHomeById(homeId)
        assertNotNull(home)
        assertEquals("Casa Test", home?.nombre)
        assertTrue(home?.codigo_invitacion?.startsWith("HOG-") == true)

        val members = repository.getMembersWithUserForHome(homeId).first()
        assertEquals(1, members.size)
        assertEquals("admin", members.first().member.rol)
    }

    @Test
    fun `test recurring task with multiple schedules generates occurrences`() = runBlocking {
        val userId = repository.registerUser("Carlos", "carlos@test.com", "carlos", "pass").toInt()
        val homeId = repository.createHome("Casa Carlos", "familia", userId).toInt()

        val taskId = repository.createTask(
            homeId = homeId,
            createdBy = userId,
            titulo = "Pasear al perro",
            descripcion = "Tres veces al día",
            tipo = "fija",
            fechaInicio = "2026-08-20",
            fechaFin = null,
            horarios = listOf(Pair("08:00", "08:30"), Pair("14:00", "14:30"), Pair("20:00", "20:30")),
            assignedUserIds = listOf(userId)
        ).toInt()

        val occurrences = repository.getOccurrencesForHomeAndDate(homeId, "2026-08-20").first()
        assertEquals(3, occurrences.size)

        // Mark first occurrence completed
        val firstOcc = occurrences.first().occurrence
        assertFalse(firstOcc.completada)

        repository.markOccurrence(firstOcc.id, true, userId, "Buen paseo")

        val updatedOccs = repository.getOccurrencesForHomeAndDate(homeId, "2026-08-20").first()
        val updatedFirst = updatedOccs.find { it.occurrence.id == firstOcc.id }
        assertNotNull(updatedFirst)
        assertTrue(updatedFirst!!.occurrence.completada)
        assertEquals("Buen paseo", updatedFirst.occurrence.notas)
        assertEquals(userId, updatedFirst.occurrence.completada_por)
    }

    @Test
    fun `test problem creation, points of view and agreement resolution`() = runBlocking {
        val u1 = repository.registerUser("Carlos", "carlos@test.com", "carlos", "pass").toInt()
        val u2 = repository.registerUser("Sofia", "sofia@test.com", "sofia", "pass").toInt()
        val homeId = repository.createHome("Casa Compartida", "pareja", u1).toInt()

        val problemId = repository.createProblem(
            homeId = homeId,
            createdBy = u1,
            titulo = "Ruido nocturno",
            descripcion = "Música alta después de las 23:00",
            involvedUserIds = listOf(u1, u2)
        ).toInt()

        repository.addPointOfView(problemId, u1, "Necesito descansar temprano para trabajar.")
        repository.addPointOfView(problemId, u2, "Entendido, usaré auriculares.")

        val povs = repository.getPointsOfViewForProblem(problemId).first()
        assertEquals(2, povs.size)

        val problem = db.problemDao().getProblemById(problemId)
        assertNotNull(problem)
        repository.updateProblemDetails(problem!!, hablado = true, solucionado = true, acuerdo = "Uso de auriculares obligatorio a partir de las 22:30")

        val updatedProblem = db.problemDao().getProblemById(problemId)
        assertTrue(updatedProblem!!.hablado)
        assertTrue(updatedProblem.solucionado)
        assertEquals("Uso de auriculares obligatorio a partir de las 22:30", updatedProblem.acuerdo)
    }

    @Test
    fun `test goal creation and postponement preserving original deadline`() = runBlocking {
        val u1 = repository.registerUser("Carlos", "carlos@test.com", "carlos", "pass").toInt()
        val homeId = repository.createHome("Hogar", "familia", u1).toInt()

        val goalId = repository.createGoal(
            homeId = homeId,
            createdBy = u1,
            titulo = "Pintar la sala",
            descripcion = "Color blanco marfil",
            tipo = "compuesto",
            fechaLimiteOriginal = "2026-09-01",
            puntos = listOf(Pair("Comprar pintura", u1), Pair("Lijar paredes", u1))
        ).toInt()

        val goal = db.goalDao().getGoalById(goalId)
        assertNotNull(goal)
        assertEquals("2026-09-01", goal?.fecha_limite_original)
        assertEquals("2026-09-01", goal?.fecha_limite_actual)

        // Postpone
        repository.postponeGoal(goal!!, "2026-09-15")

        val postponed = db.goalDao().getGoalById(goalId)
        assertEquals("2026-09-01", postponed?.fecha_limite_original) // Preserved!
        assertEquals("2026-09-15", postponed?.fecha_limite_actual)
    }
}
