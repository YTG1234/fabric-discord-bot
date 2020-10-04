/*
 * This Kotlin source file was generated by the Gradle 'init' task.
 */
package net.fabricmc.bot

import com.gitlab.kordlib.gateway.Intents
import com.gitlab.kordlib.gateway.PrivilegedIntent
import com.kotlindiscord.kord.extensions.ExtensibleBot
import mu.KotlinLogging
import net.fabricmc.bot.conf.buildInfo
import net.fabricmc.bot.conf.config
import net.fabricmc.bot.database.Migrator
import net.fabricmc.bot.extensions.*

/** The current instance of the bot. **/
val bot = ExtensibleBot(prefix = config.prefix, token = config.token)

/**
 * The main function. Every story has a beginning!
 */
@OptIn(PrivilegedIntent::class)
suspend fun main() {
    val logger = KotlinLogging.logger {}

    logger.info { "Starting Fabric Discord Bot, version ${buildInfo.version}." }

    val environment = System.getenv().getOrDefault("ENVIRONMENT", "production")

    Migrator.migrate()

    if (environment != "production") {
        // Don't really want this loaded in prod for obvious reasons
        bot.addExtension(EvalExtension::class)
    }

    bot.addExtension(CleanExtension::class)
    bot.addExtension(FilterExtension::class)
    bot.addExtension(GitHubExtension::class)
    bot.addExtension(InfractionsExtension::class)
    bot.addExtension(LoggingExtension::class)
    bot.addExtension(ModerationExtension::class)
    bot.addExtension(SelfRoleExtension::class)
    bot.addExtension(SyncExtension::class)
    bot.addExtension(VersionCheckExtension::class)

    // Leaving this here until Apple enables intents on the dev bot
//    val intents: (Intents.IntentsBuilder.() -> Unit)? = if (environment != "production"){
//        null
//    } else {
//        { +Intents.all }
//    }
//
//    bot.start(intents=intents)

    bot.start(intents={ +Intents.all })
}
