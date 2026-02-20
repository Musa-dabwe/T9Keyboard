package com.musa.t9keyboard

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.ColorDrawable
import android.graphics.drawable.RippleDrawable
import android.util.AttributeSet
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.musa.t9keyboard.databinding.EmojiPickerBinding
import org.json.JSONArray

class EmojiPickerView @JvmOverloads constructor(
    context: Context, attrs: AttributeSet? = null, defStyleAttr: Int = 0
) : LinearLayout(context, attrs, defStyleAttr) {

    private var binding: EmojiPickerBinding? = null
    var isInitialized = false
        private set

    var onEmojiClickListener: ((String) -> Unit)? = null
    var onBackClickListener: (() -> Unit)? = null

    private var emojiTypeface: Typeface? = null
    private val preferences = PreferencesManager(context)
    private var recentlyUsed = mutableListOf<String>()
    private var currentCategory = "Smileys & Emotion"
    private var accentColor = Color.parseColor("#00BFA5") // Default accent

    private val allEmojiCategories = linkedMapOf(
        "Smileys & Emotion" to listOf(
            "😀", "😃", "😄", "😁", "😆", "😅", "😂", "🤣", "🥲", "😊", "😇", "🙂", "🙃", "😉", "😌", "😍", "🥰", "😘", "😗", "😙", "😚", "😋", "😛", "😝", "😜", "🤪", "🤨", "🧐", "🤓", "😎", "🥸", "🤩", "🥳", "😏", "😒", "😞", "😔", "😟", "😕", "🙁", "☹️", "😣", "😖", "😫", "😩", "🥺", "😢", "😭", "😤", "😠", "😡", "🤬", "🤯", "😳", "🥵", "🥶", "😱", "😨", "😰", "😥", "😓", "🤗", "🤔", "🫣", "🤭", "🫢", "🫡", "🤫", "🫠", "🤥", "😶", "🫥", "😐", "🫤", "😑", "😬", "🙄", "😯", "😦", "😧", "😮", "😲", "🥱", "😴", "🤤", "😪", "😮‍💨", "😵", "😵‍💫", "🫨", "🤐", "🥴", "🤢", "🤮", "🤧", "😷", "🤒", "🤕"
        ),
        "People & Body" to listOf(
            "👋", "🤚", "🖐", "✋", "🖖", "🫱", "🫲", "🫳", "🫴", "👌", "🤌", "🤏", "✌️", "🤞", "🤟", "🤘", "🤙", "👈", "👉", "👆", "👇", "☝️", "👍", "👎", "✊", "👊", "🤛", "🤜", "👏", "🙌", "👐", "🤲", "🤝", "🙏", "✍️", "💅", "🤳", "💪", "🦾", "🦿", "🦵", "🦶", "👂", "🦻", "👃", "🧠", "🫀", "🫁", "🦷", "👀", "👁", "👅", "👄", "💋", "🩸", "👤", "👥", "🫂", "👶", "👧", "🧒", "👦", "👩", "🧑", "👨", "👩‍🦱", "🧑‍🦱", "👨‍🦱", "👩‍🦰", "🧑‍🦰", "👨‍🦰", "👱‍♀️", "👱", "👱‍♂️", "👩‍🦳", "🧑‍🦳", "👨‍🦳", "👩‍🦲", "🧑‍🦲", "👨‍🦲", "🧔‍♀️", "🧔", "🧔‍♂️", "👵", "🧓", "👴", "👲", "👳‍♀️", "👳", "👳‍♂️", "🧕", "👮‍♀️", "👮", "👮‍♂️", "👷‍♀️", "👷", "👷‍♂️", "💂‍♀️", "💂", "💂‍♂️"
        ),
        "Animals & Nature" to listOf(
            "🐶", "🐱", "🐭", "🐹", "🐰", "🦊", "🐻", "🐼", "🐻‍❄️", "🐨", "🐯", "🦁", "🐮", "🐷", "🐽", "🐸", "🐵", "🙈", "🙉", "🙊", "🐒", "🐔", "🐧", "🐦", "🐤", "🐣", "🐥", "🦆", "🦅", "🦉", "🦇", "🐺", "🐗", "🐴", "🦄", "🐝", "🪱", "🐛", "🦋", "🐌", "🐞", "🐜", "🪰", "🪲", "🪳", "🦟", "🦗", "🕷", "🕸", "🦂", "🐢", "🐍", "🦎", "🦖", "🦕", "🐙", "🦑", "🦐", "🦞", "🦀", "🐡", "🐠", "🐟", "🐬", "🐳", "🐋", "🦈", "🐊", "🐅", "🐆", "🦓", "🦍", "🦧", "🦣", "🐘", "🦛", "🦏", "🐪", "🐫", "🦒", "🦘", "🦬", "🐃", "🐂", "🐄", "🐎", "🐖", "🐏", "🐑", "🐐", "🦌", "🐕", "🐩", "🦮", "🐕‍🦺", "🐈", "🐈‍⬛", "🐓", "🦃", "🦚", "🦜", "🦢", "🦩", "🕊", "🐇", "🦝", "🦨", "🦡", "🦦", "🦥", "🐁", "🐀", "🐿", "🦔", "🐾"
        ),
        "Food & Drink" to listOf(
            "🍎", "🍊", "🍋", "🍇", "🍓", "🍔", "🍕", "🌮", "🍏", "🍐", "🍌", "🍉", "🫐", "🍈", "🍒", "🍑", "🥭", "🍍", "🥥", "🥝", "🍅", "🍆", "🥑", "🥦", "🥬", "🥒", "🌶", "🫑", "🌽", "🥕", "🫒", "🧄", "🧅", "🍄", "🥜", "🫘", "🌰", "🍞", "🥐", "🥖", "🫓", "🥨", "🥯", "🥞", "🧇", "🧀", "🍖", "🍗", "🥩", "🥓", "🍟", "🥪", "🥙", "🧆", "🌯", "🥗", "🥘", "🍲", "🫕", "🥣", "🍿", "🧈", "🧂", "🥫", "🍱", "🍘", "🍙", "🍚", "🍛", "🍜", "🍝", "🍠", "🍢", "🍣", "🍤", "🍥", "🥮", "🍡", "🥟", "🥠", "🥡", "🦀", "🦞", "🦐", "🦑", "🦪", "🍦", "🍧", "🍨", "🍩", "🍪", "🎂", "🍰", "🧁", "🥧", "🍫", "🍬", "🍭", "🍮", "🍯", "🍼", "🥛", "☕️", "🫖", "🍵", "🍶", "🍾", "🍷", "🍸", "🍹", "🍺", "🍻", "🥂", "🥃", "🥤", "🧋", "🧃", "🧉", "🧊"
        ),
        "Travel & Places" to listOf(
            "✈️", "🚀", "🚗", "🚕", "🚙", "🚌", "🛸", "🏠", "🌍", "🌎", "🌏", "🗺", "🏔", "⛰", "🌋", "🗻", "🏕", "🏖", "🏜", "🏝", "🏞", "🏟", "🏛", "🏗", "🧱", "🏘", "🏚", "🏡", "🏢", "🏣", "🏤", "🏥", "🏦", "🏨", "🏩", "🏪", "🏫", "🏬", "🏭", "🏯", "🏰", "💒", "🗼", "🗽", "⛪️", "🕌", "⛩", "🕍", "🕋", "⛲️", "⛺️", "🌁", "🌃", "🏙", "🌄", "🌆", "🌇", "🌉", "♨️", "🎠", "🎡", "🎢", "💈", "🎪", "🚂", "🚃", "🚄", "🚅", "🚆", "🚇", "🚈", "🚉", "🚊", "🚝", "🚞", "🚋", "🚍", "🚎", "🚐", "🚑", "🚒", "🚓", "🚔", "🚖", "🚘", "🛻", "🚚", "🚛", "🚜", "🏎", "🏍", "🛵", "🦽", "🦼", "🛺", "🚲", "🛴", "🛹", "🛼", "🚏", "🛣", "🛤", "🛢", "⛽️", "🚨", "🚥", "🚦", "🛑", "🚧", "⚓️", "⛵️", "🛶", "🚤", "🛳", "⛴", "🚢", "🛩", "🛫", "🛬", "🪂", "💺", "🚁", "🚟", "🚠", "🚡", "🛰"
        ),
        "Activities" to listOf(
            "⚽", "🏀", "🏈", "⚾", "🥎", "🏐", "🏉", "🎾", "🏒", "🏑", "🏏", "🏓", "🏸", "🏹", "⛳", "⛸", "🎣", "🚣", "🏊", "🏄", "🏇", "🚴", "🚵", "🏆", "🥇", "🥈", "🥉", "🏅", "🎖", "🏵", "🎫", "🎟", "🎭", "🎨", "🎬", "🎤", "🎧", "🎹", "🥁", "🎷", "🎺", "🎸", "🪕", "🎻", "🎲", "♟", "🎯", "🎳", "🎮", "🕹", "🎰", "🧩", "🧸", "🪅", "🪩", "🪆", "🃏", "🀄️", "🎴", "🖼", "🧵", "🪡", "🧶", "🪢", "🧗‍♀️", "🧗", "🧗‍♂️", "🤺", "⛷", "🏂", "🏌️‍♀️", "🏌️", "🏌️‍♂️", "🏄‍♀️", "🏄", "🏄‍♂️", "🚣‍♀️", "🚣", "🚣‍♂️", "🏊‍♀️", "🏊", "🏊‍♂️", "⛹️‍♀️", "⛹️", "⛹️‍♂️", "🏋️‍♀️", "🏋️", "🏋️‍♂️", "🚴‍♀️", "🚴", "🚴‍♂️", "🚵‍♀️", "🚵", "🚵‍♂️", "🤸‍♀️", "🤸", "🤸‍♂️", "🤽‍♀️", "🤽", "🤽‍♂️", "🤾‍♀️", "🤾", "🤾‍♂️", "🤹‍♀️", "🤹", "🤹‍♂️", "🧘‍♀️", "🧘", "🧘‍♂️", "🛀", "🛌"
        ),
        "Objects" to listOf(
            "💡", "📱", "💻", "⌨", "🖥", "🖨", "📷", "📹", "🎥", "🎞", "📞", "☎", "📟", "📠", "📺", "📻", "🎙", "🎚", "🎛", "🧭", "⏱", "⏲", "⏰", "🕰", "⏳", "⌛", "🔋", "🔌", "🔦", "🕯", "🪔", "🧯", "🛢", "💸", "💵", "💴", "💶", "💷", "🪙", "💰", "💳", "💎", "⚖", "🪜", "🔧", "🔨", "⚒", "🛠", "⛏", "🔩", "⚙", "🪛", "🧱", "⛓", "🧲", "🔫", "💣", "🧨", "🪓", "🗡", "⚔️", "🛡", "🚬", "⚰️", "🪦", "⚱️", "🏺", "🔮", "🪄", "🧿", "📿", "💈", "⚗️", "🔭", "🔬", "🕳", "💊", "💉", "🩸", "🩹", "🩺", "🌡", "🧹", "🪠", "🧺", "🧻", "🧼", "🫧", "🪥", "🪒", "🧽", "🪣", "🧴", "🛎", "🔑", "🗝", "🚪", "🪑", "🛋", "🛏", "🛌", "🪞", "🪟", "🛍", "🛒", "🎁", "🎈", "🎏", "🎀", "🏮", "🎐", "🧧", "✉️", "📩", "📨", "📧", "💌", "📥", "📤", "📦", "🏷", "🪪", "📪", "📫", "📬", "📭", "📮", "🗳", "✏️", "✒️", "🖋", "🖊", "🖌", "🖍", "📝"
        ),
        "Symbols" to listOf(
            "❤️", "🧡", "💛", "💚", "💙", "💜", "🖤", "🤍", "🤎", "💔", "❤️‍🔥", "❤️‍🩹", "❣️", "💕", "💞", "💓", "💗", "💖", "💘", "💝", "💟", "☮️", "✝️", "☪️", "🕉️", "☸️", "✡️", "🔯", "🕎", "☯️", "☦️", "🛐", "⛎", "♈️", "♉️", "♊️", "♋️", "♌️", "♍️", "♎️", "♏️", "♐️", "♑️", "♒️", "♓️", "🆔", "⚛️", "🉑", "☢️", "☣️", "📴", "📳", "🈶", "🈚️", "🈸", "🈺", "🈷️", "✴️", "VS", "🆚", "💮", "🉐", "㊙️", "㊗️", "🈴", "🈵", "🈹", "🈲", "🅰️", "🅱️", "🆑", "🅾️", "🆘", "❌", "⭕️", "🛑", "⛔️", "📛", "🚫", "💯", "💢", "♨️", "🚷", "🚯", "🚳", "🚱", "🔞", "📵", "🚭", "❗️", "❕", "❓", "❔", "‼️", "⁉️", "🔅", "🔆", "〽️", "⚠️", "🚸", "🔱", "⚜️", "🔰", "♻️", "✅", "🈯️", "💹", "❇️", "✳️", "❎", "🌐", "💠", "Ⓜ️", "🌀", "💤"
        ),
        "Flags" to listOf(
            "🏁", "🚩", "🎌", "🏴", "🏳", "🇺🇳", "🏴‍☠", "🇿🇲", "🇦🇫", "🇦🇽", "🇦🇱", "🇩🇿", "🇦🇸", "🇦🇩", "🇦🇴", "🇦🇮", "🇦🇶", "🇦🇬", "🇦🇷", "🇦🇲", "🇦🇼", "🇦🇺", "🇦🇹", "🇦🇿", "🇧🇸", "🇧🇭", "🇧🇩", "🇧🇧", "🇧🇾", "🇧🇪", "🇧🇿", "🇧🇯", "🇧🇲", "🇧🇹", "🇧🇴", "🇧🇦", "🇧🇼", "🇧🇷", "🇮🇴", "🇻🇬", "🇧🇳", "🇧🇬", "🇧🇫", "🇧🇮", "🇰🇭", "🇨🇲", "🇨🇦", "🇮🇨", "🇨🇻", "🇧🇶", "🇰🇾", "🇨🇫", "🇹🇩", "🇨🇱", "🇨🇳", "🇨🇽", "🇨🇨", "🇨🇴", "🇰🇲", "🇨🇬", "🇨🇩", "🇨🇰", "🇨🇷", "🇨🇮", "🇭🇷", "🇨🇺", "🇨🇼", "🇨🇾", "🇨🇿", "🇩🇰", "🇩🇯", "🇩🇲", "🇩🇴", "🇪🇨", "🇪🇬", "🇸🇻", "🇬🇶", "🇪🇷", "🇪🇪", "🇸🇿", "🇪🇹", "🇪🇺", "🇫🇰", "🇫🇴", "🇫🇯", "🇫🇮", "🇫🇷", "🇬🇫", "🇵🇫", "🇹🇫", "🇬🇦", "🇬🇲", "🇬🇪", "🇩🇪", "🇬🇭", "🇬🇮", "🇬🇷", "🇬🇱", "🇬🇩", "🇬🇵", "🇬🇺", "🇬🇹", "🇬🇬", "🇬🇳", "🇬🇼", "🇬🇾", "🇭🇹", "🇭🇳", "🇭🇰", "🇭🇺", "🇮🇸", "🇮🇳", "🇮🇩", "🇮🇷", "🇮🇶", "🇮🇪", "🇮🇲", "🇮🇱", "🇮🇹", "🇯🇲", "🇯🇵", "🇯🇪", "🇯🇴", "🇰🇿", "🇰🇪", "🇰🇮", "🇽🇰", "🇰🇼", "🇰🇬", "🇱🇦", "🇱🇻", "🇱🇧", "🇱🇸", "🇱🇷", "🇱🇾", "🇱🇮", "🇱🇹", "🇱🇺", "🇲🇴", "🇲🇬", "🇲🇼", "🇲🇾", "🇲🇻", "🇲🇱", "🇲🇹", "🇲🇭", "🇲🇶", "🇲🇷", "🇲🇺", "🇾🇹", "🇲🇽", "🇫🇲", "🇲🇩", "🇲🇨", "🇲🇳", "🇲🇪", "🇲🇸", "🇲🇦", "🇲🇿", "🇲🇲", "🇳🇦", "🇳🇷", "🇳🇵", "🇳🇱", "🇳🇨", "🇳🇿", "🇳🇮", "🇳🇪", "🇳🇬", "🇳🇺", "🇳🇫", "🇰🇵", "🇲🇰", "🇲🇵", "🇳🇴", "🇴🇲", "🇵🇰", "🇵🇼", "🇵🇸", "🇵🇦", "🇵🇬", "🇵🇾", "🇵🇪", "🇵🇭", "🇵🇳", "🇵🇱", "🇵🇹", "🇵🇷", "🇶🇦", "🇷🇪", "🇷🇴", "🇷🇺", "🇷🇼", "🇼🇸", "🇸🇲", "🇸🇹", "🇸🇦", "🇸🇳", "🇷🇸", "🇸🇨", "🇸🇱", "🇸🇬", "🇸🇽", "🇸🇰", "🇸🇮", "🇬🇸", "🇸🇧", "🇸🇴", "🇿🇦", "🇰🇷", "🇸🇸", "🇪🇸", "🇱🇰", "🇧🇱", "🇸🇭", "🇰🇳", "🇱🇨", "🇵🇲", "🇻🇨", "🇸🇩", "🇸🇷", "🇸🇪", "🇨🇭", "🇸🇾", "🇹🇼", "🇹🇯", "🇹🇿", "🇹🇭", "🇹🇱", "🇹🇬", "🇹🇰", "🇹🇴", "🇹🇹", "🇹🇳", "🇹🇷", "🇹🇲", "🇹🇨", "🇹🇻", "🇻🇮", "🇺🇬", "🇺🇦", "🇦🇪", "🇬🇧", "🇺🇸", "🇺🇾", "🇺🇿", "🇻🇺", "🇻🇦", "🇻🇪", "🇻🇳", "🇼🇫", "🇪🇭", "🇾🇪", "🇿🇲", "🇿🇼"
        )
    )

    init {
        try {
            emojiTypeface = Typeface.createFromAsset(context.assets, "emojifont.ttf")
        } catch (e: Exception) {
            // Fallback to system font
        }

        loadRecentlyUsed()

        try {
            binding = EmojiPickerBinding.inflate(LayoutInflater.from(context), this, true)
            binding?.let { b ->
                b.emojiRecycler.layoutManager = GridLayoutManager(context, 8)
                setupCategories()
                displayCategory("Smileys & Emotion")
                setupSearch()
                b.btnBackFromEmoji.setOnClickListener { onBackClickListener?.invoke() }
                isInitialized = true
            }
        } catch (e: Exception) {
            Log.e("EmojiPickerView", "Failed to inflate emoji picker: ${e.message}")
            isInitialized = false
        }
    }

    private fun loadRecentlyUsed() {
        val json = preferences.recentlyUsedEmojis
        try {
            val arr = JSONArray(json)
            recentlyUsed.clear()
            for (i in 0 until arr.length()) {
                recentlyUsed.add(arr.getString(i))
            }
        } catch (e: Exception) {
            recentlyUsed = mutableListOf()
        }
    }

    private fun saveRecentlyUsed() {
        val arr = JSONArray(recentlyUsed)
        preferences.recentlyUsedEmojis = arr.toString()
    }

    private fun addToRecentlyUsed(emoji: String) {
        val wasEmpty = recentlyUsed.isEmpty()
        recentlyUsed.remove(emoji)
        recentlyUsed.add(0, emoji)
        if (recentlyUsed.size > 40) {
            recentlyUsed.removeAt(recentlyUsed.size - 1)
        }
        saveRecentlyUsed()
        if (currentCategory == "Recently Used") {
            (binding?.emojiRecycler?.adapter as? EmojiAdapter)?.updateData(recentlyUsed)
        }
        if (wasEmpty) {
            setupCategories() // Only refresh if we need to show the "Recently Used" tab
        }
    }

    private fun setupSearch() {
        binding?.emojiSearch?.addTextChangedListener(object : android.text.TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {
                performSearch(s.toString())
            }
            override fun afterTextChanged(s: android.text.Editable?) {}
        })
    }

    private fun performSearch(query: String) {
        val currentList = if (currentCategory == "Recently Used") recentlyUsed else allEmojiCategories[currentCategory] ?: emptyList()
        val filtered = if (query.isEmpty()) {
            currentList
        } else {
            // Filter out non-emoji characters from query if any, though normally users type text
            currentList.filter { it.contains(query) }
        }
        (binding?.emojiRecycler?.adapter as? EmojiAdapter)?.updateData(filtered)
    }

    private fun setupCategories() {
        val b = binding ?: return
        b.emojiCategories.removeAllViews()
        val categories = mutableListOf<String>()
        if (recentlyUsed.isNotEmpty()) {
            categories.add("Recently Used")
        }
        categories.addAll(allEmojiCategories.keys)

        categories.forEach { category ->
            val tv = TextView(context).apply {
                text = category
                setPadding(16.dpToPx(), 0, 16.dpToPx(), 0)
                gravity = Gravity.CENTER
                textSize = 14f
                setTextColor(if (category == currentCategory) accentColor else Color.WHITE)
                setOnClickListener {
                    displayCategory(category)
                    setupCategories() // Update text colors
                }
                layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.MATCH_PARENT)
            }
            b.emojiCategories.addView(tv)
        }
    }

    private fun displayCategory(category: String) {
        val b = binding ?: return
        currentCategory = category
        val emojis = if (category == "Recently Used") recentlyUsed else allEmojiCategories[category] ?: emptyList()
        b.emojiRecycler.adapter = EmojiAdapter(emojis)
        b.emojiRecycler.scrollToPosition(0)
        b.emojiSearch.setText("")
    }

    fun setAccentColor(color: Int) {
        accentColor = color
        if (binding != null) {
            setupCategories()
        }
    }

    inner class EmojiAdapter(private var emojis: List<String>) : RecyclerView.Adapter<EmojiViewHolder>() {

        fun updateData(newEmojis: List<String>) {
            emojis = newEmojis
            notifyDataSetChanged()
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): EmojiViewHolder {
            val tv = object : androidx.appcompat.widget.AppCompatTextView(context) {
                override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                    super.onMeasure(widthMeasureSpec, widthMeasureSpec)
                }
            }.apply {
                layoutParams = ViewGroup.LayoutParams(
                    ViewGroup.LayoutParams.MATCH_PARENT,
                    ViewGroup.LayoutParams.WRAP_CONTENT
                )
                textSize = 28f
                gravity = Gravity.CENTER
                setPadding(4.dpToPx(), 4.dpToPx(), 4.dpToPx(), 4.dpToPx())
                emojiTypeface?.let { typeface = it }

                val ripple = RippleDrawable(
                    ColorStateList.valueOf(Color.parseColor("#3D3D3D")),
                    null,
                    ColorDrawable(Color.WHITE)
                )
                background = ripple
            }
            return EmojiViewHolder(tv)
        }

        override fun onBindViewHolder(holder: EmojiViewHolder, position: Int) {
            val emoji = emojis[position]
            (holder.itemView as TextView).text = emoji
            holder.itemView.setOnClickListener {
                onEmojiClickListener?.invoke(emoji)
                addToRecentlyUsed(emoji)
            }
        }

        override fun getItemCount(): Int = emojis.size
    }

    class EmojiViewHolder(view: View) : RecyclerView.ViewHolder(view)

    private fun Int.dpToPx(): Int = (this * context.resources.displayMetrics.density).toInt()
}
