package io.gymtracker.sync

sealed interface ReceiveResult {
    data class Accepted(val fromNickname: String, val newEvents: Int) : ReceiveResult
    data class Rejected(val reason: String) : ReceiveResult
}
