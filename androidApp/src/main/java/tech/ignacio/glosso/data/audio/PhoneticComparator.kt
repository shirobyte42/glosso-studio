package tech.ignacio.glosso.data.audio

import android.util.Log
import kotlin.math.max

data class ScoringResult(
    val score: Int,
    val normalizedExpected: String,
    val normalizedActual: String,
    val alignment: List<PhonemeMatch> = emptyList()
)

data class PhonemeMatch(
    val expected: String,
    val actual: String,
    val status: MatchStatus // PERFECT, CLOSE, MISSED
)

data class LetterFeedbackInfo(
    val char: String,
    val status: MatchStatus
)

enum class MatchStatus { PERFECT, CLOSE, MISSED }

object PhoneticComparator {
    
    private const val TAG = "PhoneticComparator"

    // Similarity matrix for near-misses (voiced/voiceless pairs, etc.)
    private val similarityMatrix = mapOf(
        // Plosives
        setOf("p", "b") to 0.8,
        setOf("t", "d") to 0.8,
        setOf("k", "ɡ") to 0.8,
        setOf("k", "g") to 0.8,
        // Fricatives
        setOf("f", "v") to 0.8,
        setOf("s", "z") to 0.8,
        setOf("ʃ", "ʒ") to 0.8,
        setOf("θ", "ð") to 0.8,
        // Vowels (Close quality)
        setOf("i", "ɪ") to 0.7,
        setOf("e", "ɛ") to 0.7,
        setOf("æ", "a") to 0.7,
        setOf("ɑ", "a") to 0.7,
        setOf("u", "ʊ") to 0.7,
        setOf("ə", "ʌ") to 0.9,
        setOf("ə", "ɤ") to 0.9
    )

    fun calculateScoringResult(text: String, expected: String, actual: String): ScoringResult {
        // We compare normalized phone lists for alignment
        val expectedList = getNormalizedPhoneList(expected)
        val actualList = getNormalizedPhoneList(actual)
        
        if (expectedList.isEmpty()) {
            return ScoringResult(if (actualList.isEmpty()) 100 else 0, "", "")
        }
        
        // Use weighted Levenshtein for scoring
        val phonemeAlignment = align(expectedList, actualList)
        
        var totalWeight = 0.0
        var matchWeight = 0.0
        
        for (match in phonemeAlignment) {
            totalWeight += 1.0
            matchWeight += when (match.status) {
                MatchStatus.PERFECT -> 1.0
                MatchStatus.CLOSE -> 0.7 // Partial credit
                MatchStatus.MISSED -> 0.0
            }
        }
        
        val score = if (totalWeight > 0) (matchWeight / totalWeight * 100).toInt() else 0
        
        return ScoringResult(
            score = score.coerceIn(0, 100),
            normalizedExpected = expectedList.joinToString(""),
            normalizedActual = actualList.joinToString(""),
            alignment = phonemeAlignment
        )
    }

