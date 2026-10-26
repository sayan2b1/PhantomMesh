package com.phantom.mesh.di

import android.content.Context
import androidx.room.Room
import com.phantom.mesh.data.local.PhantomMeshDatabase
import com.phantom.mesh.data.local.dao.ChatDao
import com.phantom.mesh.data.local.dao.DropZoneDao
import com.phantom.mesh.data.local.dao.MeshQueueDao
import com.phantom.mesh.data.local.dao.MessageDao
import com.phantom.mesh.data.local.dao.UserDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * Hilt module providing the Room database and all DAO singletons.
 */
@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): PhantomMeshDatabase {
        return Room.databaseBuilder(
            context,
            PhantomMeshDatabase::class.java,
            PhantomMeshDatabase.DATABASE_NAME
        )
            .fallbackToDestructiveMigration()
            .build()
    }

    @Provides
    fun provideUserDao(db: PhantomMeshDatabase): UserDao = db.userDao()

    @Provides
    fun provideChatDao(db: PhantomMeshDatabase): ChatDao = db.chatDao()

    @Provides
    fun provideMessageDao(db: PhantomMeshDatabase): MessageDao = db.messageDao()

    @Provides
    fun provideDropZoneDao(db: PhantomMeshDatabase): DropZoneDao = db.dropZoneDao()

    @Provides
    fun provideMeshQueueDao(db: PhantomMeshDatabase): MeshQueueDao = db.meshQueueDao()
}
