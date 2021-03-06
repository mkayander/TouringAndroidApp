package net.inqer.touringapp.ui.home

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.animation.AnimatorSet
import android.animation.ObjectAnimator
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Build
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.CircularProgressDrawable
import com.bumptech.glide.Glide
import com.google.android.material.circularreveal.CircularRevealCompat
import com.google.android.material.circularreveal.CircularRevealWidget
import net.inqer.touringapp.R
import net.inqer.touringapp.data.models.TourRoute
import net.inqer.touringapp.databinding.ItemTourBinding
import net.inqer.touringapp.util.DrawableHelpers
import net.inqer.touringapp.util.DrawableHelpers.modifyButtonIcon
import net.inqer.touringapp.util.getThemeColor
import java.text.DateFormat
import java.util.*
import kotlin.collections.HashMap
import kotlin.math.sqrt


class ToursAdapter constructor(
    private val callbacks: TourViewHolder.OnTourViewInteraction,
    private val dateFormat: DateFormat
) : ListAdapter<TourRoute, ToursAdapter.Companion.TourViewHolder>(TOUR_BRIEF_ITEM_CALLBACK) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): TourViewHolder {
        val binding: ItemTourBinding =
            ItemTourBinding.inflate(LayoutInflater.from(parent.context), parent, false)
        return TourViewHolder(this, binding, callbacks, revealedStates)
    }

    init {
        setHasStableIds(true)
    }

    override fun getItemId(position: Int) = getItem(position).id

    private lateinit var recyclerView: RecyclerView

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    override fun onBindViewHolder(holder: TourViewHolder, position: Int) {
        holder.bind(getItem(position), dateFormat)
    }

    private val revealedStates: HashMap<Long, Boolean> = HashMap()

    private fun updateStatesWithList(list: MutableList<TourRoute>) {
        val diff = revealedStates.toMutableMap()
        list.forEach { route ->
            diff.remove(route.id)
        }
        Log.d(TAG, "init: ${dateFormat.format(Date())}")

        diff.keys.forEach { removedRouteId -> revealedStates.remove(removedRouteId) }
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    fun closeOthers(id: Long) {
        revealedStates.filter { it.value && it.key != id }.forEach { entry ->
            val holder = recyclerView.findViewHolderForItemId(entry.key) as TourViewHolder?
            Log.d(TAG, "closeOthers: holder - $holder")

            if (holder != null) {
                holder.performCardReveal(getItem(holder.adapterPosition))
            } else {
                Log.w(TAG, "closeOthers: holder is null!")
                revealedStates[entry.key] = false
                notifyDataSetChanged()
            }
        }
    }

    override fun onAttachedToRecyclerView(recyclerView: RecyclerView) {
        super.onAttachedToRecyclerView(recyclerView)
        this.recyclerView = recyclerView
    }

    override fun onCurrentListChanged(
        previousList: MutableList<TourRoute>,
        currentList: MutableList<TourRoute>
    ) {
        super.onCurrentListChanged(previousList, currentList)
        updateStatesWithList(currentList)
    }

    companion object {
        private const val TAG = "ToursAdapter"

        class TourViewHolder constructor(
            private val adapter: ToursAdapter,
            val binding: ItemTourBinding,
            private val callbacks: OnTourViewInteraction,
            private val states: HashMap<Long, Boolean>
        ) : RecyclerView.ViewHolder(binding.root) {

            private var fabRevealed = false
            private val context = binding.root.context

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            fun performCardReveal(tour: TourRoute) {
                binding.fabOpenTour.isClickable = false
                animateCircularReveal(
                    context,
                    binding.innerCard,
                    !fabRevealed,
                    object : AnimationCallback {
                        override fun onStart() {
                            fabRevealed = !fabRevealed

                            val revealed = states[tour.id]
                            if (revealed != null) {
                                states[tour.id] = !revealed
                            } else {
                                states[tour.id] = true
                            }

                            DrawableHelpers.modifyFab(
                                context, binding.fabOpenTour,
                                if (fabRevealed) R.drawable.ic_baseline_close_24 else R.drawable.ic_baseline_launch_24
                            )
                        }

                        override fun onEnd() {
                            binding.fabOpenTour.isClickable = true
                            if (fabRevealed) callbacks.cardOpened(tour)
                        }
                    })
            }

            interface OnTourViewInteraction {
                fun rootClick(item: TourRoute)
                fun cardOpened(item: TourRoute)
                fun fabClick(item: TourRoute)
                fun launchClick(item: TourRoute)
                fun cancelClick(item: TourRoute)
            }

            @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
            fun bind(tour: TourRoute, dateFormat: DateFormat) {
                binding.title.text = tour.title
                binding.secondaryText.text = dateFormat.format(tour.createdAt)
                binding.supportingText.text = tour.description

                val activeIndicatorVisibility = if (tour.isActive) View.VISIBLE else View.INVISIBLE
                binding.activeIndicator.visibility = activeIndicatorVisibility
                binding.innerActiveIndicator.visibility = activeIndicatorVisibility

                val launchText: CharSequence
                val launchClick: View.OnClickListener
                val launchIcon: Drawable?

                if (!tour.isActive) {
                    launchText = context.getText(R.string.lets_go)
                    launchClick = View.OnClickListener { callbacks.launchClick(tour) }
                    launchIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_send_24)
                } else {
                    launchText = context.getText(R.string.cancel)
                    launchClick = View.OnClickListener { callbacks.cancelClick(tour) }
                    launchIcon = ContextCompat.getDrawable(context, R.drawable.ic_baseline_close_24)
                }

                val color = ColorStateList.valueOf(context.getThemeColor(R.attr.colorPrimary))
                val color2 = ColorStateList.valueOf(Color.BLUE)
                Log.d(TAG, "bind: colors: $color ; $color2")

                binding.btnStart.setOnClickListener(launchClick)
                binding.btnStart.text = launchText
                binding.btnStart.icon = launchIcon

                binding.btnStart.also {
                    modifyButtonIcon(it, launchIcon, context.getThemeColor(R.attr.colorPrimary))
                }

                binding.innerBtnStart.setOnClickListener(launchClick)
                binding.innerBtnStart.text = launchText
                binding.innerBtnStart.icon = launchIcon

                binding.innerTitle.text = tour.title
                binding.innerSubtitle.text = tour.createdAt.toString()

                // Logic that depends on current card data state (Partial/Full)
                val isFull = tour.totalDistance != null && tour.estimatedDuration != null
                Log.d(TAG, "bind: ${tour.id} ; isFull = $isFull")
                if (isFull) {
                    // The data we have is full
                    binding.innerProgressBar.hide()

                    binding.innerTourLength.visibility = View.VISIBLE
                    binding.innerWaypoints.visibility = View.VISIBLE
                    binding.innerTime.visibility = View.VISIBLE
                    binding.innerDestinations.visibility = View.VISIBLE
                    binding.innerBtnStart.isClickable = false

                    binding.innerTourLength.text =
                        context.getString(R.string.tour_length, tour.totalDistance)
                    binding.innerWaypoints.text =
                        context.getString(R.string.n_waypoints, tour.waypoints?.size)
                    binding.innerTime.text =
                        context.getString(R.string.estimated_n_minutes, tour.estimatedDuration)
                    binding.innerDestinations.text =
                        context.getString(R.string.n_destinations, tour.destinations?.size)
                } else {
                    // The data we have is currently partial
                    binding.innerProgressBar.show()

                    binding.innerTourLength.visibility = View.INVISIBLE
                    binding.innerWaypoints.visibility = View.INVISIBLE
                    binding.innerTime.visibility = View.INVISIBLE
                    binding.innerDestinations.visibility = View.INVISIBLE
                    binding.innerBtnStart.isClickable = false
                }

                fabRevealed = states[tour.id] ?: fabRevealed
                DrawableHelpers.modifyFab(
                    context, binding.fabOpenTour,
                    if (fabRevealed) R.drawable.ic_baseline_close_24 else R.drawable.ic_baseline_launch_24
                )
                binding.innerCard.visibility = if (fabRevealed) View.VISIBLE else View.INVISIBLE
                binding.innerCard.circularRevealScrimColor =
                    ContextCompat.getColor(context, android.R.color.transparent)

                val progress = CircularProgressDrawable(context)
                progress.centerRadius = 30f
                progress.strokeWidth = 4f
                progress.setColorSchemeColors(R.color.teal_200, R.color.purple_200)
                progress.start()

                Glide.with(binding.root)
                    .load(tour.image)
                    .placeholder(progress)
                    .into(binding.image)

                Glide.with(binding.root)
                    .load(tour.image)
                    .placeholder(progress)
                    .into(binding.innerImage)

                binding.root.setOnClickListener {
                    callbacks.rootClick(tour)
                }

                binding.fabOpenTour.setOnClickListener {
                    adapter.closeOthers(tour.id)
                    callbacks.fabClick(tour)
                    performCardReveal(tour)
                }
            }
        }

        @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
        private fun <T> animateCircularReveal(
            context: Context,
            circularRevealWidget: T,
            expand: Boolean = true,
            callbacks: AnimationCallback
        ) where T : View?, T : CircularRevealWidget? {
            val primaryColor = ContextCompat.getColor(context, R.color.purple_200)

            circularRevealWidget?.post {
                val viewWidth = circularRevealWidget.width
                val viewHeight = circularRevealWidget.height
                val viewDiagonal =
                    sqrt((viewWidth * viewWidth + viewHeight * viewHeight).toDouble()).toInt()

                val objectAnimator =
                    if (expand)
                        ObjectAnimator.ofArgb(
                            circularRevealWidget,
                            CircularRevealWidget.CircularRevealScrimColorProperty.CIRCULAR_REVEAL_SCRIM_COLOR,
                            primaryColor,
                            Color.TRANSPARENT
                        )
                    else
                        ObjectAnimator.ofArgb(
                            circularRevealWidget,
                            CircularRevealWidget.CircularRevealScrimColorProperty.CIRCULAR_REVEAL_SCRIM_COLOR,
                            Color.TRANSPARENT,
                            primaryColor
                        )

                val animatorSet = AnimatorSet().apply {
                    playTogether(
                        CircularRevealCompat.createCircularReveal(
                            circularRevealWidget,
                            (viewWidth - 120).toFloat(),
                            (viewHeight / 2).toFloat() + 42f,
                            if (expand) 10f else 1000f,
                            if (expand) (viewDiagonal / 1.2).toFloat() else 10f
                        ),
                        objectAnimator
                    )

                    duration = 512

                    addListener(object : AnimatorListenerAdapter() {
                        override fun onAnimationStart(animation: Animator?, isReverse: Boolean) {
                            super.onAnimationStart(animation, isReverse)
                            callbacks.onStart()
                        }

                        override fun onAnimationEnd(animation: Animator?) {
                            super.onAnimationEnd(animation)
                            if (!expand) circularRevealWidget.visibility = View.INVISIBLE
                            callbacks.onEnd()
                        }
                    })
                }

                if (expand) circularRevealWidget.visibility = View.VISIBLE
                animatorSet.start()
            }
        }

        private interface AnimationCallback {
            fun onStart()
            fun onEnd()
        }

        private val TOUR_BRIEF_ITEM_CALLBACK: DiffUtil.ItemCallback<TourRoute> =
            object : DiffUtil.ItemCallback<TourRoute>() {
                override fun areItemsTheSame(oldItem: TourRoute, newItem: TourRoute): Boolean {
                    return oldItem.id == newItem.id
                }

                override fun areContentsTheSame(oldItem: TourRoute, newItem: TourRoute): Boolean {
                    if (oldItem.title != newItem.title) return false
                    if (oldItem.description != newItem.description) return false
                    if (oldItem.image != newItem.image) return false
                    if (oldItem.createdAt != newItem.createdAt) return false
                    if (oldItem.isActive != newItem.isActive) return false
                    if (oldItem.updatedAt != newItem.updatedAt) return false
                    if (oldItem.totalDistance != newItem.totalDistance) return false
                    if (oldItem.estimatedDuration != newItem.estimatedDuration) return false
                    if (oldItem.waypoints != null) {
                        if (newItem.waypoints == null) return false
                        if (!oldItem.waypoints.contentEquals(newItem.waypoints)) return false
                    } else if (newItem.waypoints != null) return false
                    if (oldItem.destinations != null) {
                        if (newItem.destinations == null) return false
                        if (!oldItem.destinations.contentEquals(newItem.destinations)) return false
                    } else if (newItem.destinations != null) return false

                    return true
                }
            }
    }
}