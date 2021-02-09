package com.akexorcist.snaptimepicker

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import androidx.annotation.ColorRes
import androidx.annotation.StringRes
import androidx.core.content.ContextCompat
import androidx.core.view.updatePaddingRelative
import androidx.fragment.app.viewModels
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.LinearSnapHelper
import androidx.recyclerview.widget.RecyclerView
import com.akexorcist.snaptimepicker.databinding.LayoutSnapTimePickerDialogBinding
import com.akexorcist.snaptimepicker.extension.SnapTimePickerViewModel


@Suppress("unused")
class SnapTimePickerDialog : BaseSnapTimePickerDialogFragment() {
    private val binding: LayoutSnapTimePickerDialogBinding by lazy {
        LayoutSnapTimePickerDialogBinding.inflate(LayoutInflater.from(requireContext()))
    }

    private val sharedViewModel: SnapTimePickerViewModel by viewModels(ownerProducer = { requireParentFragment() })

    private lateinit var hourAdapter: TimePickerAdapter
    private lateinit var minuteAdapter: TimePickerAdapter
    private lateinit var hourLayoutManager: CenterLinearLayoutManager
    private lateinit var minuteLayoutManager: CenterLinearLayoutManager
    private lateinit var hourSnapHelper: LinearSnapHelper
    private lateinit var minuteSnapHelper: LinearSnapHelper

    private lateinit var hourList: List<Int>
    private lateinit var minuteList: List<Int>

    private var selectableTimeRange: TimeRange? = null
    private var preselectedTime: TimeValue? = null
    private var isUseViewModel = false
    private var title: Int = -1
    private var prefix: Int = -1
    private var suffix: Int = -1
    private var titleColor: Int = -1
    private var themeColor: Int = -1
    private var timeInterval: Int = 1
    private var listener: Listener? = null

    private var lastSelectedHour = -1
    private var lastSelectedMinute = -1

    companion object {
        private const val EXTRA_SELECTABLE_TIME_RANGE =
            "com.akexorcist.snaptimepicker.selectable_time_range"
        private const val EXTRA_PRESELECTED_TIME = "com.akexorcist.snaptimepicker.preselected_time"
        private const val EXTRA_SELECTED_HOUR = "com.akexorcist.snaptimepicker.selected_hour"
        private const val EXTRA_SELECTED_MINUTE = "com.akexorcist.snaptimepicker.selected_minute"
        private const val EXTRA_IS_USE_VIEW_MODEL =
            "com.akexorcist.snaptimepicker.is_use_view_model"
        private const val EXTRA_TITLE = "com.akexorcist.snaptimepicker.title"
        private const val EXTRA_SUFFIX = "com.akexorcist.snaptimepicker.suffix"
        private const val EXTRA_PREFIX = "com.akexorcist.snaptimepicker.prefix"
        private const val EXTRA_TITLE_COLOR = "com.akexorcist.snaptimepicker.title_color"
        private const val EXTRA_THEME_COLOR = "com.akexorcist.snaptimepicker.theme_color"
        private const val EXTRA_TIME_INTERVAL = "com.akexorcist.snaptimepicker.time_interval"
        private const val MIN_HOUR = 0
        private const val MAX_HOUR = 23
        private const val MIN_MINUTE = 0
        private const val MAX_MINUTE = 59
        private const val MINUTE_IN_HOUR = 60
        private const val UPDATE_PRE_SELECTED_START_TIME = 100L
        const val TAG = "SnapTimePickerDialog"

        private fun newInstance(
                selectableTimeRange: TimeRange?,
                preselectedTime: TimeValue?,
                isUseViewModel: Boolean,
                title: Int,
                prefix: Int,
                suffix: Int,
                titleColor: Int,
                themeColor: Int,
                timeInterval: Int
        ): SnapTimePickerDialog = SnapTimePickerDialog().apply {
            isCancelable = true
            arguments = Bundle().apply {
                putParcelable(EXTRA_SELECTABLE_TIME_RANGE, selectableTimeRange)
                putParcelable(EXTRA_PRESELECTED_TIME, preselectedTime)
                putBoolean(EXTRA_IS_USE_VIEW_MODEL, isUseViewModel)
                putInt(EXTRA_TITLE, title)
                putInt(EXTRA_PREFIX, prefix)
                putInt(EXTRA_SUFFIX, suffix)
                putInt(EXTRA_TITLE_COLOR, titleColor)
                putInt(EXTRA_THEME_COLOR, themeColor)
                putInt(EXTRA_TIME_INTERVAL, timeInterval)
            }
        }
    }

