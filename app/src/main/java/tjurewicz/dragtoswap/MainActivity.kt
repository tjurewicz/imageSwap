package tjurewicz.dragtoswap

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Matrix
import android.graphics.Rect
import android.opengl.Visibility
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
import android.view.animation.BounceInterpolator
import android.widget.ImageView
import androidx.activity.viewModels
import androidx.appcompat.app.AppCompatActivity
import androidx.lifecycle.Observer
import com.bumptech.glide.Glide
import com.bumptech.glide.load.resource.drawable.DrawableTransitionOptions
import com.google.android.material.imageview.ShapeableImageView
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.android.synthetic.main.content_scrolling.*
import tjurewicz.dragtoswap.MainActivityCoordinator.Events.ImageDropped
import tjurewicz.dragtoswap.MainActivityCoordinator.Events.ImageHover
import tjurewicz.dragtoswap.MainActivityCoordinator.Events.ImageSelected

/**
 * Place for applying view data to views, and passing actions to coordinator
 */
class MainActivity : AppCompatActivity() {

    private val viewModel: MainActivityViewModel by viewModels()
    private lateinit var coordinator: MainActivityCoordinator

    private val imageViews: List<ImageView> by lazy { listOf(image1, image2, image3, image4) }
    private val cursorImage: ShapeableImageView by lazy { cursor }

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        coordinator = MainActivityCoordinator(viewModel)
        setSupportActionBar(toolbar)
        toolbar.title = title

        viewModel.images.observe(this, Observer { images ->
            // Load all the images from the viewModel into ImageViews
            imageViews.forEachIndexed { index, imageView ->
                Glide.with(this)
                    .load(images[index].imageUrl)
                    .transition(DrawableTransitionOptions.withCrossFade(200))
                    .into(imageView)
                imageView.tag =
                    index // Quick&dirty: stash the index of this image in the ImageView tag
            }
        })

        list.setOnTouchListener { _, event ->
            val eventX = event.x.toInt()
            val eventY = event.y.toInt()
            when (event.action) {
                MotionEvent.ACTION_DOWN -> { // Hunt for what's under the drag and start dragging
                    cursorImage.x = event.x - CURSOR_BUFFER
                    cursorImage.y = event.y - CURSOR_BUFFER
                    getImageViewAt(eventX, eventY)?.let {
                        val index = it.tag as Int
                        Glide.with(this)
                            .load(viewModel.images.value?.get(index)?.imageUrl)
                            .into(cursorImage)
                        cursorImage.visibility = View.VISIBLE
                        animateBounceIn(cursorImage)
                        coordinator.startedSwap(index, eventX, eventY)
                    }
                }
                MotionEvent.ACTION_UP -> { // If we are dragging to something valid, do the swap
                    coordinator.imageDropped(eventX, eventY)
                }
                MotionEvent.ACTION_MOVE -> {
                    if (viewModel.draggingIndex.value != null) {
                        cursorImage.x = event.x - CURSOR_BUFFER
                        cursorImage.y = event.y - CURSOR_BUFFER
                        val hoverOverImage = getImageViewAt(eventX, eventY)
                        imageViews.filter {
                            it.tag != viewModel.draggingIndex.value
                                    && it.tag != viewModel.hoverImageIndex.value
                        }.forEach { it.clearColorFilter() }
                        hoverOverImage?.tag?.let {
                            if (it != viewModel.hoverImageIndex.value) {
                                coordinator.updateHoverImage(hoverOverImage.tag as Int, eventX, eventY)
                            }
                        }
                    }
                }
            }
            true
        }

        viewModel.events.observe(this) {
            when (it) {
                is ImageDropped -> dropImage(it.x, it.y)
                is ImageSelected -> grabImage(it.x, it.y)
                is ImageHover -> hoverImage(it.x, it.y)
            }
        }
    }

    private fun grabImage(eventX: Int, eventY: Int) {
        val sourceImage = getImageViewAt(eventX, eventY)
        animateColorFilter(sourceImage)
    }

    private fun hoverImage(eventX: Int, eventY: Int) {
        val image = getImageViewAt(eventX, eventY)
        if (image?.tag?.equals(viewModel.draggingIndex.value) == false)
            animateColorFilter(viewModel.hoverImageIndex.value?.let { imageViews[it] })
    }

    private fun dropImage(eventX: Int, eventY: Int) {
        val sourceImageIndex = viewModel.draggingIndex.value
        val targetImage = getImageViewAt(eventX, eventY)
        val targetImageIndex = targetImage?.let { it.tag as Int }
        if (targetImageIndex != null && sourceImageIndex != null && targetImageIndex != sourceImageIndex) {
            animateTranslateCursorToTargetImage(targetImage)
            targetImage.visibility = View.INVISIBLE
            animateSwap(targetImage)
            animateFadeIn(imageViews[sourceImageIndex])
            coordinator.swapImages(sourceImageIndex, targetImageIndex)
        } else {
            cursorImage.visibility = View.GONE
            coordinator.cancelSwap()
        }
        imageViews.forEach { it.clearColorFilter() }
    }

    private fun animateTranslateCursorToTargetImage(targetImage: ImageView) {
        val x = targetImage.pivotX
        val y = targetImage.y + (targetImage.height / 2)
        cursorImage.animate().apply {
            translationX(x - CURSOR_BUFFER_HALF)
            translationY(y - CURSOR_BUFFER)
            duration = ANIMATION_DURATION_MEDIUM
            start()
        }
    }

    private fun animateColorFilter(image: ImageView?) {
        val colorFilter = AnimatorInflater.loadAnimator(this, R.animator.color_filter) as ValueAnimator
        colorFilter.apply {
            addUpdateListener { animation -> image?.setColorFilter(animation.animatedValue as Int) }
            start()
        }
    }

    private fun animateFadeIn(image: ImageView) {
        val fadeIn = AnimatorInflater.loadAnimator(this, R.animator.fade_in) as ValueAnimator
        fadeIn.apply {
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                image.alpha = animatedValue
            }
            start()
        }
    }

    private fun animateSwap(image: ImageView) {
        val swap = AnimatorInflater.loadAnimator(this, R.animator.swap) as ValueAnimator
        swap.apply {
            addUpdateListener { animation ->
                image.visibility = View.VISIBLE
                cursorImage.visibility = View.GONE
                val animatedValue = animation.animatedValue as Float
                image.scaleX = animatedValue
                image.scaleY = animatedValue
                imageViews.forEach { it.clearColorFilter() } // Just in case it wasn't cleared the first time
            }
            startDelay = ANIMATION_DURATION_MEDIUM
            start()
        }
    }

    private fun animateBounceIn(image: ImageView) {
        val bounce = AnimatorInflater.loadAnimator(this, R.animator.bounce_in) as ValueAnimator
        bounce.apply {
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                image.scaleX = animatedValue
                image.scaleY = animatedValue
            }
            start()
        }
    }

    private fun getImageViewAt(x: Int, y: Int): ImageView? {
        val hitRect = Rect()
        return imageViews.firstOrNull {
            it.getHitRect(hitRect)
            hitRect.contains(x, y)
        }
    }

    companion object {
        private const val CURSOR_BUFFER = 100F
        private const val CURSOR_BUFFER_HALF = CURSOR_BUFFER / 2
        private const val ANIMATION_DURATION_MEDIUM = 350L
    }

}