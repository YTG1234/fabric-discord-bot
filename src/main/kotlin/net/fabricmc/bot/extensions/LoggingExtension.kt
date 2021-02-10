package net.fabricmc.bot.extensions

import com.kotlindiscord.kord.extensions.ExtensibleBot
import com.kotlindiscord.kord.extensions.checks.inGuild
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.createdAt
import com.kotlindiscord.kord.extensions.utils.deltas.MemberDelta
import com.kotlindiscord.kord.extensions.utils.deltas.UserDelta
import com.kotlindiscord.kord.extensions.utils.getUrl
import dev.kord.core.event.Event
import dev.kord.core.event.channel.CategoryCreateEvent
import dev.kord.core.event.channel.CategoryDeleteEvent
import dev.kord.core.event.channel.CategoryUpdateEvent
import dev.kord.core.event.channel.ChannelPinsUpdateEvent
import dev.kord.core.event.channel.DMChannelCreateEvent
import dev.kord.core.event.channel.DMChannelDeleteEvent
import dev.kord.core.event.channel.DMChannelUpdateEvent
import dev.kord.core.event.channel.NewsChannelCreateEvent
import dev.kord.core.event.channel.NewsChannelDeleteEvent
import dev.kord.core.event.channel.NewsChannelUpdateEvent
import dev.kord.core.event.channel.StoreChannelCreateEvent
import dev.kord.core.event.channel.StoreChannelDeleteEvent
import dev.kord.core.event.channel.StoreChannelUpdateEvent
import dev.kord.core.event.channel.TextChannelCreateEvent
import dev.kord.core.event.channel.TextChannelDeleteEvent
import dev.kord.core.event.channel.TextChannelUpdateEvent
import dev.kord.core.event.channel.TypingStartEvent
import dev.kord.core.event.channel.VoiceChannelCreateEvent
import dev.kord.core.event.channel.VoiceChannelDeleteEvent
import dev.kord.core.event.channel.VoiceChannelUpdateEvent
import dev.kord.core.event.gateway.ConnectEvent
import dev.kord.core.event.gateway.DisconnectEvent
import dev.kord.core.event.gateway.GatewayEvent
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.gateway.ResumedEvent
import dev.kord.core.event.guild.BanAddEvent
import dev.kord.core.event.guild.BanRemoveEvent
import dev.kord.core.event.guild.EmojisUpdateEvent
import dev.kord.core.event.guild.GuildCreateEvent
import dev.kord.core.event.guild.GuildDeleteEvent
import dev.kord.core.event.guild.GuildUpdateEvent
import dev.kord.core.event.guild.IntegrationsUpdateEvent
import dev.kord.core.event.guild.InviteCreateEvent
import dev.kord.core.event.guild.InviteDeleteEvent
import dev.kord.core.event.guild.MemberJoinEvent
import dev.kord.core.event.guild.MemberLeaveEvent
import dev.kord.core.event.guild.MemberUpdateEvent
import dev.kord.core.event.guild.MembersChunkEvent
import dev.kord.core.event.guild.VoiceServerUpdateEvent
import dev.kord.core.event.guild.WebhookUpdateEvent
import dev.kord.core.event.message.MessageBulkDeleteEvent
import dev.kord.core.event.message.MessageCreateEvent
import dev.kord.core.event.message.MessageDeleteEvent
import dev.kord.core.event.message.MessageUpdateEvent
import dev.kord.core.event.message.ReactionAddEvent
import dev.kord.core.event.message.ReactionRemoveAllEvent
import dev.kord.core.event.message.ReactionRemoveEmojiEvent
import dev.kord.core.event.message.ReactionRemoveEvent
import dev.kord.core.event.role.RoleCreateEvent
import dev.kord.core.event.role.RoleDeleteEvent
import dev.kord.core.event.role.RoleUpdateEvent
import dev.kord.core.event.user.PresenceUpdateEvent
import dev.kord.core.event.user.UserUpdateEvent
import dev.kord.core.event.user.VoiceStateUpdateEvent
import kotlinx.coroutines.flow.toSet
import mu.KotlinLogging
import net.fabricmc.bot.*
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.enums.Channels
import net.fabricmc.bot.extensions.infractions.instantToDisplay
import net.fabricmc.bot.utils.actionLog
import net.fabricmc.bot.utils.modLog
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.*

