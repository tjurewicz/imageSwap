package tjurewicz.dragtoswap

import android.animation.AnimatorInflater
import android.animation.ValueAnimator
import android.annotation.SuppressLint
import android.graphics.Rect
import android.os.Bundle
import android.view.MotionEvent
import android.view.View
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
                    cursorImage.x = event.x - 100f
                    cursorImage.y = event.y - 100f
                    getImageViewAt(eventX, eventY)?.let {
                        val index = it.tag as Int
                        Glide.with(this)
                            .load(viewModel.images.value?.get(index)?.imageUrl)
                            .into(cursorImage)
                        cursorImage.visibility = View.VISIBLE
                        coordinator.startedSwap(index, eventX, eventY)
                    }
                }
                MotionEvent.ACTION_UP -> { // If we are dragging to something valid, do the swap
                    cursorImage.visibility = View.GONE
                    coordinator.imageDropped(eventX, eventY)
                }
                MotionEvent.ACTION_MOVE -> {
                    cursorImage.x = event.x - 100f
                    cursorImage.y = event.y - 100f
                    val hoverOverImage = getImageViewAt(eventX, eventY)
                    imageViews.filter {
                        it.tag != viewModel.draggingIndex.value
                        && it.tag != viewModel.hoverImageIndex.value
                    }.forEach { it.clearColorFilter() }
                    if (hoverOverImage?.tag?.equals(viewModel.hoverImageIndex.value) == false) {
                        coordinator.updateHoverImage(hoverOverImage.tag as Int, eventX, eventY)
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
        animateColorFilter(
            image = sourceImage
        )
    }

    private fun hoverImage(eventX: Int, eventY: Int) {
        val image = getImageViewAt(eventX, eventY)
        if (image?.tag?.equals(viewModel.draggingIndex.value) == false)
            animateColorFilter(
                image = viewModel.hoverImageIndex.value?.let { imageViews[it] }
            )
    }

    private fun dropImage(eventX: Int, eventY: Int) {
        val sourceImageIndex = viewModel.draggingIndex.value
        val targetImage = getImageViewAt(eventX, eventY)
        val targetImageIndex = targetImage?.let { it.tag as Int }
        if (targetImageIndex != null && sourceImageIndex != null && targetImageIndex != sourceImageIndex) {
            animateSwap(targetImage)
            animateFade(imageViews[sourceImageIndex])
            coordinator.swapImages(sourceImageIndex, targetImageIndex)
        } else {
            coordinator.cancelSwap()
        }
        imageViews.forEach { it.clearColorFilter() }
    }

    private fun animateColorFilter(image: ImageView?) {
        val anim = AnimatorInflater.loadAnimator(this, R.animator.color_filter) as ValueAnimator
        anim.apply {
            duration = 250
            addUpdateListener { valueAnimator ->
                image?.setColorFilter(
                    valueAnimator.animatedValue as Int
                )
            }
            start()
        }
    }

    private fun animateFade(sourceImage: ImageView) {
        val fadeIn = AnimatorInflater.loadAnimator(this, R.animator.fade_in) as ValueAnimator
        fadeIn.apply {
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                sourceImage.alpha = animatedValue
            }
            start()
        }
    }

    private fun animateSwap(imageView: ImageView) {
        val animation = AnimatorInflater.loadAnimator(this, R.animator.swap) as ValueAnimator
        animation.apply {
            addUpdateListener { animation ->
                val animatedValue = animation.animatedValue as Float
                imageView.alpha = animatedValue
                imageView.scaleX = animatedValue
                imageView.scaleY = animatedValue
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

}