    fun generateLetterFeedback(text: String, expectedIpa: String, phonemeAlignment: List<PhonemeMatch>): List<LetterFeedbackInfo> {
        // 1. Align text characters with expected phonemes
        // First, simplify both for alignment
        val normalizedText = text.lowercase()
        val expectedPhonemes = getNormalizedPhoneList(expectedIpa)
        
        // Alignment of text chars to expected phonemes (many-to-many potentially)
        // For simplicity, we'll do a character-to-phoneme alignment
        val n = normalizedText.length
        val m = expectedPhonemes.size
        val dp = Array(n + 1) { IntArray(m + 1) }
        
        for (i in 0..n) dp[i][0] = i * 2
        for (j in 0..m) dp[0][j] = j * 2
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = getG2PCost(normalizedText[i-1], expectedPhonemes[j-1])
                dp[i][j] = minOf(
                    dp[i-1][j] + 2, // Deletion
                    dp[i][j-1] + 2, // Insertion
                    dp[i-1][j-1] + cost // Match/Sub
                )
            }
        }
        
        // Backtrack to find which char belongs to which phoneme
        val charToPhonemeIndex = mutableMapOf<Int, Int>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val cost = getG2PCost(normalizedText[i-1], expectedPhonemes[j-1])
                if (dp[i][j] == dp[i-1][j-1] + cost) {
                    charToPhonemeIndex[i-1] = j-1
                    i--; j--
                    continue
                }
            }
            if (i > 0 && (j == 0 || dp[i][j] == dp[i-1][j] + 2)) {
                // Character doesn't map to a phoneme (like space or punctuation)
                i--
            } else {
                j--
            }
        }

        // 2. Map status from phonemeAlignment back to chars
        val expectedStatus = mutableListOf<MatchStatus>()
        for (match in phonemeAlignment) {
            // We only care about EXPECTED phonemes
            if (match.expected != "-") {
                expectedStatus.add(match.status)
            }
        }
        
        val result = mutableListOf<LetterFeedbackInfo>()
        val nonPronounceable = setOf('.', ',', '!', '?', ';', ':', '(', ')', '-', ' ')
        
        for (idx in 0 until text.length) {
            val char = text[idx]
            
            if (char in nonPronounceable) {
                result.add(LetterFeedbackInfo(char.toString(), MatchStatus.PERFECT))
                continue
            }
            
            val phonemeIdx = charToPhonemeIndex[idx]
            val status = if (phonemeIdx != null && phonemeIdx < expectedStatus.size) {
                expectedStatus[phonemeIdx]
            } else {
                // Should not happen for letters with G2P mapping
                MatchStatus.PERFECT 
            }
            result.add(LetterFeedbackInfo(char.toString(), status))
        }
        
        return result
    }

    private fun getG2PCost(char: Char, phoneme: String): Int {
        val p = phoneme[0] // Simple for now
        if (char == p) return 0
        
        // Vowel similarity
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val ipaVowels = setOf('æ', 'ɑ', 'ɔ', 'ə', 'ɛ', 'ɪ', 'ʊ', 'ʌ', 'i', 'u', 'e', 'o')
        if (char in vowels && p in ipaVowels) return 1
        
        // Consonant similarity
        val g2pSim = mapOf(
            'c' to setOf("k", "s"),
            'g' to setOf("ɡ", "g", "d͡ʒ", "j"),
            'j' to setOf("d͡ʒ", "j"),
            'y' to setOf("j", "i", "ɪ"),
            'x' to setOf("z", "k", "s"),
            'q' to setOf("k"),
            'w' to setOf("w", "u", "ʊ")
        )
        if (g2pSim[char]?.contains(phoneme) == true) return 1
        
        return 2
    }

    private fun getNormalizedPhoneList(ipa: String): List<String> {
        // Remove punctuation, spaces, and stress marks
        val raw = ipa.lowercase().replace(Regex("[ˈˌ. ,?!()\\- \u00A0\u0300-\u036F]"), "")
        
        val result = mutableListOf<String>()
        // Just treat each remaining character as a phone unit for now 
        // since we removed the multi-char phoneticGroups mapping.
        for (char in raw) {
            result.add(char.toString())
        }
        
        // Collapse duplicates (e.g. "hh" -> "h")
        val collapsed = mutableListOf<String>()
        if (result.isNotEmpty()) {
            collapsed.add(result[0])
            for (j in 1 until result.size) {
                if (result[j] != result[j-1]) collapsed.add(result[j])
            }
        }
        return collapsed
    }

    private fun getSimilarity(p1: String, p2: String): Double {
        if (p1 == p2) return 1.0
        val pair = setOf(p1, p2)
        return similarityMatrix[pair] ?: 0.0
    }

    private fun align(expected: List<String>, actual: List<String>): List<PhonemeMatch> {
        val n = expected.size
        val m = actual.size
        val dp = Array(n + 1) { DoubleArray(m + 1) }
        
        for (i in 0..n) dp[i][0] = i.toDouble()
        for (j in 0..m) dp[0][j] = j.toDouble()
        
        for (i in 1..n) {
            for (j in 1..m) {
                val sim = getSimilarity(expected[i - 1], actual[j - 1])
                val cost = 1.0 - sim
                dp[i][j] = minOf(
                    dp[i - 1][j] + 1.0,      // Deletion
                    dp[i][j - 1] + 1.0,      // Insertion
                    dp[i - 1][j - 1] + cost  // Substitution
                )
            }
        }
        
        // Backtrack to find alignment
        val result = mutableListOf<PhonemeMatch>()
        var i = n
        var j = m
        while (i > 0 || j > 0) {
            if (i > 0 && j > 0) {
                val sim = getSimilarity(expected[i - 1], actual[j - 1])
                val cost = 1.0 - sim
                if (dp[i][j] == dp[i - 1][j - 1] + cost) {
                    val status = when {
                        sim >= 1.0 -> MatchStatus.PERFECT
                        sim >= 0.5 -> MatchStatus.CLOSE
                        else -> MatchStatus.MISSED
                    }
                    result.add(0, PhonemeMatch(expected[i - 1], actual[j - 1], status))
                    i--; j--
                    continue
                }
            }
            if (i > 0 && (j == 0 || dp[i][j] == dp[i - 1][j] + 1.0)) {
                result.add(0, PhonemeMatch(expected[i - 1], "-", MatchStatus.MISSED))
                i--
            } else {
                // Ignore insertions from user for the alignment display
                j--
            }
        }
        return result
    }

    fun normalize(ipa: String): String {
        return getNormalizedPhoneList(ipa).joinToString("")
    }
}
