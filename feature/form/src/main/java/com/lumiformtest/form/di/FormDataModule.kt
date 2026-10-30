package com.lumiformtest.form.di

import android.content.Context
import androidx.room.Room
import com.lumiformtest.form.data.local.FormDatabase
import com.lumiformtest.form.data.local.MIGRATION_1_2
import com.lumiformtest.form.data.local.FormLocalDataSource
import com.lumiformtest.form.data.local.RoomFormLocalDataSource
import com.lumiformtest.form.data.remote.FormApi
import com.lumiformtest.form.data.remote.FormRemoteDataSource
import com.lumiformtest.form.data.remote.RetrofitFormRemoteDataSource
import com.lumiformtest.form.data.repository.OfflineFormRepository
import com.lumiformtest.form.domain.repository.FormRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import java.time.Clock
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import retrofit2.Retrofit

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class FormIoDispatcher

@Qualifier
@Retention(AnnotationRetention.BINARY)
internal annotation class FormDefaultDispatcher

@Module
@InstallIn(SingletonComponent::class)
internal object FormDataModule {
    @Provides
    @Singleton
    fun database(@ApplicationContext context: Context): FormDatabase =
        Room.databaseBuilder(context, FormDatabase::class.java, "lumiform-form.db")
            .addMigrations(MIGRATION_1_2).build()

    @Provides
    fun dao(database: FormDatabase) = database.formDao()

    @Provides
    @Singleton
    fun api(retrofit: Retrofit): FormApi = retrofit.create(FormApi::class.java)

    @Provides
    fun clock(): Clock = Clock.systemUTC()

    @Provides
    @FormIoDispatcher
    fun ioDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @FormDefaultDispatcher
    fun defaultDispatcher(): CoroutineDispatcher = Dispatchers.Default
}

@Module
@InstallIn(SingletonComponent::class)
internal abstract class FormDataBindings {
    @Binds
    abstract fun remote(source: RetrofitFormRemoteDataSource): FormRemoteDataSource

    @Binds
    abstract fun local(source: RoomFormLocalDataSource): FormLocalDataSource

    @Binds
    @Singleton
    abstract fun repository(repository: OfflineFormRepository): FormRepository
}