    override fun setupLayoutView(): View = binding.root

    override fun prepare() {
        run {
            hourAdapter = TimePickerAdapter()
            minuteAdapter = TimePickerAdapter()
            hourLayoutManager = CenterLinearLayoutManager(requireContext())
            minuteLayoutManager = CenterLinearLayoutManager(requireContext())
            hourSnapHelper = LinearSnapHelper()
            minuteSnapHelper = LinearSnapHelper()
            binding.recyclerViewHour.layoutManager = hourLayoutManager
            binding.recyclerViewHour.adapter = hourAdapter
            hourSnapHelper.attachToRecyclerView(binding.recyclerViewHour)
            binding.recyclerViewMinute.layoutManager = minuteLayoutManager
            binding.recyclerViewMinute.adapter = minuteAdapter
            minuteSnapHelper.attachToRecyclerView(binding.recyclerViewMinute)
            if (title != -1) {
                binding.textViewTitle.text = getString(title)
            }
            if (prefix != -1) {
                binding.textViewTimePrefix.text = getString(prefix)
            }
            if (suffix != -1) {
                binding.textViewTimeSuffix.text = getString(suffix)
            }
            if (titleColor != -1) {
                context?.let { context ->
                    binding.textViewTitle.setTextColor(
                            ContextCompat.getColor(
                                    context,
                                    titleColor
                            )
                    )
                }
            }
            if (themeColor != -1) {
                context?.let { context ->
                    binding.buttonConfirm.setTextColor(ContextCompat.getColor(context, themeColor))
                    // binding.buttonCancel.setTextColor(ContextCompat.getColor(context, themeColor))
                    binding.textViewTitle.setBackgroundColor(
                            ContextCompat.getColor(context, themeColor)
                    )
                }
            }
            run {
                binding.buttonConfirm.setOnClickListener { onConfirmClick() }
                // binding.buttonCancel.setOnClickListener { onCancelClick() }
                binding.recyclerViewHour.addOnScrollListener(hourScrollListener)
                binding.recyclerViewMinute.addOnScrollListener(minuteScrollListener)
            }
            run {
                resetPreselectedTimeWhenNeed()
                setupTimePicker()
                if (isUseViewModel) {
                    useLiveDataAsCallback()
                }
            }
        }
    }

    override fun restoreArgument(bundle: Bundle?) {
        selectableTimeRange = bundle?.getParcelable(EXTRA_SELECTABLE_TIME_RANGE)
        preselectedTime = bundle?.getParcelable(EXTRA_PRESELECTED_TIME)
        isUseViewModel = bundle?.getBoolean(EXTRA_IS_USE_VIEW_MODEL) ?: false
        title = bundle?.getInt(EXTRA_TITLE, -1) ?: -1
        prefix = bundle?.getInt(EXTRA_PREFIX, -1) ?: -1
        suffix = bundle?.getInt(EXTRA_SUFFIX, -1) ?: -1
        themeColor = bundle?.getInt(EXTRA_THEME_COLOR, -1) ?: -1
        titleColor = bundle?.getInt(EXTRA_TITLE_COLOR, -1) ?: -1
        timeInterval = bundle?.getInt(EXTRA_TIME_INTERVAL, 1) ?: 1
    }

