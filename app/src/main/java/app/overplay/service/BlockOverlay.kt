// SPDX-License-Identifier: AGPL-3.0-or-later

package app.overplay.service

import android.accessibilityservice.AccessibilityService
import android.content.res.Configuration
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.text.InputFilter
import android.text.InputType
import android.text.method.PasswordTransformationMethod
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.view.inputmethod.EditorInfo
import android.widget.Button
import android.widget.CheckBox
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.ScrollView
import android.widget.TextView
import app.overplay.R
import app.overplay.data.OverplayRepository
import app.overplay.data.RuleEntity
import app.overplay.data.SecurityState
import app.overplay.data.SecurityStore
import app.overplay.data.VerifyResult
import app.overplay.domain.Limits
import app.overplay.domain.TimeText
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class BlockOverlay(
    private val context: AccessibilityService,
    private val rule: RuleEntity,
    private val usedMillis: Long,
    private val extraMillis: Long,
    private val securityStore: SecurityStore,
    private val repository: OverplayRepository,
    private val scope: CoroutineScope,
    private val onDismissed: () -> Unit,
    private val onExtensionGranted: () -> Unit,
    private val onOpenApp: () -> Unit,
    private val onGoHome: () -> Unit,
) {
    private val windowManager = context.getSystemService(WindowManager::class.java)
    private var root: View? = null
    private lateinit var codeInput: EditText
    private lateinit var showCode: CheckBox
    private lateinit var errorText: TextView
    private lateinit var recoveryText: TextView
    private lateinit var recoveryButton: Button
    private val extensionButtons = mutableListOf<Button>()
    private var busy = false
    private var recoveryReady = false

    private val night = context.resources.configuration.uiMode and
        Configuration.UI_MODE_NIGHT_MASK == Configuration.UI_MODE_NIGHT_YES
    private val backgroundColor = if (night) 0xFF101413.toInt() else 0xFFFFFBF7.toInt()
    private val ink = if (night) 0xFFE7EFEC.toInt() else 0xFF13201E.toInt()
    private val muted = if (night) 0xFFA5B8B4.toInt() else 0xFF51625E.toInt()
    private val accent = if (night) 0xFF8FD8CE.toInt() else 0xFF0F3D3E.toInt()
    private val onAccent = if (night) 0xFF06201F.toInt() else 0xFFF6E7C1.toInt()
    private val fieldColor = if (night) 0xFF1B2321.toInt() else 0xFFFFFFFF.toInt()
    private val errorColor = if (night) 0xFFFFB4AB.toInt() else 0xFFB3261E.toInt()

    fun show() {
        if (root != null) return
        val view = build()
        root = view
        val params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.TYPE_ACCESSIBILITY_OVERLAY,
            WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN or
                WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
            PixelFormat.TRANSLUCENT,
        ).apply {
            gravity = Gravity.CENTER
            softInputMode = WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE or
                WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_HIDDEN
            if (android.os.Build.VERSION.SDK_INT >= 30) {
                layoutInDisplayCutoutMode =
                    WindowManager.LayoutParams.LAYOUT_IN_DISPLAY_CUTOUT_MODE_ALWAYS
            }
        }
        windowManager.addView(view, params)
        scope.launch {
            val state = securityStore.state.first()
            withContext(Dispatchers.Main) { renderRecovery(state, System.currentTimeMillis()) }
        }
    }

    fun dismiss() {
        val view = root ?: return
        root = null
        runCatching { windowManager.removeView(view) }
        onDismissed()
    }

    private fun build(): View {
        val scroll = ScrollView(context).apply {
            isFillViewport = false
            setBackgroundColor(backgroundColor)
            fitsSystemWindows = true
        }
        val column = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(32), dp(24), dp(32))
        }
        scroll.addView(
            column,
            ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT),
        )

        column.addView(
            label(context.getString(R.string.block_title), 28f, ink, Typeface.BOLD),
            matchWidth(top = 0),
        )
        column.addView(
            label(rule.label, 18f, muted, Typeface.NORMAL),
            matchWidth(top = 4),
        )

        val total = rule.dailyLimitMillis + extraMillis
        val usedText = context.getString(
            R.string.block_used_of,
            TimeText.clock(usedMillis),
            TimeText.clock(total),
        )
        column.addView(label(usedText, 15f, ink, Typeface.NORMAL), matchWidth(top = 20))

        column.addView(
            ProgressBar(context, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = 100
                progress = ((usedMillis.toDouble() / total.coerceAtLeast(1)) * 100)
                    .toInt()
                    .coerceIn(0, 100)
                progressTintList = android.content.res.ColorStateList.valueOf(accent)
                progressBackgroundTintList =
                    android.content.res.ColorStateList.valueOf(fieldColor)
            },
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, dp(10)).apply {
                topMargin = dp(10)
            },
        )

        column.addView(
            label(
                context.getString(R.string.block_instruction),
                14f,
                muted,
                Typeface.NORMAL,
            ),
            matchWidth(top = 24),
        )

        codeInput = EditText(context).apply {
            hint = context.getString(R.string.block_code_hint)
            textSize = 16f
            setTextColor(ink)
            setHintTextColor(muted)
            inputType = InputType.TYPE_CLASS_NUMBER
            transformationMethod = PasswordTransformationMethod.getInstance()
            imeOptions = EditorInfo.IME_ACTION_DONE
            filters = arrayOf(
                InputFilter.LengthFilter(32),
                InputFilter { source, _, _, _, _, _ ->
                    source.filter { it.isDigit() }
                },
            )
            background = rounded(fieldColor, 14, accent, 1)
            setPadding(dp(16), dp(14), dp(16), dp(14))
            minHeight = dp(48)
        }
        column.addView(codeInput, matchWidth(top = 8))

        showCode = CheckBox(context).apply {
            text = context.getString(R.string.block_show_code)
            textSize = 13f
            setTextColor(muted)
            buttonTintList = android.content.res.ColorStateList.valueOf(accent)
            setOnCheckedChangeListener { _, checked ->
                codeInput.transformationMethod =
                    if (checked) null else PasswordTransformationMethod.getInstance()
            }
        }
        column.addView(showCode, matchWidth(top = 4))

        errorText = label("", 13f, errorColor, Typeface.NORMAL).apply {
            visibility = View.GONE
        }
        column.addView(errorText, matchWidth(top = 4))

        val extRow = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER
        }
        Limits.EXTENSIONS_MINUTES.forEach { minutes ->
            val button = Button(context).apply {
                text = context.getString(R.string.block_add_minutes, minutes)
                textSize = 14f
                isAllCaps = false
                minHeight = dp(48)
                setTextColor(onAccent)
                backgroundTintList = android.content.res.ColorStateList.valueOf(accent)
                setOnClickListener { addTime(minutes) }
            }
            extensionButtons += button
            extRow.addView(
                button,
                LinearLayout.LayoutParams(0, dp(48), 1f).apply {
                    marginEnd = dp(4)
                    marginStart = dp(4)
                },
            )
        }
        column.addView(extRow, matchWidth(top = 12))

        column.addView(
            divider(),
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                dp(1),
            ).apply { topMargin = dp(28) },
        )

        recoveryText = label("", 13f, muted, Typeface.NORMAL)
        column.addView(recoveryText, matchWidth(top = 16))
        recoveryButton = Button(context).apply {
            textSize = 13f
            isAllCaps = false
            setTextColor(accent)
            backgroundTintList =
                android.content.res.ColorStateList.valueOf(Color.TRANSPARENT)
            setOnClickListener { handleRecovery() }
        }
        column.addView(recoveryButton, buttonParams(top = 0, height = 48))

        val home = Button(context).apply {
            text = context.getString(R.string.block_go_home)
            textSize = 15f
            isAllCaps = false
            setTextColor(ink)
            background = rounded(fieldColor, 14, muted, 1)
            setOnClickListener { onGoHome() }
        }
        column.addView(home, buttonParams(top = 12, height = 52))

        return scroll
    }

    private fun addTime(minutes: Int) {
        if (busy) return
        val code = codeInput.text.toString().filter { it.isDigit() }
        if (code.length !in 16..32) {
            showError(context.getString(R.string.block_incomplete))
            return
        }
        setBusy(true)
        scope.launch {
            val now = System.currentTimeMillis()
            when (val result = securityStore.verify(code, now)) {
                VerifyResult.Success -> {
                    repository.addExtra(
                        rule.packageName,
                        TimeText.dayKey(now),
                        Limits.extensionMillis(minutes),
                    )
                    withContext(Dispatchers.Main) {
                        dismiss()
                        onExtensionGranted()
                    }
                }
                is VerifyResult.Wrong -> withContext(Dispatchers.Main) {
                    setBusy(false)
                    showError(
                        context.resources.getQuantityString(
                            R.plurals.block_wrong_attempts,
                            result.attemptsLeft,
                            result.attemptsLeft,
                        ),
                    )
                }
                is VerifyResult.Locked -> withContext(Dispatchers.Main) {
                    setBusy(false)
                    showError(
                        context.getString(
                            R.string.block_locked,
                            TimeText.countdown(result.untilMillis - now),
                        ),
                    )
                }
                VerifyResult.Expired -> {
                    val state = securityStore.state.first()
                    withContext(Dispatchers.Main) {
                        setBusy(false)
                        showError(context.getString(R.string.block_expired))
                        renderRecovery(state, now)
                    }
                }
                VerifyResult.NoCode -> withContext(Dispatchers.Main) {
                    setBusy(false)
                    showError(context.getString(R.string.block_no_code))
                }
            }
        }
    }

    private fun handleRecovery() {
        if (busy) return
        if (recoveryReady) {
            onOpenApp()
            return
        }
        setBusy(true)
        scope.launch {
            val now = System.currentTimeMillis()
            securityStore.startRecovery(now)
            val state = securityStore.state.first()
            withContext(Dispatchers.Main) {
                setBusy(false)
                renderRecovery(state, now)
            }
        }
    }

    private fun renderRecovery(state: SecurityState, now: Long) {
        recoveryReady = state.isRecoveryDue(now) || !state.hasCode
        when {
            state.isRecoveryDue(now) -> {
                recoveryText.text = context.getString(R.string.recovery_ready)
                recoveryButton.text = context.getString(R.string.recovery_open_app)
                recoveryButton.isEnabled = true
            }
            !state.hasCode -> {
                recoveryText.text = context.getString(R.string.recovery_no_code)
                recoveryButton.text = context.getString(R.string.recovery_open_app)
                recoveryButton.isEnabled = true
            }
            state.recoveryDueAt != null -> {
                recoveryText.text = context.getString(
                    R.string.recovery_active,
                    TimeText.countdown(state.recoveryDueAt - now),
                )
                recoveryButton.text = context.getString(R.string.recovery_active_button)
                recoveryButton.isEnabled = false
            }
            else -> {
                recoveryText.text = context.getString(R.string.recovery_offer)
                recoveryButton.text = context.getString(R.string.recovery_start)
                recoveryButton.isEnabled = true
            }
        }
    }

    private fun setBusy(value: Boolean) {
        busy = value
        codeInput.isEnabled = !value
        showCode.isEnabled = !value
        extensionButtons.forEach { it.isEnabled = !value }
    }

    private fun showError(message: String) {
        errorText.text = message
        errorText.visibility = View.VISIBLE
    }

    private fun label(text: String, size: Float, color: Int, style: Int) =
        TextView(context).apply {
            this.text = text
            textSize = size
            setTextColor(color)
            typeface = Typeface.create(Typeface.SANS_SERIF, style)
            gravity = Gravity.CENTER
        }

    private fun divider() = View(context).apply {
        setBackgroundColor(muted)
        alpha = 0.25f
    }

    private fun rounded(color: Int, radiusDp: Int, strokeColor: Int, strokeDp: Int) =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            setColor(color)
            cornerRadius = dp(radiusDp).toFloat()
            setStroke(dp(strokeDp), strokeColor)
        }

    private fun matchWidth(top: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        ViewGroup.LayoutParams.WRAP_CONTENT,
    ).apply {
        topMargin = dp(top)
    }

    private fun buttonParams(top: Int, height: Int) = LinearLayout.LayoutParams(
        ViewGroup.LayoutParams.MATCH_PARENT,
        dp(height),
    ).apply {
        topMargin = dp(top)
    }

    private fun dp(value: Int): Int =
        (value * context.resources.displayMetrics.density).toInt()
}
