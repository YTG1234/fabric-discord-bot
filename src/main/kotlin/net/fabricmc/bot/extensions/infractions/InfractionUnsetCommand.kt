package net.fabricmc.bot.extensions.infractions

import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.MessageChannelBehavior
import dev.kord.core.behavior.channel.createEmbed
import dev.kord.core.entity.Message
import dev.kord.core.entity.User
import dev.kord.core.event.message.MessageCreateEvent
import com.kotlindiscord.kord.extensions.checks.topRoleHigherOrEqual
import com.kotlindiscord.kord.extensions.commands.Command
import com.kotlindiscord.kord.extensions.commands.CommandContext
import com.kotlindiscord.kord.extensions.commands.converters.optionalNumber
import com.kotlindiscord.kord.extensions.commands.converters.optionalUser
import com.kotlindiscord.kord.extensions.commands.parser.Arguments
import com.kotlindiscord.kord.extensions.extensions.Extension
import com.kotlindiscord.kord.extensions.utils.dm
import com.kotlindiscord.kord.extensions.utils.runSuspended
import mu.KotlinLogging
import net.fabricmc.bot.bot
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.constants.Colours
import net.fabricmc.bot.database.Infraction
import net.fabricmc.bot.defaultCheck
import net.fabricmc.bot.enums.InfractionTypes
import net.fabricmc.bot.enums.Roles
import net.fabricmc.bot.utils.modLog
import net.fabricmc.bot.utils.requireMainGuild
import java.time.Instant
import java.util.*

/** Data class representing the arguments for the infraction pardoning command.
 *
 * @param member The member to pardon.
 * @param memberLong The ID of the member to pardon, if they're not on the server.
 */
@Suppress("UndocumentedPublicProperty")
class InfractionUnsetCommandArgs : Arguments() {
    val member by optionalUser("member")
    val memberLong by optionalNumber("memberId")
}

private val logger = KotlinLogging.logger {}

/**
 * A command type for pardoning an infraction for a user.
 *
 * This command just handles the database work and notification, you'll still need to apply an [infrAction] to
 * apply the change on Discord.
 *
 * @param type The type of infraction to apply.
 * @param commandDescription The description to use for this command.
 * @param commandName The name of this command.
 * @param infrAction How to apply the infraction to the user.
 */
class InfractionUnsetCommand(extension: Extension, private val type: InfractionTypes,
                             private val commandDescription: String,
                             private val commandName: String,
                             aliasList: Array<String> = arrayOf(),
        // This can't be suspending, see comment in InfractionActions.applyInfraction
                             private val infrAction: Infraction.(
                                     targetId: Long, expires: Instant?
                             ) -> Unit
) : Command(extension) {
    private val queries = config.db.infractionQueries

    private val commandBody: suspend CommandContext.() -> Unit = {
        if (message.requireMainGuild(null)) {
            val args = parse(::InfractionUnsetCommandArgs)

            undoInfraction(
                    args.member,
                    args.memberLong,
                    message
            )
        }
    }

    private fun getInfractionMessage(public: Boolean, infraction: Infraction, showReason: Boolean = false): String {
        var message = if (public) {
            "<@!${infraction.target_id}> is no longer ${type.actionText}."
        } else {
            "You are no longer ${type.actionText}."
        }

        message += "\n\n"

        if (showReason) {
            message += if (type == InfractionTypes.NOTE) {
                "**Infraction Message:** ${infraction.reason}"
            } else {
                "**Infraction Reason:** ${infraction.reason}"
            }
        }

        return message
    }

    private suspend fun sendToUser(infraction: Infraction) {
        if (type.relay) {
            val targetObj = bot.kord.getUser(Snowflake(infraction.target_id))

            targetObj?.dm {
                embed {
                    color = Colours.POSITIVE
                    title = type.actionText.capitalize() + "!"

                    description = getInfractionMessage(false, infraction, true)

                    footer {
                        text = "Infraction ID: ${infraction.id}"
                    }

                    timestamp = Instant.now()
                }
            }

        }
    }

    private suspend fun sendToChannel(channel: MessageChannelBehavior, infraction: Infraction) {
        channel.createEmbed {
            color = Colours.POSITIVE
            title = "Infraction pardoned"

            description = getInfractionMessage(true, infraction)

            footer {
                text = "ID: ${infraction.id}"
            }

            timestamp = Instant.now()
        }
    }

    private suspend fun sendToModLog(infraction: Infraction, actor: User) {
        var descriptionText = getInfractionMessage(true, infraction, true)

        descriptionText += "\n\n**User ID:** `${infraction.target_id}`"
        descriptionText += "\n**Moderator:** ${actor.mention} (${actor.tag} / `${actor.id.value}`)"

        modLog {
            color = Colours.POSITIVE
            title = "Infraction Pardoned"

            description = descriptionText

            footer {
                text = "ID: ${infraction.id}"
            }
        }
    }

    private suspend fun undoInfraction(memberObj: User?, memberLong: Long?, message: Message) {
        val author = message.author!!
        val (memberId, memberMessage) = getMemberId(memberObj, memberLong)

        if (memberId == null) {
            message.channel.createMessage("${author.mention} $memberMessage")
            return
        }

        val infractions = runSuspended {
            queries.getActiveInfractionsByUser(memberId).executeAsList()
        }.filter { it.infraction_type == type }

        if (infractions.isEmpty()) {
            message.channel.createMessage(
                    "${author.mention} Unable to find a matching active infraction for that user."
            )

            return
        }

        for (infraction in infractions) {
            cancelJobForInfraction(UUID.fromString(infraction.id))

            infrAction.invoke(infraction, memberId, null)

            sendToUser(infraction)
            sendToChannel(message.channel, infraction)
            sendToModLog(infraction, message.author!!)
        }
    }

    override val checkList: MutableList<suspend (MessageCreateEvent) -> Boolean> = mutableListOf(
            ::defaultCheck,
            {
                if (type.notForTrainees) {
                    topRoleHigherOrEqual(config.getRole(Roles.MODERATOR))(it)
                } else {
                    topRoleHigherOrEqual(config.getRole(Roles.TRAINEE_MODERATOR))(it)
                }
            }
    )

    init {
        this.aliases = aliasList
        this.name = commandName
        this.description = commandDescription

        signature = "<user/id>"

        action(commandBody)
    }
}
