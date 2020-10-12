package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.core.behavior.channel.createEmbed
import com.gitlab.kordlib.core.entity.User
import com.gitlab.kordlib.core.entity.channel.Category
import com.gitlab.kordlib.core.entity.channel.NewsChannel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.gitlab.kordlib.core.entity.channel.VoiceChannel
import com.gitlab.kordlib.rest.Image
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.getTopRole
import kotlinx.coroutines.flow.count
import kotlinx.coroutines.flow.toList
import net.fabricmc.bot.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.enums.Emojis
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.getMemberId
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.utils.getStatusEmoji
import net.fabricmc.bot.utils.respond
import java.time.Instant

private const val DELETE_DELAY = 10_000L  // 10 seconds

/** @suppress **/
@Suppress("UndocumentedPublicProperty")
data class UtilsUserArgs(
        val user: User? = null,
        val userId: Long? = null
)

/**
 * Extension providing useful utility commands.
 */
class UtilsExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name = "utils"

    override suspend fun setup() {
        command {
            name = "user"
            aliases = arrayOf("u")

            description = "Retrieve information about yourself (or, if you're staff, another user)."

            check(
                    ::defaultCheck,
                    ::botChannelOrTraineeModerator
            )

            signature = "[user]"

            action {
                with(parse<UtilsUserArgs>()) {
                    runSuspended {
                        val inBotChannel = inBotChannel(event)
                        val isModerator = topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))(event)

                        if (inBotChannel && !isModerator) {
                            val botCommands = config.getChannel(Channels.BOT_COMMANDS)

                            message.deleteWithDelay(DELETE_DELAY)

                            message.respond("Please use ${botCommands.mention} for this command.")
                                    .deleteWithDelay(DELETE_DELAY)

                            return@runSuspended
                        }


                        var (memberId, memberMessage) = getMemberId(user, userId)

                        if (memberId == null) {
                            memberId = message.data.authorId!!
                        }

                        if (memberId != message.data.authorId && !isModerator) {
                            message.deleteWithDelay(DELETE_DELAY)

                            message.respond("Only staff members may request information about other users.")
                                    .deleteWithDelay(DELETE_DELAY)
                            return@runSuspended
                        }

                        val member = config.getGuild().getMemberOrNull(Snowflake(memberId))

                        if (member == null) {
                            message.deleteWithDelay(DELETE_DELAY)

                            message.respond("That user doesn't appear to be on Fabricord.")
                            return@runSuspended
                        }

                        val infractions = config.db.infractionQueries.getActiveInfractionsByUser(memberId)
                                .executeAsList().filter { it.infraction_type != InfractionTypes.NOTE }

                        val activeInfractions = infractions.count { it.active }

                        message.channel.createEmbed {
                            title = "User info: ${member.tag}"

                            color = member.getTopRole()?.color ?: Colours.BLURPLE

                            description = "**ID:** `$memberId`\n" +
                                    "**Status:** ${member.getStatusEmoji()}\n\n" +

                                    "**Created at:** ${instantToDisplay(member.createdAt)}\n" +
                                    "**Joined at:** ${instantToDisplay(member.joinedAt)}\n\n" +

                                    "**Infractions:** ${infractions.size} (${activeInfractions} active)\n\n" +

                                    "**Roles:** " +
                                    member.roles.toList()
                                            .sortedBy { it.rawPosition }
                                            .reversed()
                                            .joinToString(" ") { it.mention }

                            thumbnail { url = member.avatar.url }
                            timestamp = Instant.now()
                        }
                    }
                }
            }
        }

        command {
            name = "server"
            aliases = arrayOf("s", "guild", "g")

            description = "Retrieve information about the server."

            check(
                    ::defaultCheck,
                    ::botChannelOrTraineeModerator
            )

            action {
                val inBotChannel = inBotChannel(event)
                val isModerator = topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))(event)

                if (inBotChannel && !isModerator) {
                    val botCommands = config.getChannel(Channels.BOT_COMMANDS)

                    message.deleteWithDelay(DELETE_DELAY)

                    message.respond("Please use ${botCommands.mention} for this command.")
                            .deleteWithDelay(DELETE_DELAY)

                    return@action
                }

                val guild = config.getGuild()
                val members = guild.members.toList()

                val iconUrl = guild.getIconUrl(Image.Format.PNG)

                val emojiAway = EmojiExtension.getEmoji(Emojis.STATUS_AWAY)
                val emojiDnd = EmojiExtension.getEmoji(Emojis.STATUS_DND)
                val emojiOffline = EmojiExtension.getEmoji(Emojis.STATUS_OFFLINE)
                val emojiOnline = EmojiExtension.getEmoji(Emojis.STATUS_ONLINE)

                val statuses: MutableMap<String, Long> = mutableMapOf(
                        emojiAway to 0,
                        emojiDnd to 0,
                        emojiOffline to 0,
                        emojiOnline to 0,
                )

                members.forEach {
                    statuses[it.getStatusEmoji()] = statuses[it.getStatusEmoji()]!!.plus(1)
                }

                val channels: MutableMap<String, Long> = mutableMapOf(
                        "Category" to 0,
                        "News" to 0,
                        "Text" to 0,
                        "Voice" to 0,
                )

                val guildChannels = guild.channels.toList()

                guildChannels.forEach {
                    when (it) {
                        is Category -> channels["Category"] = channels["Category"]!!.plus(1)
                        is NewsChannel -> channels["News"] = channels["News"]!!.plus(1)
                        is TextChannel -> channels["Text"] = channels["Text"]!!.plus(1)
                        is VoiceChannel -> channels["Voice"] = channels["Voice"]!!.plus(1)
                    }
                }

                message.channel.createEmbed {
                    title = guild.name
                    color = Colours.BLURPLE
                    timestamp = Instant.now()

                    description = "**Created:** ${instantToDisplay(guild.id.timeStamp)}\n" +
                            "**Owner:** ${guild.owner.mention}\n" +
                            "**Roles:** ${guild.roleIds.size}\n" +
                            "**Voice Region:** ${guild.data.region}"

                    field {
                        name = "Channels"
                        inline = true

                        value = "**Total:** ${guildChannels.size}\n\n" +

                                channels.map { "**${it.key}:** ${it.value}" }
                                        .sorted()
                                        .joinToString("\n")
                    }

                    field {
                        name = "Members"
                        inline = true

                        value = "**Total:** ${members.size}\n\n" +

                                "$emojiOnline ${statuses[emojiOnline]}\n" +
                                "$emojiAway ${statuses[emojiAway]}\n" +
                                "$emojiDnd ${statuses[emojiDnd]}\n" +
                                "$emojiOffline ${statuses[emojiOffline]}"
                    }

                    field {
                        name = "Features"
                        inline = false

                        value = if (guild.features.isNotEmpty()) {
                            guild.features.joinToString(", ") { "`${it.value}`" }
                        } else {
                            "No features."
                        }
                    }

                    if (iconUrl != null) {
                        thumbnail { url = iconUrl }
                    }
                }
            }
        }
    }

}
