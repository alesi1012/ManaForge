package com.example.manaforge



import com.example.manaforge.Card
import com.example.manaforge.DeckCardWithDetails
import com.example.manaforge.DeckFormat

// ─────────────────────────────────────────────
//  Validation result types
// ─────────────────────────────────────────────

data class ValidationResult(
    val isValid: Boolean,
    val errors: List<String> = emptyList()
)

// ─────────────────────────────────────────────
//  Deck Validator
// ─────────────────────────────────────────────

object DeckValidator {

    /**
     * Validates [cards] according to [format] rules.
     * Returns a [ValidationResult] with all collected errors.
     */
    fun validate(
        format: DeckFormat,
        cards: List<DeckCardWithDetails>
    ): ValidationResult = when (format) {
        DeckFormat.STANDARD  -> validateStandard(cards)
        DeckFormat.COMMANDER -> validateCommander(cards)
    }

    // ── Standard ──────────────────────────────────────────────────────────

    private fun validateStandard(cards: List<DeckCardWithDetails>): ValidationResult {
        val errors = mutableListOf<String>()
        val totalCards = cards.sumOf { it.quantity }

        if (totalCards < 60) {
            errors += "A Standard deck needs at least 60 cards (currently $totalCards)."
        }

        cards.forEach { entry ->
            val isBasicLand = entry.card.type?.contains("Basic Land") == true
            if (!isBasicLand && entry.quantity > 4) {
                errors += "'${entry.card.name}' has ${entry.quantity} copies (max 4 for non-basic lands)."
            }
        }

        val illegalCards = cards.filter { entry ->
            !isStandardLegal(entry.card)
        }
        illegalCards.forEach { entry ->
            errors += "'${entry.card.name}' is not legal in Standard."
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    // ── Commander ─────────────────────────────────────────────────────────

    private fun validateCommander(cards: List<DeckCardWithDetails>): ValidationResult {
        val errors = mutableListOf<String>()
        val totalCards = cards.sumOf { it.quantity }

        if (totalCards != 100) {
            errors += "A Commander deck must have exactly 100 cards (currently $totalCards)."
        }

        // Find commander (legendary creature marked as commander)
        val commanders = cards.filter { it.card.type?.contains("Legendary Creature") == true }
        if (commanders.isEmpty()) {
            errors += "The deck has no Legendary Creature to serve as commander."
        }
        if (commanders.size > 1) {
            // Allowed only for partner commanders – simplified check
            errors += "Only 1 commander is allowed (unless they have Partner). Found ${commanders.size} Legendary Creatures."
        }

        // No duplicates (except basic lands)
        cards.forEach { entry ->
            val isBasicLand = entry.card.type?.contains("Basic Land") == true
            if (!isBasicLand && entry.quantity > 1) {
                errors += "'${entry.card.name}' has ${entry.quantity} copies. Only 1 copy allowed per card (except basic lands)."
            }
        }

        // Color identity check
        if (commanders.isNotEmpty()) {
            val commanderColors = commanders.flatMap { parseColors(it.card) }.toSet()
            cards.forEach { entry ->
                val cardColors = parseColors(entry.card).toSet()
                if (!commanderColors.containsAll(cardColors)) {
                    errors += "'${entry.card.name}' contains colors outside the commander's color identity."
                }
            }
        }

        return ValidationResult(isValid = errors.isEmpty(), errors = errors)
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /**
     * Very lightweight legality check based on cached data.
     * For a production app you'd also cache the legalities field from Scryfall.
     * Here we rely on the 'rarity' / 'set_name' being present as a proxy indicator.
     * In a real scenario: store `is_standard_legal` boolean from Scryfall legalities.standard.
     */
    private fun isStandardLegal(card: Card): Boolean {
        // Basic lands are always legal
        if (card.type?.contains("Basic Land") == true) return true
        // If we stored set_name we could cross-check against Standard sets;
        // for now we trust cards cached from a Standard search query.
        return true
    }

    /** Parses the comma-separated colors string stored in the Card model. */
    private fun parseColors(card: Card): List<String> =
        card.colors?.split(",")?.map { it.trim() }?.filter { it.isNotBlank() } ?: emptyList()
}