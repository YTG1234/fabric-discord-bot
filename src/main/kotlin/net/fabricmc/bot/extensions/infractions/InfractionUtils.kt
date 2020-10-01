package net.fabricmc.bot.extensions.infractions

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.DateTimeFormatterBuilder
import java.time.temporal.ChronoField
import java.util.*

private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM/uuuu 'at' HH:mm '(UTC)'", Locale.ENGLISH)
private val mySqlTimeFormatter = DateTimeFormatter.ofPattern("uuuu-MM-dd HH:mm:ss", Locale.ENGLISH)

private val mySqlTimeParser = DateTimeFormatterBuilder()
        .appendPattern("uuuu-MM-dd HH:mm:ss['.'n]")
        .parseDefaulting(ChronoField.NANO_OF_SECOND, 0)
        .toFormatter()
        .withZone(ZoneId.of("UTC"))

/**
 * Format an Instant for display on Discord.
 *
 * @param ts [Instant] to format.
 * @return String representation of the given [Instant].
 */
fun instantToDisplay(ts: Instant): String = timeFormatter.format(ts.atZone(ZoneId.of("UTC")))

/**
 * Given a MySQL-formatted datetime string, return an Instant.
 *
 * @param ts String representation of a MySQL datetime.
 * @return [Instant] representing the given datetime.
 */
fun mysqlToInstant(ts: String?): Instant? {
    ts ?: return null

    return mySqlTimeParser.parse(ts) { accessor -> Instant.from(accessor) }
}

/**
 * Given an [Instant], return a MySQL-formatted datetime string.
 *
 * @param ts: [Instant] to format to a MySQL string.
 * @return MySQL-formatted string representing the given [Instant].
 */
fun instantToMysql(ts: Instant): String =
        mySqlTimeFormatter.format(
                ts.atZone(
                        ZoneId.of("UTC")
                )
        )
