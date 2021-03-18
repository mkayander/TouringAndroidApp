package net.inqer.touringapp.hilt

import android.content.Context
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.components.ViewModelComponent
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.android.scopes.ViewModelScoped
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import net.inqer.touringapp.R
import net.inqer.touringapp.data.remote.RoutesApi
import net.inqer.touringapp.data.repository.main.DefaultMainRepository
import net.inqer.touringapp.data.repository.main.MainRepository
import net.inqer.touringapp.util.DispatcherProvider
import javax.inject.Named

@Module
@InstallIn(ViewModelComponent::class)
object MainModule {

    @ViewModelScoped
    @Provides
    @Named("String2")
    fun provideTestString2(
            @ApplicationContext context: Context,
            @Named("String1") testString1: String
    ) = "${context.getString(R.string.string_to_inject)} - $testString1"


    @ViewModelScoped
    @Provides
    fun provideMainRepository(api: RoutesApi): MainRepository = DefaultMainRepository(api)


    @ViewModelScoped
    @Provides
    fun provideDispatchers(): DispatcherProvider = object : DispatcherProvider {
        override val main: CoroutineDispatcher
            get() = Dispatchers.Main
        override val io: CoroutineDispatcher
            get() = Dispatchers.IO
        override val default: CoroutineDispatcher
            get() = Dispatchers.Main
        override val unconfined: CoroutineDispatcher
            get() = Dispatchers.Unconfined

    }
}