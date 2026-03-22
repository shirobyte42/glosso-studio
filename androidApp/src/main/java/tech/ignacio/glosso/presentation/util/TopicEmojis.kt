package tech.ignacio.glosso.presentation.util

object TopicEmojiMap {
    private val map = mapOf(
        "Daily Life" to "🏠",
        "Daily Routine" to "⏰",
        "Family" to "👪",
        "Food" to "🍕",
        "Food & Drink" to "🍹",
        "Greetings" to "👋",
        "Introduction" to "🙋",
        "My House" to "🏡",
        "Travel" to "✈️",
        "Weather" to "☁️",
        "Hobbies" to "🎨",
        "Shopping" to "🛒",
        "Work" to "💼",
        "Jobs" to "👷",
        "Health" to "🏥",
        "Entertainment" to "🎬",
        "Environment" to "🌳",
        "Technology" to "💻",
        "News" to "📰",
        "Relationships" to "💑",
        "Social Media" to "📱",
        "Science" to "🔬",
        "Education" to "🎓",
        "Arts" to "🎭",
        "Lifestyle" to "🧘",
        "Politics" to "🗳️",
        "Law" to "⚖️",
        "Economics" to "📈",
        "Philosophy" to "🏛️",
        "Ethics" to "🧠",
        "Psychology" to "💭",
        "Literature" to "📚",
        "Global Issues" to "🌍",
        "Advanced Sociology" to "👥",
        "Linguistics" to "🗣️",
        "Diplomacy" to "🤝",
        "Metaphysics" to "🌌",
        "Quantum Physics" to "⚛️",
        "My Hometown" to "🏘️"
    )

    fun getEmoji(topic: String): String {
        return map[topic] ?: "✨" // Default sparkle if topic not in map
    }
}
