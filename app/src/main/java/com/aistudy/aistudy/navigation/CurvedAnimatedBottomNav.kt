package com.aistudy.aistudy.navigation

import android.animation.ArgbEvaluator
import android.animation.ValueAnimator
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.Path
import android.graphics.Typeface
import android.graphics.drawable.Drawable
import android.util.AttributeSet
import android.view.ActionProvider
import android.view.ContextMenu
import android.view.Gravity
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewOutlineProvider
import android.view.animation.OvershootInterpolator
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.interpolator.view.animation.FastOutSlowInInterpolator
import com.aistudy.aistudy.R
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Modern animated curved bottom navigation bar inspired by Reference Image 1.
 * Features:
 * - Concave scooped notch (.nav2-notch-svg: 92dp x 30dp)
 * - Animated sliding bubble indicator (.nav2-bubble-icon: 48dp circle with elevated primary tone)
 * - 5 native navigation tabs with smooth icon elevation and text color interpolation
 */
class CurvedAnimatedBottomNav @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {

    data class TabDefinition(
        val id: Int,
        val iconRes: Int,
        val label: String
    )

    companion object {
        val TABS = listOf(
            TabDefinition(R.id.nav_home, R.drawable.ic_nav_home, "Trang chủ"),
            TabDefinition(R.id.nav_ask, R.drawable.ic_nav_ask, "Hỏi đáp"),
            TabDefinition(R.id.nav_playground, R.drawable.ic_nav_playground, "Sân chơi"),
            TabDefinition(R.id.nav_history, R.drawable.ic_nav_history, "Lịch sử"),
            TabDefinition(R.id.nav_progress, R.drawable.ic_nav_progress, "Tiến độ")
        )
    }

    private var onItemSelectedListener: ((MenuItem) -> Boolean)? = null

    private val density = resources.displayMetrics.density
    private fun dp(v: Float): Float = v * density
    private fun dpInt(v: Float): Int = (v * density).roundToInt()

    // Dimensions matching Reference Image 1
    private val barTop = dp(14f)
    private val notchWidth = dp(92f)
    private val notchHalf = notchWidth / 2f
    private val notchDepth = dp(28f)
    private val bubbleDiameter = dp(48f)
    private val bubbleRadius = bubbleDiameter / 2f

