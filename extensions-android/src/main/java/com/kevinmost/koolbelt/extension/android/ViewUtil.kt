@file:Suppress("unused")

package com.kevinmost.koolbelt.extension.android

import android.annotation.TargetApi
import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.content.ContextWrapper
import android.content.res.TypedArray
import android.os.Build
import android.support.annotation.IdRes
import android.support.annotation.LayoutRes
import android.support.annotation.StyleableRes
import android.support.design.widget.Snackbar
import android.support.v7.widget.ViewUtils
import android.util.AttributeSet
import android.view.LayoutInflater
import android.view.View
import android.view.View.GONE
import android.view.View.INVISIBLE
import android.view.View.MeasureSpec
import android.view.View.VISIBLE
import android.view.ViewGroup
import android.view.ViewTreeObserver
import android.widget.Toast
import com.kevinmost.koolbelt.extension.mutableListOfSize
import com.kevinmost.koolbelt.util.buildList
import com.kevinmost.koolbelt.util.selfReference


fun <T : View> Activity.find(@IdRes id: Int): T? {
  @Suppress("UNCHECKED_CAST")
  return findViewById(id) as? T
}

fun <T : View> View.find(@IdRes id: Int): T? {
  @Suppress("UNCHECKED_CAST")
  return findViewById(id) as? T
}

fun <T : View> Dialog.find(@IdRes id: Int): T? {
  @Suppress("UNCHECKED_CAST")
  return findViewById(id) as? T
}

fun ViewGroup.getChildren(): List<View> {
  return mutableListOfSize<View>(childCount).apply {
    for (index in 0..childCount) {
      getChildAt(index)?.let { add(it) }
    }
  }
}

/**
 * NOTE: This [Sequence] could easily get unstable if you change the View hierarchy while it's in scope.
 */
fun ViewGroup.toSequence(): Sequence<View?> {
  var currentIndex = 0
  return generateSequence {
    return@generateSequence try {
      getChildAt(currentIndex)
    } finally {
      currentIndex++
    }
  }
}

fun <T : View> View.firstDescendantOfInstance(type: Class<T>, includeSelf: Boolean = true): T? {
  return when {
    includeSelf && type.isInstance(this) -> this
    this is ViewGroup -> toSequence()
        .filterNotNull()
        .firstOrNull { child ->
          child.firstDescendantOfInstance(type, includeSelf = true) != null
        }
    else -> null
  }?.let { type.cast(it) }
}


fun <T : View?> View.allDescendantsOfInstance(type: Class<T>, includeSelf: Boolean = true): List<T> {
  return buildList {
    addIf(includeSelf && type.isInstance(this)) { type.cast(this) }
    addAllIf(this is ViewGroup) {
      (this@allDescendantsOfInstance as ViewGroup).toSequence()
          .map { child -> child?.allDescendantsOfInstance(type, includeSelf = true) ?: emptyList() }
          .flatten()
          .asIterable()
    }
  }
}

operator fun ViewGroup.plus(@LayoutRes layoutRes: Int): ViewGroup {
  addView(layoutRes)
  return this
}

operator fun ViewGroup.plus(view: View): ViewGroup {
  addView(view)
  return this
}

fun ViewGroup.addView(@LayoutRes layoutRes: Int, index: Int = -1): View {
  return context.inflate<View>(layoutRes, this, false).apply {
    this@addView.addView(this, index)
  }
}

@JvmOverloads
fun <T : View> Context.inflate(
    @LayoutRes layout: Int,
    into: ViewGroup? = null,
    attachToParent: Boolean = false
): T {
  @Suppress("UNCHECKED_CAST")
  return LayoutInflater.from(this).inflate(layout, into, into != null && attachToParent) as T
}

fun sharedElementPair(view: View): Pair<View, String> {
  view.tag?.let { tag ->
    return view to tag as String
  } ?: throw IllegalArgumentException(
      "Your view must have a tag set to build this shared element transition!")
}

