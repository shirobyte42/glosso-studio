package me.shirobyte42.glosso.data.audio

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
        // Nasals - Nasals are distinct but sometimes misrecognized
        setOf("m", "n") to 0.7,
        setOf("n", "ŋ") to 0.7,
        // Approximants
        setOf("l", "r") to 0.6,
        setOf("w", "v") to 0.6,
        // Vowels (Close quality)
        setOf("i", "ɪ") to 0.7,
        setOf("e", "ɛ") to 0.7,
        setOf("æ", "a") to 0.7,
        setOf("ɑ", "a") to 0.7,
        setOf("u", "ʊ") to 0.7,
        setOf("ə", "ʌ") to 0.9,
        setOf("ə", "ɤ") to 0.9,
        setOf("ɔ", "o") to 0.8
    )

    fun calculateScoringResult(text: String, expected: String, actual: String): ScoringResult {
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
        val normalizedText = text.lowercase()
        val expectedPhonemes = getNormalizedPhoneList(expectedIpa)
        
        val n = normalizedText.length
        val m = expectedPhonemes.size
        
        if (m == 0) {
            return text.map { LetterFeedbackInfo(it.toString(), MatchStatus.PERFECT) }
        }

        // DP for Text to Phoneme alignment
        val dp = Array(n + 1) { IntArray(m + 1) }
        for (i in 0..n) dp[i][0] = i * 10
        for (j in 0..m) dp[0][j] = j * 10
        
        for (i in 1..n) {
            for (j in 1..m) {
                val cost = getG2PCost(normalizedText[i-1], expectedPhonemes[j-1])
                dp[i][j] = minOf(
                    dp[i-1][j] + 10, // Skip char
                    dp[i][j-1] + 10, // Skip phoneme
                    dp[i-1][j-1] + cost // Match
                )
            }
        }
        
        // Backtrack
        val charToPhonemeIndex = mutableMapOf<Int, Int>()
        var i = n
        var j = m
        while (i > 0 && j > 0) {
            val cost = getG2PCost(normalizedText[i-1], expectedPhonemes[j-1])
            if (dp[i][j] == dp[i-1][j-1] + cost) {
                charToPhonemeIndex[i-1] = j-1
                i--; j--
            } else if (dp[i][j] == dp[i-1][j] + 10) {
                i--
            } else {
                j--
            }
        }

        // Map status from phonemeAlignment (which contains EXPECTED phonemes)
        val expectedStatus = phonemeAlignment.filter { it.expected != "-" }.map { it.status }
        
        val result = mutableListOf<LetterFeedbackInfo>()
        val nonPronounceable = setOf('.', ',', '!', '?', ';', ':', '(', ')', '-', ' ', '\'', '"')
        
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
                // If a letter didn't map to a phoneme (backtrack skipped it), 
                // look for neighbors
                val prevStatus = charToPhonemeIndex[idx - 1]?.let { if (it < expectedStatus.size) expectedStatus[it] else null }
                val nextStatus = charToPhonemeIndex[idx + 1]?.let { if (it < expectedStatus.size) expectedStatus[it] else null }
                prevStatus ?: nextStatus ?: MatchStatus.PERFECT
            }
            result.add(LetterFeedbackInfo(char.toString(), status))
        }
        
        return result
    }

    private fun getG2PCost(char: Char, phoneme: String): Int {
        val p = phoneme.lowercase()
        val c = char.lowercaseChar()
        
        // Exact match
        if (p.startsWith(c)) return 0
        
        // Vowels
        val vowels = setOf('a', 'e', 'i', 'o', 'u')
        val ipaVowels = setOf('æ', 'ɑ', 'ɔ', 'ə', 'ɛ', 'ɪ', 'ʊ', 'ʌ', 'i', 'u', 'e', 'o', 'a')
        if (c in vowels && p.any { it in ipaVowels }) return 2
        
        // Consonants
        val g2pSim = mapOf(
            'c' to setOf("k", "s", "t͡ʃ"),
            'g' to setOf("ɡ", "g", "d͡ʒ", "j"),
            'j' to setOf("d͡ʒ", "j"),
            'y' to setOf("j", "i", "ɪ"),
            'x' to setOf("z", "k", "s"),
            'q' to setOf("k"),
            'w' to setOf("w", "u", "ʊ"),
            'h' to setOf("h", "ɦ"),
            'm' to setOf("m", "ɱ"),
            'n' to setOf("n", "ŋ", "ɲ"),
            'r' to setOf("r", "ɹ", "ɻ", "l"),
            's' to setOf("s", "z", "ʃ"),
            't' to setOf("t", "θ", "ð"),
            'd' to setOf("d", "ð"),
            'f' to setOf("f", "v"),
            'v' to setOf("v", "f")
        )
        
        if (g2pSim[c]?.contains(p) == true) return 2
        
        return 8 // High cost for non-matching
    }

    private fun getNormalizedPhoneList(ipa: String): List<String> {
        val raw = ipa.lowercase()
            .replace(Regex("[ˈˌ. ,?!()\\- \u00A0]"), "")
            .replace(Regex("[\u0300-\u0360\u0362-\u036F]"), "")
        
        val result = mutableListOf<String>()
        var i = 0
        while (i < raw.length) {
            val char = raw[i]
            
            // Affricates/tied phonemes
            if (i + 2 < raw.length && raw[i + 1] == '\u0361') {
                result.add(raw.substring(i, i + 3))
                i += 3
            } else if (i + 1 < raw.length && isTiedPair(raw[i], raw[i+1])) {
                // Handle common tied pairs that might not have the explicit tie bar in some IPA sources
                // though Allosaurus usually provides them.
                result.add(raw.substring(i, i + 2))
                i += 2
            } else {
                result.add(char.toString())
                i++
            }
        }
        
        // DO NOT collapse duplicates like 'mm' or 'tt' if they are intentional in IPA 
        // (though in English IPA they are rare, some models might output them)
        // Allosaurus output usually doesn't need collapsing if it represents distinct segments.
        return result
    }

    private fun isTiedPair(c1: Char, c2: Char): Boolean {
        val s = "$c1$c2"
        return s == "tʃ" || s == "dʒ" || s == "ts" || s == "dz"
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
                j--
            }
        }
        return result
    }

    fun normalize(ipa: String): String {
        return getNormalizedPhoneList(ipa).joinToString("")
    }
}
