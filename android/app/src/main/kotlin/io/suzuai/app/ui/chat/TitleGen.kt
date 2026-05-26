package io.suzuai.app.ui.chat

/**
 * Generate a concise chat title from the user's first prompt.
 *
 * Rules (in order):
 *  1. Pure greeting / smalltalk → fixed labels ("Greeting", "Smalltalk").
 *  2. Project / task verbs ("build/fix/create/buatkan/...") → extract the
 *     subject phrase after the verb so the sidebar shows what they're working
 *     on, e.g. "Buatkan login page" → "Login page".
 *  3. Fallback → first non-trivial sentence, truncated.
 */
object TitleGen {
    private val GREETINGS = setOf(
        "hi", "hai", "halo", "hello", "helo", "hey", "yo", "hola",
        "salam", "assalam", "assalamualaikum", "selamat pagi", "selamat petang",
        "selamat malam", "good morning", "good evening", "good night",
        "morning", "evening", "night", "sup",
    )

    private val SMALLTALK = setOf(
        "apa khabar", "how are you", "khabar", "you ok", "you there",
        "boleh tolong", "tolong", "tq", "thanks", "thank you", "ok",
    )

    /** Action verbs the user starts a project request with. */
    private val PROJECT_VERBS = listOf(
        "buatkan", "buat", "bina", "build", "create", "develop", "make",
        "tolong buat", "tolong bina", "code", "tulis", "write",
        "fix", "betulkan", "perbaiki", "improve", "tambah", "add",
        "design", "rekabentuk", "implement", "implementasikan",
    )

    fun titleFor(prompt: String): String {
        val raw = prompt.trim()
        if (raw.isEmpty()) return "New chat"
        val lower = raw.lowercase()

        // 1. Pure greeting → don't pollute history with the literal text.
        if (GREETINGS.any { isStandaloneGreeting(lower, it) }) return "Greeting"
        if (SMALLTALK.any { lower.startsWith(it) && raw.length <= 30 }) return "Smalltalk"

        // 2. Project / task — pull the noun phrase after the verb.
        for (verb in PROJECT_VERBS) {
            if (lower.startsWith("$verb ")) {
                val rest = raw.substring(verb.length).trim().trimStart(':', '-', '—').trim()
                if (rest.isNotEmpty()) {
                    return capitalise(firstClause(rest)).take(48)
                }
            }
        }

        // 3. Default — first sentence-ish chunk, truncated.
        return capitalise(firstClause(raw)).take(60)
    }

    /** A greeting is "standalone" when the user's whole message is just it. */
    private fun isStandaloneGreeting(lower: String, greeting: String): Boolean {
        if (!lower.startsWith(greeting)) return false
        val rest = lower.removePrefix(greeting).trim().trimEnd('.', '!', '?', ',')
        return rest.isEmpty() || rest in GREETINGS || rest.length <= 4
    }

    private fun firstClause(text: String): String {
        // Cut at the first sentence-ending punctuation or newline.
        val end = text.indexOfAny(charArrayOf('.', '!', '?', '\n'))
        val slice = if (end > 0) text.substring(0, end) else text
        return slice.trim()
    }

    private fun capitalise(s: String): String {
        if (s.isEmpty()) return s
        return s.first().uppercase() + s.drop(1)
    }
}