inline fun Context.styledAttributes(attributeSet: AttributeSet,
    @StyleableRes attrs: IntArray,
    block: (TypedArray) -> Unit) {
  val typedArray = obtainStyledAttributes(attributeSet, attrs)
  block(typedArray)
  try {
    typedArray.recycle()
  } catch(ignored: RuntimeException) {
    // This is thrown if we call .recycle() twice. The user doesn't have to call this method, but maybe they will!
  }
}

fun copyMeasureSpec(
    oldMeasureSpec: Int = -1,
    newSize: Int = -1,
    newMode: MeasureSpecMode? = null): Int {
  if (oldMeasureSpec == -1) {
    return MeasureSpec.makeMeasureSpec(newSize, newMode?.modeInt ?: MeasureSpec.UNSPECIFIED)
  }
  val oldSize = MeasureSpec.getSize(oldMeasureSpec)
  val oldMode = MeasureSpec.getMode(oldMeasureSpec)
  return MeasureSpec.makeMeasureSpec(
      if (newSize == -1) oldSize else newSize,
      if (newMode == null) oldMode else newMode.modeInt
  )
}

enum class MeasureSpecMode(val modeInt: Int) {
  EXACTLY(MeasureSpec.EXACTLY),
  AT_MOST(MeasureSpec.AT_MOST),
  UNSPECIFIED(MeasureSpec.UNSPECIFIED),
  ;

  operator fun get(size: Int): Int {
    return MeasureSpec.makeMeasureSpec(size, modeInt)
  }
}

/**
 * @param whileDisabled A function that is invoked after disabling the receiver [View]. If this
 * function returns `true`, the receiver [View] will be re-enabled.
 *
 * @return the return value of [whileDisabled]; essentially, whether or not this [View] will be
 * re-enabled at the end.
 */
inline fun <T : View> T.disableWhile(whileDisabled: (T) -> Boolean): Boolean {
  isEnabled = false
  whileDisabled(this).let { result ->
    if (result) {
      isEnabled = true
    }
    return result
  }
}

@JvmOverloads
fun View.snackbar(
    message: CharSequence,
    @IdRes anchorView: Int = View.NO_ID,
    duration: Int = Snackbar.LENGTH_LONG): Snackbar {
  return Snackbar.make(
      if (anchorView == View.NO_ID) this else findViewById(anchorView)!!,
      message,
      duration
  ).apply { show() }
}

fun Context.toast(
    message: CharSequence,
    duration: Int = Toast.LENGTH_LONG) {
  Toast.makeText(this, message, duration).show()
}

sealed class Visibility {

  interface HiddenVisibility

  object VISIBLE : Visibility()
  object INVISIBLE : Visibility(), HiddenVisibility
  object GONE : Visibility(), HiddenVisibility
}

var View.vis: Visibility
  get() {
    return when (visibility) {
      VISIBLE -> Visibility.VISIBLE
      INVISIBLE -> Visibility.INVISIBLE
      GONE -> Visibility.GONE
      else -> throw IllegalStateException("A view's visibility cannot be anything but VISIBLE, INVISIBLE, or GONE. The int value for this view's visibility was $visibility")
    }
  }
  set(value) {
    visibility = when (value) {
      Visibility.VISIBLE -> VISIBLE
      Visibility.INVISIBLE -> INVISIBLE
      Visibility.GONE -> GONE
    }
  }

fun View.show() {
  vis = Visibility.VISIBLE
}

@JvmOverloads
fun View.hide(visibility: Visibility.HiddenVisibility = Visibility.GONE) {
  vis = visibility as? Visibility ?: Visibility.GONE
}


val Activity.rootView: View
  get() = findViewById(android.R.id.content)


