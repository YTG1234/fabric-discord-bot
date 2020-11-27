package net.fabricmc.bot.extensions

import com.gitlab.kordlib.common.entity.GuildFeature
import com.gitlab.kordlib.common.entity.Snowflake
import com.gitlab.kordlib.common.entity.PresenceStatus
import com.gitlab.kordlib.core.entity.channel.Category
import com.gitlab.kordlib.core.entity.channel.NewsChannel
import com.gitlab.kordlib.core.entity.channel.TextChannel
import com.gitlab.kordlib.core.entity.channel.VoiceChannel
import com.gitlab.kordlib.rest.Image
import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.converters.optionalNumber
import com.kotlindiscord.kord.extensions.commands.converters.optionalUser
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.*
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.flow.toSet
import net.fabricmc.bot.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colors
import net.fabricmc.bot.enums.Emojis
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.extensions.infractions.getMemberId
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.utils.getStatusEmoji
import net.fabricmc.bot.utils.requireBotChannel
import java.time.Instant

private const val DELETE_DELAY = 10_000L  // 10 seconds

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

            check(::defaultCheck)

            signature = "[user]"

            action {
                with(parse(::UtilsUserArgs)) {
                    runSuspended {
                        if (!message.requireBotChannel(delay = DELETE_DELAY)) {
                            return@runSuspended
                        }

                        val isModerator = topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))(event)
                        var (memberId, _) = getMemberId(user, userId?.let { Snowflake(it) })

                        if (memberId == null) {
                            memberId = message.data.authorId
                        }

                        if (memberId != message.data.authorId && !isModerator) {
                            message.deleteWithDelay(DELETE_DELAY)

                            message.respond("Only staff members may request information about other users.")
                                    .deleteWithDelay(DELETE_DELAY)
                            return@runSuspended
                        }

                        val member = config.getGuild().getMemberOrNull(memberId)

                        if (member == null) {
                            message.deleteWithDelay(DELETE_DELAY)

                            message.respond("That user doesn't appear to be on Fabricord.")
                            return@runSuspended
                        }

                        val infractions = config.db.infractionQueries.getActiveInfractionsByUser(memberId.value)
                                .executeAsList().filter { it.infraction_type != InfractionTypes.NOTE }

                        val activeInfractions = infractions.count { it.active }

                        val roles = member.roles.toList()

                        message.respond {
                            embed {
                                title = "User info: ${member.tag}"

                                color = member.getTopRole()?.color ?: Colors.BLURPLE

                                description = "**ID:** `${memberId.value}`\n" +
                                        "**Status:** ${member.getStatusEmoji()}\n"

                                if (member.nickname != null) {
                                    description += "**Nickname:** ${member.nickname}\n"
                                }

                                description += "\n" +
                                        "**Created at:** ${instantToDisplay(member.createdAt)}\n" +
                                        "**Joined at:** ${instantToDisplay(member.joinedAt)}"

                                if (infractions.isNotEmpty()) {
                                    description += "\n\n" +

                                            "**Infractions:** ${infractions.size} (${activeInfractions} active)"
                                }

                                if (roles.isNotEmpty()) {
                                    description += "\n\n" +

                                            "**Roles:** " +
                                            roles.sortedBy { it.rawPosition }
                                                    .reversed()
                                                    .joinToString(" ") { it.mention }
                                }

                                thumbnail { url = member.avatar.url }
                                timestamp = Instant.now()
                            }
                        }
                    }
                }
            }
        }

        command {
            name = "server"
            aliases = arrayOf("s", "guild", "g")

            description = "Retrieve information about the server."

            check(::defaultCheck)

            action {
                if (!message.requireBotChannel(delay = DELETE_DELAY)) {
                    return@action
                }

                val guild = config.getGuild()
                val members = guild.members.toList()

                val iconUrl = guild.getIconUrl(Image.Format.PNG)

                val emojiAway = EmojiExtension.getEmoji(Emojis.STATUS_AWAY)
                val emojiDnd = EmojiExtension.getEmoji(Emojis.STATUS_DND)
                val emojiOffline = EmojiExtension.getEmoji(Emojis.STATUS_OFFLINE)
                val emojiOnline = EmojiExtension.getEmoji(Emojis.STATUS_ONLINE)

                val statuses: MutableMap<PresenceStatus, Long> = mutableMapOf(
                        PresenceStatus.Idle to 0,
                        PresenceStatus.DoNotDisturb to 0,
                        PresenceStatus.Offline to 0,
                        PresenceStatus.Online to 0,
                )

                val presences = guild.presences.toList()

                presences.toList().forEach {
                    statuses[it.status] = statuses[it.status]!!.plus(1)
                }

                val offline = members.size - presences.size + statuses[PresenceStatus.Offline]!!

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

                val newestEmoji = guild.emojis.toList().maxByOrNull { it.id.timeStamp }
                val totalEmojis = guild.emojis.toSet().size

                message.respond {
                    embed {
                        title = guild.name
                        color = Colors.BLURPLE
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

                                    "$emojiOnline ${statuses[PresenceStatus.Online]}\n" +
                                    "$emojiAway ${statuses[PresenceStatus.Idle]}\n" +
                                    "$emojiDnd ${statuses[PresenceStatus.DoNotDisturb]}\n" +
                                    "$emojiOffline $offline"
                        }

                        field {
                            name = "Emojis"
                            inline = true

                            value = "**Total:** $totalEmojis"

                            if (newestEmoji != null) {
                                value += "\n**Latest:** ${newestEmoji.mention}"
                            }
                        }

                        field {
                            name = "Features"
                            inline = true

                            value = if (guild.features.isNotEmpty()) {
                                guild.features
                                        .filter { it !is GuildFeature.Unknown }
                                        .joinToString("\n") { "`${it.value}`" }
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

    /** @suppress **/
    @Suppress("UndocumentedPublicProperty")
    class UtilsUserArgs : Arguments() {
        val user by optionalUser("user")
        val userId by optionalNumber("userId")

    }
}
