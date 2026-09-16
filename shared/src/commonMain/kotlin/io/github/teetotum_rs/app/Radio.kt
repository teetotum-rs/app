package io.github.teetotum_rs.app

/** Joins and leaves the network a Knob offers. */
interface Radio {
    /** Returns once traffic goes to the Knob's network; throws [JoinFailed] when it cannot. */
    suspend fun join(code: JoinCode)

    fun leave()
}

class JoinFailed(message: String) : Exception(message)
