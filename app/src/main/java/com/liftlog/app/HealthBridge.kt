package com.liftlog.app

import android.content.Context
import android.content.Intent
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.PermissionController
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import org.json.JSONArray
import org.json.JSONObject
import java.time.Instant
import java.time.temporal.ChronoUnit

/** Reads weight and body fat (Body tab) and nutrition (Diet) from Android Health Connect. */
object HealthBridge {
    fun interface Callback {
        fun done(json: String)
    }

    private val WEIGHT = HealthPermission.getReadPermission(WeightRecord::class)
    private val FAT = HealthPermission.getReadPermission(BodyFatRecord::class)

    private val NUTRITION = HealthPermission.getReadPermission(NutritionRecord::class)

    @JvmField
    val PERMISSIONS: Set<String> = setOf(WEIGHT, FAT)

    @JvmField
    val NUTRITION_PERMISSIONS: Set<String> = setOf(NUTRITION)

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    @JvmStatic
    fun status(context: Context): String =
        when (HealthConnectClient.getSdkStatus(context)) {
            HealthConnectClient.SDK_AVAILABLE -> "available"
            HealthConnectClient.SDK_UNAVAILABLE_PROVIDER_UPDATE_REQUIRED -> "update_required"
            else -> "unavailable"
        }

    @JvmStatic
    fun permissionIntent(context: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(context, PERMISSIONS)

    @JvmStatic
    fun nutritionPermissionIntent(context: Context): Intent =
        PermissionController.createRequestPermissionResultContract().createIntent(context, NUTRITION_PERMISSIONS)

    /** Every meal (or daily total) other apps wrote in the last [days] days; the page adds them up per day. */
    @JvmStatic
    fun readNutrition(context: Context, days: Int, callback: Callback) {
        val app = context.applicationContext
        scope.launch {
            val out = JSONObject()
            try {
                val st = status(app)
                if (st != "available") {
                    out.put("status", st)
                } else {
                    val client = HealthConnectClient.getOrCreate(app)
                    if (!client.permissionController.getGrantedPermissions().contains(NUTRITION)) {
                        out.put("status", "no_permission")
                    } else {
                        val range = TimeRangeFilter.after(Instant.now().minus(days.toLong(), ChronoUnit.DAYS))
                        val items = JSONArray()
                        var token: String? = null
                        do {
                            val resp = client.readRecords(ReadRecordsRequest(NutritionRecord::class, range, pageToken = token))
                            for (r in resp.records) {
                                val o = JSONObject()
                                    .put("t", r.startTime.toEpochMilli())
                                    .put("src", r.metadata.dataOrigin.packageName)
                                r.startZoneOffset?.let { o.put("z", it.totalSeconds) }
                                r.energy?.let { o.put("kcal", it.inKilocalories) }
                                r.protein?.let { o.put("p", it.inGrams) }
                                r.totalCarbohydrate?.let { o.put("c", it.inGrams) }
                                r.totalFat?.let { o.put("f", it.inGrams) }
                                items.put(o)
                            }
                            token = resp.pageToken
                        } while (token != null)
                        out.put("status", "ok").put("items", items)
                    }
                }
            } catch (e: Exception) {
                out.put("status", "error").put("message", e.message ?: e.javaClass.simpleName)
            }
            callback.done(out.toString())
        }
    }

    @JvmStatic
    fun read(context: Context, days: Int, callback: Callback) {
        val app = context.applicationContext
        scope.launch {
            val out = JSONObject()
            try {
                val st = status(app)
                if (st != "available") {
                    out.put("status", st)
                } else {
                    val client = HealthConnectClient.getOrCreate(app)
                    val granted = client.permissionController.getGrantedPermissions()
                    if (!granted.contains(WEIGHT)) {
                        out.put("status", "no_permission")
                    } else {
                        val range = TimeRangeFilter.after(Instant.now().minus(days.toLong(), ChronoUnit.DAYS))
                        val weights = JSONArray()
                        var token: String? = null
                        do {
                            val resp = client.readRecords(ReadRecordsRequest(WeightRecord::class, range, pageToken = token))
                            for (r in resp.records) {
                                weights.put(
                                    JSONObject()
                                        .put("t", r.time.toEpochMilli())
                                        .put("kg", r.weight.inKilograms)
                                        .put("src", r.metadata.dataOrigin.packageName)
                                )
                            }
                            token = resp.pageToken
                        } while (token != null)
                        val fats = JSONArray()
                        if (granted.contains(FAT)) {
                            token = null
                            do {
                                val resp = client.readRecords(ReadRecordsRequest(BodyFatRecord::class, range, pageToken = token))
                                for (r in resp.records) {
                                    fats.put(
                                        JSONObject()
                                            .put("t", r.time.toEpochMilli())
                                            .put("pct", r.percentage.value)
                                            .put("src", r.metadata.dataOrigin.packageName)
                                    )
                                }
                                token = resp.pageToken
                            } while (token != null)
                        }
                        out.put("status", "ok").put("weights", weights).put("fat", fats)
                    }
                }
            } catch (e: Exception) {
                out.put("status", "error").put("message", e.message ?: e.javaClass.simpleName)
            }
            callback.done(out.toString())
        }
    }
}
