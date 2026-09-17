package io.github.teetotum_rs.app

import org.jetbrains.compose.resources.StringResource

/** Joins and leaves the network a Knob offers. */
interface Radio {
    /** Returns once traffic goes to the Knob's network; throws [JoinFailed] when it cannot. */
    suspend fun join(code: JoinCode)

    fun leave()
}

class JoinFailed(override val shown: Message) :
    Exception(shown.toString()),
    Shown {
    constructor(resource: StringResource, vararg args: Any) : this(messageOf(resource, *args))
}
