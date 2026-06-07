package com.example.notes.network

import com.example.notes.data.dto.NoteDto
import retrofit2.Response
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.POST
import retrofit2.http.PUT
import retrofit2.http.Path

interface NotesApi {

    @GET("notes")
    suspend fun getAllNotes(): Response<List<NoteDto>>

    @GET("notes/{id}")
    suspend fun getNote(@Path("id") id: Long): Response<NoteDto>

    @POST("notes")
    suspend fun createNote(@Body note: NoteDto): Response<NoteDto>

    @PUT("notes/{id}")
    suspend fun updateNote(@Path("id") id: Long, @Body note: NoteDto): Response<NoteDto>

    @DELETE("notes/{id}")
    suspend fun deleteNote(@Path("id") id: Long): Response<Unit>

    @GET("notes/sync")
    suspend fun syncNotes(): Response<List<NoteDto>>
}