    override fun initialize() {
        setupPreselectedTime()
    }

    override fun restoreInstanceState(savedInstanceState: Bundle?) {
        selectableTimeRange = savedInstanceState?.getParcelable(EXTRA_SELECTABLE_TIME_RANGE)
        preselectedTime = savedInstanceState?.getParcelable(EXTRA_PRESELECTED_TIME)
        lastSelectedHour = savedInstanceState?.getInt(EXTRA_SELECTED_HOUR, -1) ?: -1
        lastSelectedMinute = savedInstanceState?.getInt(EXTRA_SELECTED_MINUTE, -1) ?: -1
        isUseViewModel = savedInstanceState?.getBoolean(EXTRA_IS_USE_VIEW_MODEL) ?: false
        title = savedInstanceState?.getInt(EXTRA_TITLE, -1) ?: -1
        prefix = savedInstanceState?.getInt(EXTRA_PREFIX, -1) ?: -1
        suffix = savedInstanceState?.getInt(EXTRA_SUFFIX, -1) ?: -1
        themeColor = savedInstanceState?.getInt(EXTRA_THEME_COLOR, -1) ?: -1
        titleColor = savedInstanceState?.getInt(EXTRA_TITLE_COLOR, -1) ?: -1
        timeInterval = savedInstanceState?.getInt(EXTRA_TIME_INTERVAL, 1) ?: 1
    }

    override fun restore() {
        setupPreselectedTime(TimeValue(lastSelectedHour, lastSelectedMinute))
        updateSelectableTime(lastSelectedHour, lastSelectedMinute)
    }

    override fun saveInstanceState(outState: Bundle?) {
        outState?.putParcelable(EXTRA_SELECTABLE_TIME_RANGE, selectableTimeRange)
        outState?.putParcelable(EXTRA_PRESELECTED_TIME, preselectedTime)
        outState?.putInt(EXTRA_SELECTED_HOUR, lastSelectedHour)
        outState?.putInt(EXTRA_SELECTED_MINUTE, lastSelectedMinute)
        outState?.putBoolean(EXTRA_IS_USE_VIEW_MODEL, isUseViewModel)
        outState?.putInt(EXTRA_TITLE, title)
        outState?.putInt(EXTRA_PREFIX, prefix)
        outState?.putInt(EXTRA_SUFFIX, suffix)
        outState?.putInt(EXTRA_THEME_COLOR, themeColor)
        outState?.putInt(EXTRA_TITLE_COLOR, titleColor)
        outState?.putInt(EXTRA_TIME_INTERVAL, timeInterval)
    }

    override fun setup() {}

    override fun onDestroy() {
        super.onDestroy()
        binding.recyclerViewHour.removeOnScrollListener(hourScrollListener)
        binding.recyclerViewMinute.removeOnScrollListener(minuteScrollListener)
    }

