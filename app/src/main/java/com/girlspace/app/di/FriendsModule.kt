package com.girlspace.app.di

import com.girlspace.app.data.friends.FriendRepository
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object FriendsModule {

    // Global singleton FirebaseAuth for the app
    @Provides
    @Singleton
    fun provideFirebaseAuth(): FirebaseAuth = FirebaseAuth.getInstance()

    // Global singleton Firestore instance for the app
    @Provides
    @Singleton
    fun provideFirebaseFirestore(): FirebaseFirestore = FirebaseFirestore.getInstance()

    // FriendRepository bound for injection into FriendsViewModel
    @Provides
    @Singleton
    fun provideFriendRepository(
        auth: FirebaseAuth,
        firestore: FirebaseFirestore
    ): FriendRepository = FriendRepository(auth, firestore)
}
