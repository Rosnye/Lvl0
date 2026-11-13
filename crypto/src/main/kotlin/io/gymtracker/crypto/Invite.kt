package io.gymtracker.crypto

import java.util.Base64

/**
 * Invitación compacta con la tarjeta firmada del usuario: "GT1:" + base64url(JSON).
 * La misma cadena va en el QR y en el texto que se comparte por mensajería.
 */
object Invite {
    private const val PREFIX = "GT1:"
    private val TOKEN = Regex("""GT1:[A-Za-z0-9_-]{40,}""")

    fun encode(card: FriendCard): String =
        PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(card.encode().encodeToByteArray())

    /** Busca una invitación válida dentro de cualquier texto (mensaje pegado, QR). */
    fun decode(text: String): FriendCard? {
        val token = TOKEN.find(text)?.value ?: return FriendCard.decode(text.trim())
        val json = runCatching { Base64.getUrlDecoder().decode(token.removePrefix(PREFIX)).decodeToString() }.getOrNull() ?: return null
        return FriendCard.decode(json)
    }
}
