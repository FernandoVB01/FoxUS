package com.foxus.app.data.remote

import kotlinx.serialization.ExperimentalSerializationApi
import kotlinx.serialization.json.Json
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import retrofit2.http.Body
import retrofit2.http.DELETE
import retrofit2.http.GET
import retrofit2.http.PATCH
import retrofit2.http.POST
import retrofit2.http.Path
import java.util.concurrent.TimeUnit

interface FoxusApiService {
    @GET("api/v1/health")
    suspend fun getHealth(): HealthResponseDto

    @GET("api/v1/tasks")
    suspend fun listTasks(): List<TaskDto>

    @POST("api/v1/tasks")
    suspend fun createTask(@Body payload: TaskCreateRequestDto): TaskDto

    @PATCH("api/v1/tasks/{taskId}")
    suspend fun updateTask(
        @Path("taskId") taskId: String,
        @Body payload: TaskUpdateRequestDto
    ): TaskDto

    @POST("api/v1/agents/generate-mission")
    suspend fun generateMission(@Body payload: GenerateMissionRequestDto): GenerateMissionResponseDto

    @POST("api/v1/agents/evaluate-pressure")
    suspend fun evaluatePressure(@Body payload: PressureEvaluationRequestDto): PressureEvaluationResponseDto

    @GET("api/v1/agents/traces/{traceId}")
    suspend fun getTraceTimeline(@Path("traceId") traceId: String): AgentTraceTimelineDto

    @POST("api/v1/sessions/start")
    suspend fun startSession(@Body payload: SessionStartRequestDto): SessionActionResponseDto

    @POST("api/v1/sessions/{sessionId}/complete")
    suspend fun completeSession(
        @Path("sessionId") sessionId: String,
        @Body payload: SessionCompleteRequestDto
    ): SessionActionResponseDto

    @POST("api/v1/sessions/{sessionId}/verify-evidence")
    suspend fun verifySessionEvidence(
        @Path("sessionId") sessionId: String,
        @Body payload: SessionEvidenceVerifyRequestDto
    ): SessionEvidenceVerifyResponseDto

    @POST("api/v1/sessions/{sessionId}/stop")
    suspend fun stopSession(
        @Path("sessionId") sessionId: String,
        @Body payload: SessionStopRequestDto
    ): SessionActionResponseDto

    @POST("api/v1/sessions/hooks/{hookId}/dismiss")
    suspend fun dismissHook(
        @Path("hookId") hookId: String,
        @Body payload: HookDismissRequestDto
    ): HookDismissResponseDto

    @GET("api/v1/sessions/history")
    suspend fun getSessionsHistory(): List<SessionDto>

    @GET("api/v1/rewards/profile")
    suspend fun getRewardProfile(): RewardProfileDto

    @GET("api/v1/rewards/history")
    suspend fun getRewardHistory(): List<RewardTransactionDto>

    @GET("api/v1/preferences")
    suspend fun getPreferences(): UserPreferenceDto

    @PATCH("api/v1/preferences")
    suspend fun updatePreferences(@Body payload: UserPreferenceUpdateDto): UserPreferenceDto

    @GET("api/v1/integrations/google/status")
    suspend fun getGoogleStatus(): GoogleStatusResponseDto

    @GET("api/v1/integrations/google/auth-url")
    suspend fun getGoogleAuthUrl(): GoogleAuthUrlResponseDto

    @POST("api/v1/integrations/google/connect-token")
    suspend fun connectGoogleToken(@Body payload: GoogleConnectTokenRequestDto): GoogleSyncResponseDto

    @POST("api/v1/integrations/google/connect-ical")
    suspend fun connectGoogleIcal(@Body payload: GoogleConnectIcalRequestDto): GoogleSyncResponseDto

    @POST("api/v1/integrations/google/sync")
    suspend fun syncGoogleNow(): GoogleSyncResponseDto

    @DELETE("api/v1/integrations/google")
    suspend fun disconnectGoogle(): Map<String, String>

    @GET("api/v1/devices")
    suspend fun listDevices(): List<DeviceDto>

    @POST("api/v1/devices/register")
    suspend fun registerDevice(@Body payload: DeviceRegisterRequestDto): DeviceDto

    @GET("api/v1/devices/{deviceId}/commands/next")
    suspend fun getNextDeviceCommand(@Path("deviceId") deviceId: String): NextDeviceCommandResponseDto

    @POST("api/v1/devices/{deviceId}/events")
    suspend fun sendDeviceEvent(
        @Path("deviceId") deviceId: String,
        @Body payload: DeviceEventRequestDto
    ): DeviceEventResponseDto
}

class DynamicEndpointConfig(
    @Volatile var baseUrl: String = "http://10.0.2.2:8000/",
    @Volatile var demoAccessToken: String = "",
    @Volatile var deviceSharedToken: String = ""
)

object FoxusNetworkFactory {
    @OptIn(ExperimentalSerializationApi::class)
    val json: Json = Json {
        ignoreUnknownKeys = true
        coerceInputValues = true
        encodeDefaults = true
        explicitNulls = false
    }

    fun createApiService(config: DynamicEndpointConfig): FoxusApiService {
        val dynamicInterceptor = Interceptor { chain ->
            val original = chain.request()
            val targetBase = config.baseUrl.trim().let {
                if (it.endsWith("/")) it else "$it/"
            }.toHttpUrlOrNull()

            val rewrittenUrl = if (targetBase != null) {
                original.url.newBuilder()
                    .scheme(targetBase.scheme)
                    .host(targetBase.host)
                    .port(targetBase.port)
                    .build()
            } else {
                original.url
            }

            val reqBuilder = original.newBuilder().url(rewrittenUrl)
            val demoTok = config.demoAccessToken.trim()
            if (demoTok.isNotEmpty()) {
                reqBuilder.header("X-Demo-Token", demoTok)
            }
            val devTok = config.deviceSharedToken.trim()
            if (devTok.isNotEmpty()) {
                reqBuilder.header("X-Device-Token", devTok)
            }
            chain.proceed(reqBuilder.build())
        }

        val logger = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
            redactHeader("Authorization")
            redactHeader("X-Demo-Token")
            redactHeader("X-Device-Token")
        }

        val okHttp = OkHttpClient.Builder()
            .addInterceptor(dynamicInterceptor)
            .addInterceptor(logger)
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(60, TimeUnit.SECONDS)
            .writeTimeout(20, TimeUnit.SECONDS)
            .build()

        return Retrofit.Builder()
            .baseUrl("http://10.0.2.2:8000/")
            .client(okHttp)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(FoxusApiService::class.java)
    }
}
