package net.fabricmc.bot.conf.spec

import com.uchuhimo.konf.ConfigSpec

/**
 * A class representing the `channels` section of the configuration.
 *
 * This is used by Konf, and will not need to be accessed externally.
 */
object ChannelsSpec : ConfigSpec() {
    /** Configured alerts channel ID. **/
    val alerts by required<Long>()

    /** Configured bot-commands channel ID. **/
    val botCommands by required<Long>()

    /** Configured action-log channel ID. **/
    val actionLog by required<Long>()

    /** Configured moderator-log channel ID. **/
    val moderatorLog by required<Long>()

    /** Channels that should be ignored by the logging extension. **/
    val ignoredChannels by required<List<Long>>()

    /** Category used for rotating action logs. **/
    val actionLogCategory by required<Long>()

    /**
     * Configured player support channel ID.
     */
    val playerSupport by required<Long>()
}
