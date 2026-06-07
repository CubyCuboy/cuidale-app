package com.example.cuidaleapp.data

import android.content.Context
import android.content.SharedPreferences
import com.example.cuidaleapp.models.UserData
import com.google.firebase.Timestamp
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test
import org.mockito.ArgumentMatchers.anyBoolean
import org.mockito.ArgumentMatchers.anyInt
import org.mockito.ArgumentMatchers.anyLong
import org.mockito.ArgumentMatchers.anyString
import org.mockito.Mock
import org.mockito.Mockito.`when`
import org.mockito.Mockito.verify
import org.mockito.MockitoAnnotations
import org.mockito.kotlin.whenever

class UserSessionTest {

    @Mock
    private lateinit var context: Context

    @Mock
    private lateinit var sharedPreferences: SharedPreferences

    @Mock
    private lateinit var editor: SharedPreferences.Editor

    private lateinit var userSession: UserSession

    @Before
    fun setup() {
        MockitoAnnotations.openMocks(this)
        whenever(context.getSharedPreferences(anyString(), anyInt())).thenReturn(sharedPreferences)
        whenever(sharedPreferences.edit()).thenReturn(editor)
        whenever(editor.putString(anyString(), anyString())).thenReturn(editor)
        whenever(editor.putBoolean(anyString(), anyBoolean())).thenReturn(editor)
        whenever(editor.putLong(anyString(), anyLong())).thenReturn(editor)
        
        userSession = UserSession(context)
    }

    @Test
    fun `test saveUser and getUser with empty fotoUrl`() {
        val user = UserData(
            uid = "123",
            nombre = "John",
            fotoUrl = ""
        )

        // Mock getting values from SharedPreferences
        whenever(sharedPreferences.getString("user_uid", "")).thenReturn("123")
        whenever(sharedPreferences.getString("user_nombre", "")).thenReturn("John")
        whenever(sharedPreferences.getString("user_fotoUrl", "")).thenReturn("")

        userSession.saveUser(user)

        // Verify that putString was called with empty string for fotoUrl
        verify(editor).putString("user_fotoUrl", "")

        val retrievedUser = userSession.getUser()
        assertEquals("", retrievedUser.fotoUrl)
    }

    @Test
    fun `test saveUser and getUser with populated fotoUrl`() {
        val url = "https://firebasestorage.googleapis.com/v0/b/cuidale.appspot.com/o/profiles%2F123%2Fpic.jpg"
        val user = UserData(
            uid = "123",
            nombre = "John",
            fotoUrl = url
        )

        whenever(sharedPreferences.getString("user_uid", "")).thenReturn("123")
        whenever(sharedPreferences.getString("user_nombre", "")).thenReturn("John")
        whenever(sharedPreferences.getString("user_fotoUrl", "")).thenReturn(url)

        userSession.saveUser(user)

        verify(editor).putString("user_fotoUrl", url)

        val retrievedUser = userSession.getUser()
        assertEquals(url, retrievedUser.fotoUrl)
    }
}
