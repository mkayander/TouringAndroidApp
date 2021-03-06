package net.inqer.touringapp.data.remote

import net.inqer.touringapp.data.models.TourRoute
import net.inqer.touringapp.data.models.response.TourRouteBrief
import net.inqer.touringapp.data.models.response.TourRouteResponse
import retrofit2.Response
import retrofit2.http.GET
import retrofit2.http.Path

interface RoutesApi {
    @GET("routes/{route_id}/")
    suspend fun fetchRoute(@Path("route_id") routeId: Long): Response<TourRouteResponse>

    @GET("routes/")
    suspend fun fetchRoutesBrief(): Response<List<TourRouteBrief>>
}