// See: http://grepcode.com/file/repository.grepcode.com/java/ext/com.google.android/android/4.3_r1/android/support/v7/app/MediaRouteButton.java#256
// Even Google, the kings of "let's have 50 fields written in Hungarian notation in our 4000-line class files",
// think this is gross
inline fun <reified A : Activity> View.getHostingActivity(): A {
  var context: Context = context
  while (context is ContextWrapper) {
    if (context is A) {
      return context
    }
    context = @Suppress("USELESS_CAST") (context as ContextWrapper).baseContext
  }
  throw IllegalStateException("This View has no hosting Activity of type ${A::class.java.name}. Its completely-unwrapped Context is an instance of ${context.javaClass.name}")
}

class OnHierarchyChangeListenerBuilder(private val preventInfiniteLoop: Boolean, private val parent: ViewGroup) {

  private var onChildAdded: (View) -> Unit = {}
  private var onChildRemoved: (View) -> Unit = {}

  private var userRequestedListenerStaysRemoved: Boolean = false

  /**
   * Specify what happens when a child is added from the [parent]
   */
  fun onChildAdded(block: (View) -> Unit) {
    onChildAdded = block
  }

  /**
   * Specify what happens when a child is removed from the [parent]
   */
  fun onChildRemoved(block: (View) -> Unit) {
    onChildRemoved = block
  }

  /**
   * Stops any future listening from this listener when invoked
   */
  fun removeThisListener() {
    userRequestedListenerStaysRemoved = true
    parent.setOnHierarchyChangeListener(null)
  }

  fun build(): ViewGroup.OnHierarchyChangeListener {
    return object : ViewGroup.OnHierarchyChangeListener {
      override fun onChildViewAdded(parent: View, child: View) {
        invokeListener(this, onChildAdded, child)
      }

      override fun onChildViewRemoved(parent: View, child: View) {
        invokeListener(this, onChildRemoved, child)
      }
    }
  }

  private fun invokeListener(listener: ViewGroup.OnHierarchyChangeListener, callback: (View) -> Unit, arg: View) {
    if (preventInfiniteLoop) {
      parent.setOnHierarchyChangeListener(null)
    }
    callback(arg)
    if (preventInfiniteLoop && !userRequestedListenerStaysRemoved) {
      parent.setOnHierarchyChangeListener(listener)
    }
  }
}

abstract class ViewSideAttribute protected constructor(protected val view: View) {

  abstract var left: Int

  abstract var right: Int

  abstract var top: Int

  abstract var bottom: Int

  var start: Int
    get() = if (view.direction == LayoutDirection.RTL) right else left
    set(value) {
      if (view.direction == LayoutDirection.RTL) right = value else left = value
    }

  var end: Int
    get() = if (view.direction == LayoutDirection.RTL) left else right
    set(value) {
      if (view.direction == LayoutDirection.RTL) left = value else right = value
    }

  operator fun get(side: Side): Int {
    return when (side) {
      Side.START -> start
      Side.TOP -> top
      Side.END -> end
      Side.BOTTOM -> bottom
    }
  }

  operator fun set(side: Side, value: Int) {
    when (side) {
      Side.START -> start = value
      Side.TOP -> top = value
      Side.END -> end = value
      Side.BOTTOM -> bottom = value
    }
  }
}

val View.margins: ViewMargins
  get() = ViewMargins(this)

class ViewMargins internal constructor(view: View) : ViewSideAttribute(view) {

  override var left: Int
    get() = view.marginLayoutParams?.leftMargin ?: 0
    set(value) {
      view.marginLayoutParams?.leftMargin = value
    }

  override var right: Int
    get() = view.marginLayoutParams?.rightMargin ?: 0
    set(value) {
      view.marginLayoutParams?.rightMargin = value
    }

  override var top: Int
    get() = view.marginLayoutParams?.topMargin ?: 0
    set(value) {
      view.marginLayoutParams?.topMargin = value
    }

  override var bottom: Int
    get() = view.marginLayoutParams?.bottomMargin ?: 0
    set(value) {
      view.marginLayoutParams?.bottomMargin = value
    }

  private val View.marginLayoutParams: ViewGroup.MarginLayoutParams?
    get() {
      return layoutParams as? ViewGroup.MarginLayoutParams
    }
}