    private val hourScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                var currentSelectedHour = -1
                val hourSnappedView = hourSnapHelper.findSnapView(hourLayoutManager)
                hourSnappedView?.let { view ->
                    currentSelectedHour =
                        hourAdapter.getValueByPosition(hourLayoutManager.getPosition(view))
                }
                updateSelectableTime(currentSelectedHour, lastSelectedMinute)
                lastSelectedHour = currentSelectedHour
            }
        }
    }

    private val minuteScrollListener = object : RecyclerView.OnScrollListener() {
        override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
            super.onScrollStateChanged(recyclerView, newState)
            if (newState == RecyclerView.SCROLL_STATE_IDLE) {
                var currentSelectedMinute = -1
                val minuteSnappedView = minuteSnapHelper.findSnapView(minuteLayoutManager)
                minuteSnappedView?.let { view ->
                    currentSelectedMinute =
                        minuteAdapter.getValueByPosition(minuteLayoutManager.getPosition(view))
                }
                updateSelectableTime(lastSelectedHour, currentSelectedMinute)
                lastSelectedMinute = currentSelectedMinute
            }
        }
    }

    fun setListener(listener: Listener?) {
        this.listener = listener
    }

    fun setListener(onTimePicked: (hour: Int, minute: Int) -> Unit) {
        this.listener = object : Listener {
            override fun onTimePicked(hour: Int, minute: Int) {
                onTimePicked(hour, minute)
            }
        }
    }

    fun isUseViewModel(): Boolean = isUseViewModel

    fun getTitleResourceId(): Int = title

    fun getTitle(): String = getString(title)

    fun getPrefixResourceId(): Int = prefix

    fun getPrefix(): String = getString(prefix)

    fun getSuffixResourceId(): Int = suffix

    fun getSuffix(): String = getString(suffix)

    fun getTitleColorResourceId(): Int = titleColor

    fun getTitleColor(): Int = context?.let { context ->
        ContextCompat.getColor(context, titleColor)
    } ?: -1

    fun getThemeColorResourceId(): Int = themeColor

    fun getThemeColor(): Int = context?.let { context ->
        ContextCompat.getColor(context, themeColor)
    } ?: -1

    private fun updateSelectableTime(currentSelectedHour: Int, currentSelectedMinute: Int) {
        if (currentSelectedHour != -1 && currentSelectedMinute != -1) {
            if (currentSelectedHour == selectableTimeRange?.start?.hour) {
                val startMinute = selectableTimeRange?.start?.minute ?: -1
                val endMinute = MAX_MINUTE
                updateMinuteListWithRange(startMinute, endMinute)
                val minutePosition = minuteAdapter.getPositionByValue(currentSelectedMinute)
                if (currentSelectedMinute < startMinute) {
                    updateMinutePosition(minutePosition)
                }
            } else if (currentSelectedHour == selectableTimeRange?.end?.hour) {
                val startMinute = MIN_MINUTE
                val endMinute = selectableTimeRange?.end?.minute ?: -1
                updateMinuteListWithRange(startMinute, endMinute)
                if (currentSelectedMinute > endMinute) {
                    updateMinutePosition(currentSelectedMinute)
                }
            } else if (currentSelectedHour != selectableTimeRange?.start?.hour &&
                currentSelectedHour != selectableTimeRange?.end?.hour &&
                minuteList.size < MAX_MINUTE + 1
            ) {
                initMinuteList(true)
            }
        }
    }

    private fun setupTimePicker() {
        iniHourList()
        initMinuteList(false)
    }

    private fun iniHourList() {
        val startHour = selectableTimeRange?.start?.hour ?: -1
        val endHour = selectableTimeRange?.end?.hour ?: -1
        this.hourList = listOf()
        if (isEarlierSelectableTime()) {
            for (index in MIN_HOUR..MAX_HOUR) {
                if (startHour != -1 && endHour != -1) {
                    if (index in startHour..endHour) {
                        hourList = hourList + index
                    }
                } else {
                    hourList = hourList + index
                }
            }
        } else if (isLaterSelectableTime()) {
            if (startHour != -1 && endHour != -1) {
                for (index in startHour..MAX_HOUR) {
                    if (index in startHour..MAX_HOUR || index in MIN_HOUR..endHour) {
                        hourList = hourList + index
                    }
                }
                for (index in MIN_HOUR..endHour) {
                    if (index in startHour..MAX_HOUR || index in MIN_HOUR..endHour) {
                        hourList = hourList + index
                    }
                }
            } else {
                for (index in MIN_HOUR..MAX_HOUR) {
                    hourList = hourList + index
                }
            }
        } else {
            if (startHour != -1 && endHour != -1) {
                hourList = hourList + startHour
            } else {
                for (index in MIN_HOUR..MAX_HOUR) {
                    hourList = hourList + index
                }
            }
        }
        hourAdapter.setItemList(hourList)
    }

    private fun initMinuteList(includeAll: Boolean) {
        this.minuteList = listOf()
        for (index in MIN_MINUTE until (MINUTE_IN_HOUR / timeInterval)) {
            minuteList = minuteList + index * this.timeInterval
        }
        minuteAdapter.setItemList(minuteList)
        if (!includeAll && preselectedTime != null &&
            isInTimeRange(preselectedTime, selectableTimeRange)
        ) {
            preselectedTime?.let { time ->
                updateSelectableTime(time.hour, time.minute)
            }
        }
    }

    private fun isInTimeRange(time: TimeValue?, timeRange: TimeRange?): Boolean {
        val startHour = timeRange?.start?.hour ?: -1
        val startMinute = timeRange?.start?.minute ?: -1
        val endHour = timeRange?.end?.hour ?: -1
        val endMinute = timeRange?.end?.minute ?: -1
        val expectHour = time?.hour ?: -1
        val expectMinute = time?.minute ?: -1
        if (time == null || timeRange == null ||
            startHour == -1 || startMinute == -1 ||
            endHour == -1 || endMinute == -1 ||
            expectHour == -1 || expectMinute == -1
        ) {
            return false
        }
        return if (startHour < endHour || (startHour == endHour && startMinute < endMinute)) {
            (expectHour in (startHour + 1) until endHour) ||
                    (expectHour == startHour && expectMinute >= startMinute) ||
                    (expectHour == endHour && expectMinute <= endMinute)
        } else if (startHour > endHour || (startHour == endHour && startHour > endHour)) {
            (expectHour in (endHour + 1) until startHour) ||
                    (expectHour == endHour && expectMinute >= endMinute) ||
                    (expectHour == startHour && expectMinute <= startMinute)
        } else {
            expectHour == startHour && expectMinute == startMinute
        }
    }

    private fun updateMinuteListWithRange(startMinute: Int, endMinute: Int) {
        this.minuteList = listOf()
        for (index in MIN_MINUTE..MAX_MINUTE) {
            if (startMinute != -1 && endMinute != -1) {
                if (index in startMinute..endMinute) {
                    minuteList = minuteList + index
                }
            } else {
                minuteList = minuteList + index
            }
        }
        minuteAdapter.setItemList(minuteList)
    }

    private fun resetPreselectedTimeWhenNeed() {
        if (preselectedTime != null && selectableTimeRange != null) {
            preselectedTime?.let {
                selectableTimeRange?.let { timeRange ->
                    if (shouldResetPreselectedTime()) {
                        timeRange.start?.let { start ->
                            preselectedTime?.hour = start.hour
                            preselectedTime?.minute = start.minute
                        }
                    }
                }
            }
        }
    }

    private fun shouldResetPreselectedTime(): Boolean {
        val startHour = selectableTimeRange?.start?.hour ?: -1
        val startMinute = selectableTimeRange?.start?.minute ?: -1
        val endHour = selectableTimeRange?.end?.hour ?: -1
        val endMinute = selectableTimeRange?.end?.minute ?: -1
        val preSelectedHour = preselectedTime?.hour ?: -1
        val preSelectedMinute = preselectedTime?.minute ?: -1
        return when {
            isEarlierSelectableTime() -> preSelectedHour < startHour || preSelectedHour > endHour ||
                    (preSelectedHour == startHour && preSelectedMinute < startMinute) ||
                    (preSelectedHour == endHour && preSelectedMinute > endMinute)
            isLaterSelectableTime() -> (preSelectedHour in (endHour + 1) until startHour) ||
                    (preSelectedHour == startHour && preSelectedMinute < startMinute) ||
                    (preSelectedHour == endHour && preSelectedMinute > endMinute)
            else -> false
        }
    }

    private fun setupPreselectedTime(selectedTime: TimeValue? = preselectedTime) {
        binding.recyclerViewHour.scrollToPosition(1)
        binding.recyclerViewMinute.scrollToPosition(1)
        selectedTime?.let { time ->
            val selectedHour = time.hour
            val selectedMinute = time.minute
            val hourPosition = hourAdapter.getPositionByValue(selectedHour)
            val minutePosition = minuteAdapter.getPositionByValue(selectedMinute)
            updateHourPosition(if (hourPosition != -1) hourPosition else 0)
            updateMinutePosition(if (minutePosition != -1) minutePosition else 0)
        } ?: run {
            updateHourPosition(0)
            updateMinutePosition(0)
        }
    }

    private fun updateHourPosition(hourPosition: Int) {
        try {
            if (hourPosition != -1) {
                binding.recyclerViewHour.scrollToPosition(if (hourPosition > 0) hourPosition - 1 else 0)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (hourPosition != -1) {
                    binding.recyclerViewHour.smoothScrollToPosition(hourPosition)
                }
            }, UPDATE_PRE_SELECTED_START_TIME)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    private fun updateMinutePosition(minutePosition: Int) {
        try {
            if (minutePosition != -1) {
                binding.recyclerViewMinute.scrollToPosition(if (minutePosition > 0) minutePosition - 1 else 0)
            }
            Handler(Looper.getMainLooper()).postDelayed({
                if (minutePosition != -1) {
                    binding.recyclerViewMinute.smoothScrollToPosition(minutePosition)
                }
            }, UPDATE_PRE_SELECTED_START_TIME)
        } catch (ignored: IllegalArgumentException) {
        }
    }

    private fun useLiveDataAsCallback() {
        activity?.let { _ ->
            this.listener = object : Listener {
                override fun onTimePicked(hour: Int, minute: Int) {
                    sharedViewModel.onTimePicked(hour, minute)
                }
            }
        }
    }

    private fun onConfirmClick() {
        var hour = -1
        var minute = -1
        val minuteSnappedView = minuteSnapHelper.findSnapView(minuteLayoutManager)
        minuteSnappedView?.let { view ->
            minute = minuteAdapter.getValueByPosition(minuteLayoutManager.getPosition(view))
        }
        val hourSnappedView = hourSnapHelper.findSnapView(hourLayoutManager)
        hourSnappedView?.let { view ->
            hour = hourAdapter.getValueByPosition(hourLayoutManager.getPosition(view))
        }
        listener?.onTimePicked(hour, minute)
        targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_OK, Intent().apply {
            putExtra(EXTRA_SELECTED_HOUR, hour)
            putExtra(EXTRA_SELECTED_MINUTE, minute)
        })
        dismiss()
    }

    private fun onCancelClick() {
        targetFragment?.onActivityResult(targetRequestCode, Activity.RESULT_CANCELED, null)
        dismiss()
    }

    private fun isEarlierSelectableTime(): Boolean {
        val startHour = selectableTimeRange?.start?.hour ?: -1
        val startMinute = selectableTimeRange?.start?.minute ?: -1
        val endHour = selectableTimeRange?.end?.hour ?: -1
        val endMinute = selectableTimeRange?.end?.minute ?: -1
        return startHour != -1 && startMinute != -1 &&
                endHour != -1 && endMinute != -1 &&
                (startHour < endHour || (startHour == endHour && startMinute < endMinute))
    }

    private fun isLaterSelectableTime(): Boolean {
        val startHour = selectableTimeRange?.start?.hour ?: -1
        val startMinute = selectableTimeRange?.start?.minute ?: -1
        val endHour = selectableTimeRange?.end?.hour ?: -1
        val endMinute = selectableTimeRange?.end?.minute ?: -1
        return startHour != -1 && startMinute != -1 &&
                endHour != -1 && endMinute != -1 &&
                (startHour > endHour || (startHour == endHour && startMinute > endMinute))
    }

    // For LinearSnapHelper to snap correctly at first / last items
    //
    // Reference:
    // https://stackoverflow.com/questions/64853649/how-can-i-properly-center-the-first-and-last-items-in-a-horizontal-recyclerview

    open class CenterLinearLayoutManager : LinearLayoutManager {
        constructor(context: Context) : super(context)
        constructor(context: Context, orientation: Int, reverseLayout: Boolean) : super(context, orientation, reverseLayout)
        constructor(context: Context, attrs: AttributeSet, defStyleAttr: Int, defStyleRes: Int) : super(context, attrs, defStyleAttr, defStyleRes)

        private lateinit var recyclerView: RecyclerView

        override fun onLayoutChildren(recycler: RecyclerView.Recycler, state: RecyclerView.State) {
            // always measure first item, its size determines starting offset
            // this must be done before super.onLayoutChildren
            if (childCount == 0 && state.itemCount > 0) {
                val firstChild = recycler.getViewForPosition(0)
                measureChildWithMargins(firstChild, 0, 0)
                recycler.recycleView(firstChild)
            }
            super.onLayoutChildren(recycler, state)
        }

        override fun measureChildWithMargins(child: View, widthUsed: Int, heightUsed: Int) {
            val lp = (child.layoutParams as RecyclerView.LayoutParams).absoluteAdapterPosition
            super.measureChildWithMargins(child, widthUsed, heightUsed)
            if (lp != 0 && lp != itemCount - 1) return
            // after determining first and/or last items size use it to alter host padding
            when (orientation) {
                HORIZONTAL -> {
                    val hPadding = ((width - child.measuredWidth) / 2).coerceAtLeast(0)
                    if (lp == 0 || lp == itemCount - 1) recyclerView.updatePaddingRelative(start = hPadding, end = hPadding)
                }
                VERTICAL -> {
                    val vPadding = ((height - child.measuredHeight) / 2).coerceAtLeast(0)
                    if (lp == 0 || lp == itemCount - 1) recyclerView.updatePaddingRelative(top = vPadding, bottom = vPadding)
                }
            }
        }

        // capture host recyclerview
        override fun onAttachedToWindow(view: RecyclerView) {
            recyclerView = view
            super.onAttachedToWindow(view)
        }
    }

    interface Listener {
        fun onTimePicked(hour: Int, minute: Int)
    }

    class Builder {
        private var selectableTimeRange: TimeRange? = null
        private var preselectedTime: TimeValue? = null
        private var isUseViewModel: Boolean = false
        private var title: Int = -1
        private var prefix: Int = -1
        private var suffix: Int = -1
        private var titleColor: Int = -1
        private var themeColor: Int = -1
        private var timeInterval: Int = 1

        fun setPreselectedTime(time: TimeValue): Builder = this.apply {
            preselectedTime = time
        }

        fun setSelectableTimeRange(timeRange: TimeRange): Builder = this.apply {
            selectableTimeRange = timeRange
        }

        fun setTitle(@StringRes titleResId: Int): Builder = this.apply {
            title = titleResId
        }

        fun setPrefix(@StringRes prefixResId: Int): Builder = this.apply {
            prefix = prefixResId
        }

        fun setSuffix(@StringRes suffixResId: Int): Builder = this.apply {
            suffix = suffixResId
        }

        fun setTitleColor(@ColorRes colorResId: Int): Builder = this.apply {
            titleColor = colorResId
        }

        fun setThemeColor(@ColorRes colorResId: Int): Builder = this.apply {
            themeColor = colorResId
        }

        fun setTimeInterval(interval: Int): Builder = this.apply {
            timeInterval = interval
        }

        fun useViewModel(): Builder = this.apply {
            isUseViewModel = true
        }

        fun build(): SnapTimePickerDialog =
            newInstance(
                    selectableTimeRange,
                    preselectedTime,
                    isUseViewModel,
                    title,
                    prefix,
                    suffix,
                    titleColor,
                    themeColor,
                    timeInterval
            )
    }
}
