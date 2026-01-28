package moe.koiverse.archivetune.utils

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import com.my.kizzy.rpc.KizzyRPC
import com.my.kizzy.rpc.UserInfo
import io.mockk.*
import io.mockk.impl.annotations.MockK
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.test.*
import moe.koiverse.archivetune.utils.dataStore
import org.junit.After
import org.junit.Before
import org.junit.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

@OptIn(ExperimentalCoroutinesApi::class)
class DiscordAuthManagerTest {

    private val testDispatcher = UnconfinedTestDispatcher()
    private lateinit var mockContext: Context
    private lateinit var mockDataStore: DataStore<Preferences>
    private lateinit var discordAuthManager: DiscordAuthManager

    @Before
    fun setup() {
        Dispatchers.setMain(testDispatcher)

        // Mock DataStore
        mockDataStore = mockk(relaxed = true)
        every { mockDataStore.data } returns flowOf(
            mutablePreferencesOf()
        )
        coEvery { mockDataStore.edit(any()) } answers {
            val transform = firstArg<suspend (MutablePreferences) -> Unit>()
            val mockMutablePrefs = mockk<MutablePreferences>(relaxed = true)
            transform(mockMutablePrefs)
            mockDataStore
        }

        // Mock Context
        mockContext = mockk(relaxed = true)
        every { mockContext.dataStore } returns mockDataStore

        // Mock KizzyRPC
        mockkObject(KizzyRPC)

        discordAuthManager = DiscordAuthManager(mockContext)
    }

    @After
    fun tearDown() {
        Dispatchers.resetMain()
        unmockkAll()
    }

    @Test
    fun `validateToken returns success for valid token`() = runTest {
        // Arrange
        val validToken = "valid_token_12345"
        val userInfo = UserInfo("testuser", "Test User")
        coEvery { KizzyRPC.getUserInfo(validToken) } returns Result.success(userInfo)

        // Act
        val result = discordAuthManager.validateToken(validToken)

        // Assert
        assertTrue(result.isSuccess)
        assertEquals("testuser", result.getOrNull()?.username)
        assertEquals("Test User", result.getOrNull()?.name)
        coVerify(exactly = 1) { KizzyRPC.getUserInfo(validToken) }
    }

    @Test
    fun `validateToken returns failure for empty token`() = runTest {
        // Act
        val result = discordAuthManager.validateToken("")

        // Assert
        assertTrue(result.isFailure)
        assertEquals("Token is empty", result.exceptionOrNull()?.message)
    }

    @Test
    fun `validateToken returns failure for blank token`() = runTest {
        // Act
        val result = discordAuthManager.validateToken("   ")

        // Assert
        assertTrue(result.isFailure)
    }

    @Test
    fun `validateToken returns failure when KizzyRPC throws error`() = runTest {
        // Arrange
        val invalidToken = "invalid_token"
        val exception = Exception("401 Unauthorized")
        coEvery { KizzyRPC.getUserInfo(invalidToken) } returns Result.failure(exception)

        // Act
        val result = discordAuthManager.validateToken(invalidToken)

        // Assert
        assertTrue(result.isFailure)
        coVerify(exactly = 1) { KizzyRPC.getUserInfo(invalidToken) }
    }

    @Test
    fun `saveToken stores token and user info`() = runTest {
        // Arrange
        val token = "test_token_123"
        val userInfo = UserInfo("username", "Display Name")

        // Act
        discordAuthManager.saveToken(token, userInfo)

        // Assert
        coVerify(exactly = 1) { mockDataStore.edit(any()) }
    }

    @Test
    fun `clearToken removes all discord preferences`() = runTest {
        // Arrange
        val mockMutablePrefs = mockk<MutablePreferences>(relaxed = true)

        // Act
        discordAuthManager.clearToken()

        // Assert
        coVerify(exactly = 1) {
            mockDataStore.edit(any())
        }
    }

    @Test
    fun `getToken returns current token from preferences`() = runTest {
        // Arrange
        val expectedToken = "stored_token"
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns expectedToken
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val token = discordAuthManager.getToken()

        // Assert
        assertEquals(expectedToken, token)
    }

    @Test
    fun `getToken returns null when no token stored`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns null
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val token = discordAuthManager.getToken()

        // Assert
        assertEquals(null, token)
    }

    @Test
    fun `isTokenValid returns true when token is valid`() = runTest {
        // Arrange
        val token = "valid_token"
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns token
        every { mockDataStore.data } returns flowOf(mockPrefs)
        coEvery { KizzyRPC.getUserInfo(token) } returns Result.success(UserInfo("user", "User"))

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isValid = discordAuthManager.isTokenValid()

        // Assert
        assertTrue(isValid)
    }

    @Test
    fun `isTokenValid returns false when token is blank`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns ""
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isValid = discordAuthManager.isTokenValid()

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `isTokenValid returns false when token is null`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns null
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isValid = discordAuthManager.isTokenValid()

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `isTokenValid returns false when KizzyRPC fails`() = runTest {
        // Arrange
        val token = "invalid_token"
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns token
        every { mockDataStore.data } returns flowOf(mockPrefs)
        coEvery { KizzyRPC.getUserInfo(token) } returns Result.failure(Exception("Unauthorized"))

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isValid = discordAuthManager.isTokenValid()

        // Assert
        assertFalse(isValid)
    }

    @Test
    fun `getUserInfo returns username and name when both exist`() = runTest {
        // Arrange
        val username = "testuser"
        val name = "Test User"
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every {
            mockPrefs[any<Preferences.Key<String>>()]
        } returnsMany listOf(username, name)
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val userInfo = discordAuthManager.getUserInfo()

        // Assert
        assertEquals(username to name, userInfo)
    }

    @Test
    fun `getUserInfo returns null when username is null`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every {
            mockPrefs[any<Preferences.Key<String>>()]
        } returns null
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val userInfo = discordAuthManager.getUserInfo()

        // Assert
        assertEquals(null, userInfo)
    }

    @Test
    fun `isLoggedIn returns true when token exists`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns "some_token"
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isLoggedIn = discordAuthManager.isLoggedIn()

        // Assert
        assertTrue(isLoggedIn)
    }

    @Test
    fun `isLoggedIn returns false when token is null`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns null
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isLoggedIn = discordAuthManager.isLoggedIn()

        // Assert
        assertFalse(isLoggedIn)
    }

    @Test
    fun `isLoggedIn returns false when token is empty`() = runTest {
        // Arrange
        val mockPrefs = mockk<Preferences>(relaxed = true)
        every { mockPrefs[any<Preferences.Key<String>>()] } returns ""
        every { mockDataStore.data } returns flowOf(mockPrefs)

        // Recreate manager to pick up new mock
        discordAuthManager = DiscordAuthManager(mockContext)

        // Act
        val isLoggedIn = discordAuthManager.isLoggedIn()

        // Assert
        assertFalse(isLoggedIn)
    }
}