val View.padding: ViewPadding
  get() = ViewPadding(this)

class ViewPadding internal constructor(view: View) : ViewSideAttribute(view) {
  override var left: Int
    get() = view.paddingLeft
    set(value) = view.paddings(left = value)

  override var right: Int
    get() = view.paddingRight
    set(value) = view.paddings(right = value)

  override var top: Int
    get() = view.paddingTop
    set(value) = view.paddings(top = value)

  override var bottom: Int
    get() = view.paddingBottom
    set(value) = view.paddings(bottom = value)

  private fun View.paddings(
      left: Int = paddingLeft,
      top: Int = paddingTop,
      right: Int = paddingRight,
      bottom: Int = paddingBottom
  ) = setPadding(left, top, right, bottom)
}

val Context.layoutDirection: LayoutDirection
  @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR1)
  get() = LayoutDirection.determineForLayoutDirection(resources.configuration.layoutDirection)

val View.direction: LayoutDirection
  get() = if (ViewUtils.isLayoutRtl(this)) LayoutDirection.RTL else LayoutDirection.LTR

enum class LayoutDirection {
  LTR,
  RTL
  ;

  companion object {

    fun determineForLayoutDirection(layoutDirectionInt: Int) = when (layoutDirectionInt) {
      android.util.LayoutDirection.LTR -> LTR
      android.util.LayoutDirection.RTL -> RTL
      else -> throw IllegalArgumentException("Invalid int $layoutDirectionInt passed to determineForLayoutDirection")
    }
  }
}

enum class Side(val opposite: Side, val clockwiseInLTR: Side) {
  START(opposite = END, clockwiseInLTR = TOP),
  TOP(opposite = BOTTOM, clockwiseInLTR = END),
  END(opposite = START, clockwiseInLTR = BOTTOM),
  BOTTOM(opposite = TOP, clockwiseInLTR = START)
  ;

  fun counterClockwise(layoutDirectionInt: Int): Side {
    return counterClockwise(LayoutDirection.determineForLayoutDirection(layoutDirectionInt))
  }

  fun counterClockwise(layoutDirection: LayoutDirection): Side {
    return clockwise(layoutDirection).clockwise(layoutDirection).clockwise(layoutDirection)
  }

  fun clockwise(layoutDirectionInt: Int): Side {
    return clockwise(LayoutDirection.determineForLayoutDirection(layoutDirectionInt))
  }

  fun clockwise(layoutDirection: LayoutDirection): Side {
    return clockwiseInLTR.run {
      if (layoutDirection == LayoutDirection.RTL) this.opposite else this
    }
  }
}


/**
 * Sets an [ViewGroup.OnHierarchyChangeListener] on this [ViewGroup]
 *
 * @param preventInfiniteLoop removes the listener before invoking either [OnHierarchyChangeListenerBuilder.onChildAdded]
 * or [OnHierarchyChangeListenerBuilder.onChildRemoved], and then adds it back after invoking, so that calls can't be
 * made recursively by accident
 *
 * @param init where you specify what to listen for
 */
inline fun <V : ViewGroup> V.onHierarchyChanges(
    preventInfiniteLoop: Boolean = true,
    init: OnHierarchyChangeListenerBuilder.() -> Unit) {
  setOnHierarchyChangeListener(OnHierarchyChangeListenerBuilder(preventInfiniteLoop, this).apply { init() }.build())
}

inline fun <V : View> V.onGlobalLayout(
    removeAfterFirstInvocation: Boolean = true,
    crossinline onNextGlobalLayout: (V) -> Unit) {
  selfReference<ViewTreeObserver.OnGlobalLayoutListener> {
    ViewTreeObserver.OnGlobalLayoutListener {
      if (removeAfterFirstInvocation) {
        viewTreeObserver.removeOnGlobalLayoutListener(self)
      }
      onNextGlobalLayout(this@onGlobalLayout)
    }.apply {
      viewTreeObserver.addOnGlobalLayoutListener(this)
    }
  }
}
