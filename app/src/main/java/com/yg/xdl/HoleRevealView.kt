package com.yg.xdl

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Path
import android.graphics.RectF
import android.view.View

/**
 * 主题切换的「覆盖」层：整层画旧界面截图，中间挖一个圆洞。
 *
 * 洞里的内容不画 —— 那是下层已经换好色的新界面。洞半径从 0 涨到最大时，
 * 视觉上就是**新主题从点击点向外扩张盖住旧界面**；同时两张都是真实界面，
 * 组件（文字/卡片/按钮）不会被一块纯色抹掉。
 *
 * 另外这个 View 也负责「收回」方向：洞半径保持 0，配合外层
 * `ViewAnimationUtils.createCircularReveal` 把整层从满屏裁到点击点即可，
 * 两者互不干扰（canvas.clipPath 和 View 自身的 reveal 裁剪会叠加）。
 */
class HoleRevealView(context: Context) : View(context) {

    private var snapshot: Bitmap? = null
    private val outer = Path()
    private val rect = RectF()

    var holeCx = 0f
        private set
    var holeCy = 0f
        private set
    var holeRadius = 0f
        set(value) {
            if (field != value) {
                field = value
                invalidate()
            }
        }

    fun setSnapshot(bmp: Bitmap?, cx: Float, cy: Float) {
        snapshot = bmp
        holeCx = cx
        holeCy = cy
        holeRadius = 0f
        invalidate()
    }

    fun clearSnapshot() {
        snapshot = null
        holeRadius = 0f
        invalidate()
    }

    override fun onDraw(canvas: Canvas) {
        val bmp = snapshot ?: return
        rect.set(0f, 0f, width.toFloat(), height.toFloat())
        outer.reset()
        // WINDING + 矩形顺时针、圆逆时针 = 差集（矩形挖掉圆）
        outer.fillType = Path.FillType.WINDING
        outer.addRect(rect, Path.Direction.CW)
        if (holeRadius > 0f) {
            outer.addOval(
                holeCx - holeRadius,
                holeCy - holeRadius,
                holeCx + holeRadius,
                holeCy + holeRadius,
                Path.Direction.CCW
            )
        }
        val save = canvas.save()
        canvas.clipPath(outer)
        canvas.drawBitmap(bmp, null, rect, null)
        canvas.restoreToCount(save)
    }
}