private val timeFormatter = DateTimeFormatter
    .ofPattern("dd/MM/yyyy HH:mm:ss '(UTC)'")
    .withLocale(Locale.UK)
    .withZone(ZoneId.of("UTC"))

private val logger = KotlinLogging.logger {}

/**
 * Event logging extension.
 *
 * This extension is in charge of relaying events to the action log and moderator log
 * channels. No advanced filtering or alerting is done here, we're just logging
 * events.
 */
class LoggingExtension(bot: ExtensibleBot) : Extension(bot) {
    override val name: String = "logging"

    override suspend fun setup() {
        event<Event> {
            check(
                inGuild(config.getGuild()),
                ::isNotBot,
                ::isNotIgnoredChannel
            )

            action {
                event.also {
                    when (it) {
                        is BanAddEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "User banned"

                            field { name = "Username"; value = it.user.username; inline = true }
                            field { name = "Discriminator"; value = it.user.discriminator; inline = true }

                            footer { text = it.user.id.value.toString() }
                            thumbnail { url = it.user.avatar.url }
                        }

                        is BanRemoveEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "User unbanned"

                            field { name = "Username"; value = it.user.username; inline = true }
                            field { name = "Discriminator"; value = it.user.discriminator; inline = true }

                            footer { text = it.user.id.value.toString() }
                            thumbnail { url = it.user.avatar.url }
                        }

                        is CategoryCreateEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "Category created"

                            field { name = "Name"; value = it.channel.name; inline = true }
                            field { name = "Mention"; value = it.channel.mention; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is CategoryDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "Category deleted"

                            field { name = "Name"; value = it.channel.name; inline = true }
                            field { name = "Mention"; value = it.channel.mention; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is InviteCreateEvent -> actionLog {
                            color = Colours.POSITIVE
                            title = "Invite created"

                            field { name = "Channel"; value = it.channel.mention; inline = true }
                            field { name = "Code"; value = "`${it.code}`"; inline = true }
                            field { name = "Inviter"; value = it.inviter!!.mention; inline = true }
                        }

                        is InviteDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "Invite deleted"

                            field { name = "Channel"; value = it.channel.mention; inline = true }
                            field { name = "Code"; value = "`${it.code}`"; inline = true }
                        }

                        is MemberJoinEvent -> actionLog {
                            color = Colours.POSITIVE
                            title = "Member joined"

                            field { name = "Username"; value = it.member.username; inline = true }
                            field { name = "Discrim"; value = it.member.discriminator; inline = true }

                            val createdAt = timeFormatter.format(it.member.createdAt)

                            field {
                                name = "Created"

                                value = if (it.member.isNew()) {
                                    ":new: $createdAt"
                                } else {
                                    createdAt
                                }
                            }

                            footer { text = it.member.id.value.toString() }
                            thumbnail { url = it.member.avatar.url }
                        }

                        is MemberLeaveEvent -> actionLog {
                            color = Colours.NEGATIVE
                            title = "Member left"

                            field { name = "Username"; value = it.user.username; inline = true }
                            field { name = "Discrim"; value = it.user.discriminator; inline = true }

                            footer { text = it.user.id.value.toString() }
                            thumbnail { url = it.user.avatar.url }
                        }

                        is MemberUpdateEvent -> {
                            val new = it.member
                            val delta = MemberDelta.from(it.old, new)

                            if (delta?.changes?.isEmpty() == true) {
                                logger.debug { "No changes found." }
                            } else {
                                actionLog {
                                    color = Colours.BLURPLE
                                    title = "Member updated"

                                    field {
                                        name = "Username"

                                        value = if (delta?.username != null) {
                                            "**${new.username}**"
                                        } else {
                                            new.username
                                        }

                                        inline = true
                                    }

                                    field {
                                        name = "Discriminator"

                                        value = if (delta?.discriminator != null) {
                                            "**${new.discriminator}**"
                                        } else {
                                            new.discriminator
                                        }

                                        inline = true
                                    }

                                    if (delta?.avatar != null) {
                                        field {
                                            name = "Avatar"
                                            inline = true

                                            value = "[New avatar](${delta.avatar})"
                                        }
                                    }

                                    if (delta?.nickname != null) {
                                        field {
                                            name = "Nickname"
                                            inline = true

                                            value = if (new.nickname == null) {
                                                "**Removed**"
                                            } else {
                                                "**Updated:** ${new.nickname}"
                                            }
                                        }
                                    }

                                    if (delta?.boosting != null) {
                                        field {
                                            name = "Boost status"
                                            inline = true

                                            value = if (new.premiumSince == null) {
                                                "**No longer boosting**"
                                            } else {
                                                "**Boosting since**: " + timeFormatter.format(new.premiumSince)
                                            }
                                        }
                                    }

                                    if (delta?.owner != null) {
                                        field {
                                            name = "Server owner"
                                            inline = true

                                            value = if (delta.owner == true) {
                                                "**Gained server ownership**"
                                            } else {
                                                "**Lost server ownership**"
                                            }
                                        }
                                    }

                                    if (delta?.roles != null) {
                                        val oldRoles = it.old?.roles?.toSet() ?: setOf()
                                        val newRoles = new.roles.toSet()

                                        if (oldRoles != newRoles) {
                                            val added = newRoles - oldRoles
                                            val removed = oldRoles - newRoles

                                            if (added.isNotEmpty()) {
                                                field {
                                                    name = "Roles added"

                                                    value = added.joinToString(" ") { role -> role.mention }
                                                }
                                            }

                                            if (removed.isNotEmpty()) {
                                                field {
                                                    name = "Roles removed"

                                                    value = removed.joinToString(" ") { role -> role.mention }
                                                }
                                            }
                                        }
                                    }

                                    footer {
                                        text = if (delta == null) {
                                            "Not cached: ${new.id.value}"
                                        } else {
                                            new.id.value.toString()
                                        }
                                    }

                                    thumbnail { url = new.avatar.url }
                                }
                            }
                        }

                        is MessageBulkDeleteEvent -> modLog {
                            // TODO: There's a Flow<Message> we could use for something.
                            // I don't think outputting all the messages to the channel is a good idea, though.

                            color = Colours.NEGATIVE
                            title = "Bulk message delete"

                            field { name = "Channel"; value = it.channel.mention; inline = true }
                            field { name = "Count"; value = it.messageIds.size.toString(); inline = true }
                        }

                        is MessageDeleteEvent -> actionLog {
                            color = Colours.NEGATIVE
                            title = "Message deleted"

                            val message = it.message

                            if (message != null) {
                                description = message.content

                                if (message.author != null) {
                                    field { name = "Author"; value = message.author!!.mention; inline = true }
                                } else {
                                    field { name = "Author"; value = "Unknown Author"; inline = true }
                                }

                                field { name = "Channel"; value = it.channel.mention; inline = true }
                                field { name = "Created"; value = instantToDisplay(it.messageId.timeStamp)!! }

                                field {
                                    name = "Attachments"; value = message.attachments.size.toString(); inline = true
                                }
                                field { name = "Embeds"; value = message.embeds.size.toString(); inline = true }

                                field {
                                    inline = true

                                    name = "Reactions"
                                    value = message.reactions.sumBy { reaction -> reaction.count }.toString()
                                }
                            } else {
                                description = "_Message was not cached, so information about it is unavailable._"

                                field { name = "Channel"; value = it.channel.mention }
                                field { name = "Created"; value = instantToDisplay(it.messageId.timeStamp)!! }
                            }

                            footer { text = it.messageId.value.toString() }
                        }

                        is MessageUpdateEvent -> if (it.getMessage().author != null) {
                            actionLog {
                                color = Colours.BLURPLE
                                title = "Message edited"

                                val old = it.old
                                val new = it.getMessage()

                                field { name = "Author"; value = new.author!!.mention; inline = true }
                                field { name = "Channel"; value = new.channel.mention; inline = true }

                                if (new.editedTimestamp != null) {
                                    field {
                                        inline = true

                                        name = "Edited at"
                                        value = timeFormatter.format(new.editedTimestamp!!)
                                    }
                                }

                                field {
                                    name = "Attachments"; value = new.attachments.size.toString(); inline = true
                                }
                                field { name = "Embeds"; value = new.embeds.size.toString(); inline = true }

                                field {
                                    inline = true

                                    name = "Reactions"
                                    value = new.reactions.sumBy { reaction -> reaction.count }.toString()
                                }

                                field { name = "URL"; value = new.getUrl() }

                                description = when {
                                    old == null -> """
                                _Message was not cached, so some information about it is unavailable._
                                
                                **__New message content__**

                                ${new.content}
                            """.trimIndent()

                                    old.content != new.content -> """
                                **__Old message content__**

                                ${old.content}
                            """.trimIndent()

                                    else -> "**__Message content not edited__**"
                                }

                                footer { text = it.messageId.value.toString() }
                            }
                        }

                        is NewsChannelCreateEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "News channel created"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Mention"; value = it.channel.mention; inline = true }
                            field { name = "Name"; value = "#${it.channel.name}"; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is NewsChannelDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "News channel deleted"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Channel"; value = "#${it.channel.name}"; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is ReactionRemoveAllEvent -> if (it.getMessage().author != null) {
                            modLog {
                                color = Colours.NEGATIVE
                                title = "All reactions removed"

                                val message = it.getMessage()

                                field { name = "Author"; value = message.author!!.mention; inline = true }
                                field { name = "Channel"; value = message.channel.mention; inline = true }

                                field { name = "Message"; value = message.getUrl() }

                                footer { text = it.messageId.value.toString() }
                            }
                        }

                        is ReactionRemoveEmojiEvent -> if (it.getMessage().author != null) {
                            modLog {
                                color = Colours.NEGATIVE
                                title = "All reactions removed"

                                val message = it.getMessage()

                                field { name = "Author"; value = message.author!!.mention; inline = true }
                                field { name = "Channel"; value = message.channel.mention; inline = true }
                                field { name = "Emoji"; value = it.emoji.mention; inline = true }

                                field { name = "Message"; value = message.getUrl() }

                                footer { text = it.messageId.value.toString() }
                            }
                        }

                        is RoleCreateEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "Role created"

                            field { name = "Name"; value = it.role.name; inline = true }

                            footer { text = it.role.id.value.toString() }
                        }

                        is RoleDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "Role deleted"

                            val role = it.role

                            if (role == null) {
                                description = "_Role was not cached, so information about it is unavailable._"
                            } else {
                                field { name = "Name"; value = role.name; inline = true }
                            }

                            footer { text = it.roleId.value.toString() }
                        }

                        is StoreChannelCreateEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "Store channel created"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Mention"; value = it.channel.mention; inline = true }
                            field { name = "Name"; value = "#${it.channel.name}"; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is StoreChannelDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "Store channel deleted"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Channel"; value = "#${it.channel.name}"; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is TextChannelCreateEvent -> {
                            val category = it.channel.category

                            if (
                                category == null ||
                                category.id.value != config.getChannel(Channels.ACTION_LOG_CATEGORY).id.value
                            ) {
                                modLog {
                                    color = Colours.POSITIVE
                                    title = "Text channel created"

                                    if (category != null) {
                                        field { name = "Category"; value = category.asChannel().name; inline = true }
                                    }

                                    field { name = "Mention"; value = it.channel.mention; inline = true }
                                    field { name = "Name"; value = "#${it.channel.name}"; inline = true }

                                    footer { text = it.channel.id.value.toString() }
                                }
                            }
                        }

                        is TextChannelDeleteEvent -> {
                            val category = it.channel.category

                            if (
                                category == null ||
                                category.id.value != config.getChannel(Channels.ACTION_LOG_CATEGORY).id.value
                            ) {
                                modLog {
                                    color = Colours.NEGATIVE
                                    title = "Text channel deleted"

                                    if (category != null) {
                                        field { name = "Category"; value = category.asChannel().name; inline = true }
                                    }

                                    field { name = "Channel"; value = "#${it.channel.name}"; inline = true }

                                    footer { text = it.channel.id.value.toString() }
                                }
                            }
                        }

                        is VoiceChannelCreateEvent -> modLog {
                            color = Colours.POSITIVE
                            title = "Voice channel created"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Mention"; value = it.channel.mention; inline = true }
                            field { name = "Name"; value = ""; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        is VoiceChannelDeleteEvent -> modLog {
                            color = Colours.NEGATIVE
                            title = "Voice channel deleted"

                            val category = it.channel.category

                            if (category != null) {
                                field { name = "Category"; value = category.asChannel().name; inline = true }
                            }

                            field { name = "Channel"; value = "#${it.channel.name}"; inline = true }

                            footer { text = it.channel.id.value.toString() }
                        }

                        // We're not logging these events, they're either irrelevant or don't
                        // concern a guild. This is an explicit silencing, so we don't trigger
                        // an issue on Sentry.
                        is CategoryUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is ChannelPinsUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is ConnectEvent -> logger.debug { "Ignoring event: $it" }
                        is DMChannelCreateEvent -> logger.debug { "Ignoring event: $it" }
                        is DMChannelDeleteEvent -> logger.debug { "Ignoring event: $it" }
                        is DMChannelUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is DisconnectEvent -> logger.debug { "Ignoring event: $it" }
                        is EmojisUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is GatewayEvent -> logger.debug { "Ignoring event: $it" }
                        is GuildCreateEvent -> logger.debug { "Ignoring event: $it" }
                        is GuildDeleteEvent -> logger.debug { "Ignoring event: $it" }
                        is GuildUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is IntegrationsUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is MembersChunkEvent -> logger.debug { "Ignoring event: $it" }
                        is MessageCreateEvent -> logger.debug { "Ignoring event: $it" }
                        is NewsChannelUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is PresenceUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is ReactionAddEvent -> logger.debug { "Ignoring event: $it" }
                        is ReactionRemoveEvent -> logger.debug { "Ignoring event: $it" }
                        is ReadyEvent -> logger.debug { "Ignoring event: $it" }
                        is ResumedEvent -> logger.debug { "Ignoring event: $it" }
                        is RoleUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is StoreChannelUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is TextChannelUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is TypingStartEvent -> logger.debug { "Ignoring event: $it" }
                        is UserUpdateEvent -> Unit // We have more specific handling for this event below.
                        is VoiceChannelUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is VoiceServerUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is VoiceStateUpdateEvent -> logger.debug { "Ignoring event: $it" }
                        is WebhookUpdateEvent -> logger.debug { "Ignoring event: $it" }

                        // This is an event we haven't accounted for that we may or
                        // may not want to log.
                        else -> logger.warn { "Unknown event: $it" }
                    }
                }
            }
        }

        event<UserUpdateEvent> {
            check(::isNotBot)

            action {
                with(event) {
                    val guild = config.getGuild()

                    if (guild.getMemberOrNull(user.id) == null) {
                        return@action
                    }

                    val delta = UserDelta.from(old, user)

                    if (delta?.changes?.isEmpty() != true) {
                        actionLog {
                            title = "User updated"

                            if (delta?.avatar != null) {
                                field {
                                    name = "Avatar"
                                    inline = true

                                    value = "[New avatar](${delta.avatar})"
                                }
                            }

                            field {
                                name = "Username"

                                value = if (delta?.username != null) {
                                    "**${user.username}**"
                                } else {
                                    user.username
                                }

                                inline = true
                            }

                            field {
                                name = "Discrim"

                                value = if (delta?.discriminator != null) {
                                    "**${user.discriminator}**"
                                } else {
                                    user.discriminator
                                }

                                inline = true
                            }

                            footer {
                                text = if (delta == null) {
                                    "Not cached: ${user.id.value}"
                                } else {
                                    user.id.value.toString()
                                }
                            }

                            thumbnail { url = user.avatar.url }
                        }
                    }
                }
            }
        }
    }
}
