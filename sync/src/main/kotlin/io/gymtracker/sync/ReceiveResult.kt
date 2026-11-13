package io.gymtracker.sync

import io.gymtracker.crypto.RejectReason

sealed interface ReceiveResult {
    data class Accepted(val fromNickname: String, val newEvents: Int) : ReceiveResult
    data class Rejected(val reason: RejectReason) : ReceiveResult
}
