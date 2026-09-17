package io.github.teetotum_rs.app

import androidx.compose.runtime.Composable
import org.jetbrains.compose.resources.PluralStringResource
import org.jetbrains.compose.resources.StringResource
import org.jetbrains.compose.resources.pluralStringResource
import org.jetbrains.compose.resources.stringResource

/** A line for the user, kept unresolved so code outside composition can make one; [text] resolves it. */
sealed interface Message {
    data class Resource(val resource: StringResource, val args: List<Any> = emptyList()) : Message {
        override fun toString() = "${resource.key}$args"
    }

    /** [resource] for [count], which is also its only argument. */
    data class Plural(val resource: PluralStringResource, val count: Int) : Message {
        override fun toString() = "${resource.key}[$count]"
    }

    /** Text that needs no translation, such as a file name or a system's own error. */
    data class Raw(val text: String) : Message {
        override fun toString() = text
    }
}

fun messageOf(resource: StringResource, vararg args: Any): Message = Message.Resource(resource, args.toList())

@Composable
fun Message.text(): String = when (this) {
    is Message.Resource -> stringResource(resource, *args.toTypedArray())
    is Message.Plural -> pluralStringResource(resource, count, count)
    is Message.Raw -> text
}

/** A failure that brings the line the user sees about it. */
interface Shown {
    val shown: Message
}

/** The line to show for this failure: its own, or else the message of whatever threw it. */
fun Throwable.toMessage(): Message = (this as? Shown)?.shown ?: Message.Raw(message ?: toString())