    // Paints
    private val backgroundPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.FILL
    }
    private val strokePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeWidth = dp(1f)
    }

    // Dynamic Notch State
    private var notchCenterX = 0f
    private var currentTabIndex = -1
    private var isFirstLayout = true
    private var activeAnimator: ValueAnimator? = null
    private val argbEvaluator = ArgbEvaluator()

    // Child Views
    private val tabsContainer: LinearLayout
    private val bubbleContainer: FrameLayout
    private val bubbleIcon: ImageView
    private val tabItemViews = mutableListOf<TabItemView>()

    private class TabItemView(
        val root: FrameLayout,
        val iconView: ImageView,
        val labelView: TextView,
        val iconRes: Int
    )

    init {
        setWillNotDraw(false)
        clipChildren = false
        clipToPadding = false

        updateColors()

        // 1. Horizontal container for the 5 tabs
        tabsContainer = LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT).apply {
                topMargin = dpInt(14f)
            }
            weightSum = TABS.size.toFloat()
            clipChildren = false
        }
        addView(tabsContainer)

        // Populate tabs
        for ((index, tab) in TABS.withIndex()) {
            val tabView = createTabView(index, tab)
            tabsContainer.addView(tabView.root)
            tabItemViews.add(tabView)
        }

        // 2. Elevated Floating Bubble (.nav2-bubble-icon)
        bubbleContainer = FrameLayout(context).apply {
            layoutParams = LayoutParams(bubbleDiameter.toInt(), bubbleDiameter.toInt()).apply {
                topMargin = dpInt(2f) // Floats partly above the barTop (14dp)
            }
            setBackgroundResource(R.drawable.bg_nav_bubble)
            elevation = dp(6f)
            outlineProvider = ViewOutlineProvider.BACKGROUND
            clipToOutline = true
        }

        bubbleIcon = ImageView(context).apply {
            layoutParams = LayoutParams(dpInt(24f), dpInt(24f), Gravity.CENTER)
            setImageResource(TABS[0].iconRes)
            setColorFilter(ContextCompat.getColor(context, R.color.answer_on_primary))
        }
        bubbleContainer.addView(bubbleIcon)
        addView(bubbleContainer)
    }

    private fun updateColors() {
        backgroundPaint.color = ContextCompat.getColor(context, R.color.answer_surface)
        strokePaint.color = ContextCompat.getColor(context, R.color.answer_divider)
    }

    private fun createTabView(index: Int, tab: TabDefinition): TabItemView {
        val root = FrameLayout(context).apply {
            layoutParams = LinearLayout.LayoutParams(0, LayoutParams.MATCH_PARENT, 1f)
            setBackgroundResource(android.R.drawable.list_selector_background)
            isClickable = true
            isFocusable = true
        }

        val content = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            layoutParams = LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT, Gravity.CENTER_HORIZONTAL).apply {
                topMargin = dpInt(10f)
            }
        }

        val icon = ImageView(context).apply {
            layoutParams = LinearLayout.LayoutParams(dpInt(22f), dpInt(22f)).apply {
                gravity = Gravity.CENTER_HORIZONTAL
            }
            setImageResource(tab.iconRes)
            setColorFilter(ContextCompat.getColor(context, R.color.answer_foreground_secondary))
        }

        val label = TextView(context).apply {
            layoutParams = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply {
                topMargin = dpInt(2f)
                gravity = Gravity.CENTER_HORIZONTAL
            }
            text = tab.label
            textSize = 11f
            setTextColor(ContextCompat.getColor(context, R.color.answer_foreground_secondary))
            typeface = Typeface.DEFAULT
            gravity = Gravity.CENTER
            isSingleLine = true
        }

        content.addView(icon)
        content.addView(label)
        root.addView(content)

        root.setOnClickListener {
            selectTab(index, animate = true, notifyListener = true)
        }

        return TabItemView(root, icon, label, tab.iconRes)
    }

    override fun onSizeChanged(w: Int, h: Int, oldw: Int, oldh: Int) {
        super.onSizeChanged(w, h, oldw, oldh)
        if (w > 0) {
            val safeIndex = if (currentTabIndex in TABS.indices) currentTabIndex else 0
            val tabWidth = w.toFloat() / TABS.size
            notchCenterX = (safeIndex + 0.5f) * tabWidth
            bubbleContainer.translationX = notchCenterX - bubbleRadius
            if (isFirstLayout) {
                isFirstLayout = false
                applyTabState(safeIndex, 1f)
            }
            invalidate()
        }
    }

    override fun onDraw(canvas: Canvas) {
        super.onDraw(canvas)
        val w = width.toFloat()
        val h = height.toFloat()
        if (w <= 0 || h <= 0) return

        updateColors()

        val left = notchCenterX - notchHalf
        val right = notchCenterX + notchHalf

        // Background solid fill path with smooth scooped notch
        val path = Path().apply {
            moveTo(0f, barTop)
            if (left > 0f) {
                lineTo(left, barTop)
            }
            // Left curve
            cubicTo(
                left + dp(18f), barTop,
                notchCenterX - dp(20f), barTop + notchDepth,
                notchCenterX, barTop + notchDepth
            )
            // Right curve
            cubicTo(
                notchCenterX + dp(20f), barTop + notchDepth,
                right - dp(18f), barTop,
                right, barTop
            )
            lineTo(w, barTop)
            lineTo(w, h)
            lineTo(0f, h)
            close()
        }
        canvas.drawPath(path, backgroundPaint)

        // Top outline stroke
        val strokePath = Path().apply {
            moveTo(0f, barTop)
            if (left > 0f) {
                lineTo(left, barTop)
            }
            cubicTo(
                left + dp(18f), barTop,
                notchCenterX - dp(20f), barTop + notchDepth,
                notchCenterX, barTop + notchDepth
            )
            cubicTo(
                notchCenterX + dp(20f), barTop + notchDepth,
                right - dp(18f), barTop,
                right, barTop
            )
            lineTo(w, barTop)
        }
        canvas.drawPath(strokePath, strokePaint)
    }

    /**
     * Programmatically select a tab by item ID (e.g. R.id.nav_home).
     */
    var selectedItemId: Int
        get() = if (currentTabIndex in TABS.indices) TABS[currentTabIndex].id else TABS[0].id
        set(value) {
            val index = TABS.indexOfFirst { it.id == value }
            if (index != -1) {
                if (index != currentTabIndex || currentTabIndex == -1) {
                    selectTab(index, animate = !isFirstLayout && currentTabIndex != -1, notifyListener = true)
                }
            }
        }

    fun setOnItemSelectedListener(listener: (MenuItem) -> Boolean) {
        this.onItemSelectedListener = listener
    }

    private fun selectTab(targetIndex: Int, animate: Boolean, notifyListener: Boolean) {
        if (targetIndex !in TABS.indices) return

        if (targetIndex == currentTabIndex && !isFirstLayout) {
            // Little playful bounce if already on the selected tab
            bubbleContainer.animate()
                .scaleX(1.15f)
                .scaleY(1.15f)
                .setDuration(120)
                .withEndAction {
                    bubbleContainer.animate()
                        .scaleX(1f)
                        .scaleY(1f)
                        .setDuration(120)
                        .start()
                }.start()
            return
        }

        val previousIndex = currentTabIndex
        currentTabIndex = targetIndex

        if (notifyListener) {
            val menuItem = CurvedMenuItem(TABS[targetIndex].id, TABS[targetIndex].label)
            val handled = onItemSelectedListener?.invoke(menuItem) ?: true
            if (!handled) {
                currentTabIndex = previousIndex
                return
            }
        }

        if (!animate || width == 0) {
            val tabWidth = if (width > 0) width.toFloat() / TABS.size else 0f
            notchCenterX = (targetIndex + 0.5f) * tabWidth
            bubbleContainer.translationX = notchCenterX - bubbleRadius
            bubbleIcon.setImageResource(TABS[targetIndex].iconRes)
            applyTabState(targetIndex, 1f)
            invalidate()
            return
        }

        val tabWidth = width.toFloat() / TABS.size
        val startX = notchCenterX
        val targetX = (targetIndex + 0.5f) * tabWidth

        activeAnimator?.cancel()

        val primaryColor = ContextCompat.getColor(context, R.color.answer_primary)
        val secondaryColor = ContextCompat.getColor(context, R.color.answer_foreground_secondary)

        var iconSwapped = false

        activeAnimator = ValueAnimator.ofFloat(0f, 1f).apply {
            duration = 320L
            interpolator = OvershootInterpolator(0.5f)
            addUpdateListener { animator ->
                val fraction = animator.animatedValue as Float
                notchCenterX = startX + (targetX - startX) * fraction
                bubbleContainer.translationX = notchCenterX - bubbleRadius

                // Micro-interaction: transit squash and spring-back
                val transitSquash = 1f - 0.12f * sin(Math.PI * fraction).toFloat()
                bubbleContainer.scaleX = transitSquash
                bubbleContainer.scaleY = transitSquash

                // Mid-flight icon crossfade
                if (fraction >= 0.45f && !iconSwapped) {
                    bubbleIcon.setImageResource(TABS[targetIndex].iconRes)
                    iconSwapped = true
                }

                // Smoothly fade inactive/active icons and interpolate label colors
                for (i in tabItemViews.indices) {
                    val itemView = tabItemViews[i]
                    if (i == targetIndex) {
                        // Rising into bubble: row icon fades out
                        itemView.iconView.alpha = 1f - fraction
                        itemView.iconView.scaleX = 1f - 0.4f * fraction
                        itemView.iconView.scaleY = 1f - 0.4f * fraction
                        val c = argbEvaluator.evaluate(fraction, secondaryColor, primaryColor) as Int
                        itemView.labelView.setTextColor(c)
                        if (fraction > 0.7f) {
                            itemView.labelView.typeface = Typeface.DEFAULT_BOLD
                        }
                    } else if (i == previousIndex) {
                        // Returning to row: row icon fades in
                        itemView.iconView.alpha = fraction
                        itemView.iconView.scaleX = 0.6f + 0.4f * fraction
                        itemView.iconView.scaleY = 0.6f + 0.4f * fraction
                        val c = argbEvaluator.evaluate(fraction, primaryColor, secondaryColor) as Int
                        itemView.labelView.setTextColor(c)
                        if (fraction > 0.7f) {
                            itemView.labelView.typeface = Typeface.DEFAULT
                        }
                    } else {
                        itemView.iconView.alpha = 1f
                        itemView.iconView.scaleX = 1f
                        itemView.iconView.scaleY = 1f
                        itemView.labelView.setTextColor(secondaryColor)
                        itemView.labelView.typeface = Typeface.DEFAULT
                    }
                }

                invalidate()
            }
            start()
        }
    }

    private fun applyTabState(activeIdx: Int, activeWeight: Float) {
        val primaryColor = ContextCompat.getColor(context, R.color.answer_primary)
        val secondaryColor = ContextCompat.getColor(context, R.color.answer_foreground_secondary)

        for (i in tabItemViews.indices) {
            val itemView = tabItemViews[i]
            if (i == activeIdx) {
                itemView.iconView.alpha = 0f
                itemView.labelView.setTextColor(primaryColor)
                itemView.labelView.typeface = Typeface.DEFAULT_BOLD
            } else {
                itemView.iconView.alpha = 1f
                itemView.iconView.scaleX = 1f
                itemView.iconView.scaleY = 1f
                itemView.labelView.setTextColor(secondaryColor)
                itemView.labelView.typeface = Typeface.DEFAULT
            }
        }
    }

    /**
     * Lightweight MenuItem implementation to ensure 100% backward compatibility
     * with existing setOnItemSelectedListener { item -> when (item.itemId) ... }
     */
    private class CurvedMenuItem(
        private val id: Int,
        private val titleStr: CharSequence?
    ) : MenuItem {
        override fun getItemId(): Int = id
        override fun getTitle(): CharSequence? = titleStr
        override fun getTitleCondensed(): CharSequence? = titleStr
        override fun isEnabled(): Boolean = true
        override fun isVisible(): Boolean = true
        override fun isChecked(): Boolean = false
        override fun isCheckable(): Boolean = false
        override fun getGroupId(): Int = 0
        override fun getOrder(): Int = 0
        override fun setTitle(title: CharSequence?): MenuItem = this
        override fun setTitle(title: Int): MenuItem = this
        override fun setTitleCondensed(title: CharSequence?): MenuItem = this
        override fun setIcon(icon: Drawable?): MenuItem = this
        override fun setIcon(iconRes: Int): MenuItem = this
        override fun getIcon(): Drawable? = null
        override fun setIntent(intent: Intent?): MenuItem = this
        override fun getIntent(): Intent? = null
        override fun setShortcut(numericChar: Char, alphaChar: Char): MenuItem = this
        override fun setNumericShortcut(numericChar: Char): MenuItem = this
        override fun getNumericShortcut(): Char = '0'
        override fun setAlphabeticShortcut(alphaChar: Char): MenuItem = this
        override fun getAlphabeticShortcut(): Char = 'a'
        override fun setCheckable(checkable: Boolean): MenuItem = this
        override fun setChecked(checked: Boolean): MenuItem = this
        override fun setVisible(visible: Boolean): MenuItem = this
        override fun setEnabled(enabled: Boolean): MenuItem = this
        override fun hasSubMenu(): Boolean = false
        override fun getSubMenu(): SubMenu? = null
        override fun setOnMenuItemClickListener(menuItemClickListener: MenuItem.OnMenuItemClickListener?): MenuItem = this
        override fun getMenuInfo(): ContextMenu.ContextMenuInfo? = null
        override fun setShowAsAction(actionEnum: Int) {}
        override fun setShowAsActionFlags(actionEnum: Int): MenuItem = this
        override fun setActionView(view: View?): MenuItem = this
        override fun setActionView(resId: Int): MenuItem = this
        override fun getActionView(): View? = null
        override fun setActionProvider(actionProvider: ActionProvider?): MenuItem = this
        override fun getActionProvider(): ActionProvider? = null
        override fun expandActionView(): Boolean = false
        override fun collapseActionView(): Boolean = false
        override fun isActionViewExpanded(): Boolean = false
        override fun setOnActionExpandListener(listener: MenuItem.OnActionExpandListener?): MenuItem = this
    }
}
