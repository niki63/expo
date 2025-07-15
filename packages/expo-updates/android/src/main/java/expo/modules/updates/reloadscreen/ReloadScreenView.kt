package expo.modules.updates.reloadscreen

import android.content.Context
import android.graphics.BitmapFactory
import android.graphics.Color
import android.net.Uri
import android.util.AttributeSet
import android.util.Base64
import android.view.Gravity
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.ProgressBar
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL

class ReloadScreenView @JvmOverloads constructor(
  context: Context,
  attrs: AttributeSet? = null,
  defStyleAttr: Int = 0
) : FrameLayout(context, attrs, defStyleAttr) {
  private var progressBar: ProgressBar? = null
  private var imageView: ImageView? = null
  private var currentConfiguration: ReloadScreenConfiguration? = null
  private val scope = CoroutineScope(Dispatchers.IO)

  init {
    layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
  }

  fun updateConfiguration(configuration: ReloadScreenConfiguration) {
    currentConfiguration = configuration
    removeAllViews()
    if (configuration.image != null && configuration.imageFullScreen) {
      setBackgroundColor(Color.TRANSPARENT)
    } else {
      setBackgroundColor(configuration.backgroundColor)
    }

    if (configuration.image != null) {
      addImageView(configuration)
    }
    if (configuration.spinner.enabled) {
      addSpinner(configuration.spinner)
    }
  }

  private fun addImageView(configuration: ReloadScreenConfiguration) {
    val imageSource = configuration.image ?: return

    imageView = ImageView(context).apply {
      scaleType = when (configuration.imageResizeMode) {
        ImageResizeMode.CONTAIN -> ImageView.ScaleType.FIT_CENTER
        ImageResizeMode.COVER -> ImageView.ScaleType.CENTER_CROP
        ImageResizeMode.CENTER -> ImageView.ScaleType.CENTER
        ImageResizeMode.STRETCH -> ImageView.ScaleType.FIT_XY
      }

      if (configuration.imageFullScreen) {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      } else if (imageSource.width != null && imageSource.height != null && imageSource.width > 0 && imageSource.height > 0) {
        val scale = imageSource.scale ?: 1.0
        val scaledWidth = dpToPx((imageSource.width * scale).toFloat())
        val scaledHeight = dpToPx((imageSource.height * scale).toFloat())
        layoutParams = LayoutParams(scaledWidth, scaledHeight).apply {
          gravity = Gravity.CENTER
        }
      } else {
        layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT)
      }
    }

    addView(imageView)
    loadImage(imageSource)
  }

  private fun addSpinner(spinnerConfig: SpinnerConfiguration) {
    progressBar = ProgressBar(context).apply {
      isIndeterminate = true

      val size = dpToPx(spinnerConfig.size.getSize().toFloat())
      layoutParams = LayoutParams(size, size).apply {
        gravity = Gravity.CENTER
      }

      indeterminateDrawable?.setTint(spinnerConfig.color)
    }

    addView(progressBar)
  }

  private fun loadImage(imageSource: ReloadScreenImageSource) {
    val view = imageView ?: return
    val url = imageSource.url ?: return

    when (url.scheme) {
      "http", "https" -> loadImageFromUrl(url, view)
      "file" -> loadImageFromFile(url, view)
      "data" -> loadImageFromDataUri(url, view)
      else -> loadImageFromAssets(url.toString(), view)
    }
  }

  private fun loadImageFromUrl(uri: Uri, imageView: ImageView) {
    scope.launch {
      try {
        val url = URL(uri.toString())
        val connection = url.openConnection() as HttpURLConnection
        connection.doInput = true
        connection.connect()

        val inputStream: InputStream = connection.inputStream
        val bitmap = BitmapFactory.decodeStream(inputStream)

        withContext(Dispatchers.Main) {
          bitmap?.let {
            imageView.setImageBitmap(it)
          } ?: handleImageLoadFailure()
        }
      } catch (e: Exception) {
        withContext(Dispatchers.Main) {
          handleImageLoadFailure()
        }
      }
    }
  }

  private fun loadImageFromFile(uri: Uri, imageView: ImageView) {
    val bitmap = BitmapFactory.decodeFile(uri.path)
    bitmap?.let {
      imageView.setImageBitmap(it)
    } ?: handleImageLoadFailure()
  }

  private fun loadImageFromDataUri(uri: Uri, imageView: ImageView) {
    try {
      val dataString = uri.toString()
      if (dataString.startsWith("data:image/")) {
        val base64String = dataString.substring(dataString.indexOf(",") + 1)
        val decodedBytes = Base64.decode(base64String, Base64.DEFAULT)
        val bitmap = BitmapFactory.decodeByteArray(decodedBytes, 0, decodedBytes.size)

        bitmap?.let {
          imageView.setImageBitmap(it)
        } ?: handleImageLoadFailure()
      } else {
        handleImageLoadFailure()
      }
    } catch (e: Exception) {
      handleImageLoadFailure()
    }
  }

  private fun loadImageFromAssets(imageName: String, imageView: ImageView) {
    try {
      val resourceId = getResourceId(imageName)
      if (resourceId != 0) {
        val bitmap = BitmapFactory.decodeResource(context.resources, resourceId)
        if (bitmap != null) {
          imageView.setImageBitmap(bitmap)
        } else {
          handleImageLoadFailure()
        }
      } else {
        handleImageLoadFailure()
      }
    } catch (e: Exception) {
      handleImageLoadFailure()
    }
  }

  private fun handleImageLoadFailure() {
    imageView?.visibility = View.GONE
    currentConfiguration?.let { config ->
      setBackgroundColor(config.backgroundColor)
      addSpinner(config.spinner.copy(enabled = true))
    }
  }

  private fun getResourceId(resourceName: String): Int {
    return try {
      val name = resourceName.replace("@drawable/", "").replace("@mipmap/", "")
      context.resources.getIdentifier(name, "drawable", context.packageName)
    } catch (e: Exception) {
      0
    }
  }

  private fun dpToPx(dp: Float): Int {
    return (dp * context.resources.displayMetrics.density).toInt()
  }

  override fun onDetachedFromWindow() {
    super.onDetachedFromWindow()
    scope.cancel()
  }
}
