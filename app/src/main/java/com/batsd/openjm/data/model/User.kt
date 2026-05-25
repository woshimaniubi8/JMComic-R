package com.batsd.openjm.data.model

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonPrimitive

@Serializable
data class User(
    @SerialName("uid") val uid: String = "",
    @SerialName("name") val userName: String = "",
    @SerialName("title") val title: String = "",
    @SerialName("level") val level: String = "",
    @SerialName("level_name") val levelName: String = "",
    val exp: JsonElement? = null,
    @SerialName("next_exp") val nextExp: JsonElement? = null,
    val expPercent: Double = 0.0,
    @SerialName("favorites") val favorites: Int = 0,
    @SerialName("can_favorites") val canFavorites: Int = 0,
    @SerialName("coin") val coin: Int = 0,
    @SerialName("gender") val gender: String = "",
    val badges: List<String> = emptyList(),
    val isLogin: Boolean = false,
    val cookie: Map<String, String> = emptyMap(),
    val photo: String = ""
) {
    val imgUrl: String get() = if (photo.isNotEmpty()) "media/users/$photo"
        else if (uid.isNotEmpty()) "/media/users/$uid.jpg" else ""

    val name: String get() = userName

    val expStr: String get() = when (exp) {
        is JsonPrimitive -> exp.content
        else -> ""
    }
}
