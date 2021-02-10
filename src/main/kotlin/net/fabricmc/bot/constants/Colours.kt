package net.fabricmc.bot.constants

import dev.kord.common.Color

/**
 * Constant values for colours used around the bot.
 */
object Colours {
    /** @suppress **/
    val BLURPLE = Color(red = 0x72, green = 0x89, blue = 0xDA)

    /** @suppress **/
    val FABRIC = Color(red = 0xDB, green = 0xD0, blue = 0xB4)

    /** @suppress **/
    val NEGATIVE = Color(red = 0xe7, green = 0x4c, blue = 0x3c)

    /** @suppress **/
    val POSITIVE = Color(red = 0x2e, green = 0xcc, blue = 0x71)

    /**
     * Given a string name, return the corresponding colour.
     *
     * @return A [Color] object, or null if the name doesn't match anything.
     */
    fun fromName(name: String): Color? = when (name.toLowerCase()) {
        "blurple" -> BLURPLE
        "fabric" -> FABRIC
        "negative" -> NEGATIVE
        "positive" -> POSITIVE

        else -> null
    }
}
