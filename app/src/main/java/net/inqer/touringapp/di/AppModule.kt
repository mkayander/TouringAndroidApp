package net.inqer.touringapp.di

import android.content.Context
import android.content.SharedPreferences
import android.location.LocationManager
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import androidx.room.Room
import com.google.android.gms.location.FusedLocationProviderClient
import com.google.android.gms.location.LocationServices
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.distinctUntilChanged
import net.inqer.touringapp.R
import net.inqer.touringapp.data.local.AppDatabase
import net.inqer.touringapp.data.models.ActiveRouteDataBus
import net.inqer.touringapp.data.models.TourRoute
import net.inqer.touringapp.data.remote.RoutesApi
import net.inqer.touringapp.di.qualifiers.ActiveTourRouteLiveData
import net.inqer.touringapp.preferences.AppConfig
import net.inqer.touringapp.preferences.DefaultAppConfig
import net.inqer.touringapp.util.DispatcherProvider
import retrofit2.Converter
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import java.text.DateFormat
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {
    private const val TAG = "AppModule"

    @Singleton
    @Provides
    fun provideAppConfig(
            @ApplicationContext context: Context,
            preferences: SharedPreferences
    ): AppConfig = DefaultAppConfig(context, preferences)

    @Singleton
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

    @Singleton
    @Provides
    fun provideConverterFactory(): Converter.Factory = GsonConverterFactory.create()

    @Singleton
    @Provides
    fun provideRetrofitInstance(config: AppConfig, factory: Converter.Factory): Retrofit = Retrofit.Builder()
            .baseUrl(config.baseUrl)
            .addConverterFactory(factory)
            .build()

    @Singleton
    @Provides
    fun provideRoutesApi(retrofit: Retrofit): RoutesApi = retrofit.create(RoutesApi::class.java)

    @Singleton
    @Provides
    fun provideSharedPreferences(@ApplicationContext context: Context): SharedPreferences =
            context.getSharedPreferences(
                    context.getString(R.string.main_preference_file_key), Context.MODE_PRIVATE
            )

    @Singleton
    @Provides
    fun provideAppDatabase(@ApplicationContext context: Context): AppDatabase =
            Room.databaseBuilder(context, AppDatabase::class.java, "touring-app-database")
                    .fallbackToDestructiveMigration()
                    .build()

    @Singleton
    @Provides
    fun provideDateFormat(@ApplicationContext context: Context): DateFormat =
            android.text.format.DateFormat.getDateFormat(context)

    @Singleton
    @Provides
    @ActiveTourRouteLiveData
    fun provideActiveTourRouteLiveData(database: AppDatabase): LiveData<TourRoute?> =
            database.tourRouteDao().observeActiveRoute().distinctUntilChanged().asLiveData()

    @Provides
    fun provideFusedLocationProviderClient(@ApplicationContext context: Context): FusedLocationProviderClient =
            LocationServices.getFusedLocationProviderClient(context)

    @Singleton
    @Provides
    fun provideActiveRouteData() = ActiveRouteDataBus()


    @Singleton
    @Provides
    fun provideLocationManager(@ApplicationContext context: Context): LocationManager =
            context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
}
