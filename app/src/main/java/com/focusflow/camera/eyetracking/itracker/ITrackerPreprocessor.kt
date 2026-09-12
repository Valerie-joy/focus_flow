package com.focusflow.camera.eyetracking.itracker

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import java.nio.ByteBuffer
import java.nio.ByteOrder

/**
 * Turns an upright camera frame plus its 478 landmarks into iTracker's four
 * input tensors, matching the training-time preprocessing exactly:
 *
 *  * each box is cropped with zero (black) padding where it leaves the frame
 *    (`prepareDataset.cropImage`), then resized to 224x224 — done here as a
 *    single filtered Canvas draw of the in-frame part of the box into the
 *    corresponding part of a black 224x224 bitmap, which is the same thing
 *    without an intermediate native-resolution copy;
 *  * pixels become float RGB on 0..1, channels-last, exactly as the TFLite
 *    graph expects — the per-pathway mean-image subtraction lives *inside*
 *    the exported graph, so nothing else is done to the pixels;
 *  * the face grid comes from [ITrackerGeometry.faceGrid].
 *
 * Buffers are allocated once and reused; the caller must consume the
 * returned [Inputs] before the next call. Not thread-safe.
 *
 * [lastFaceCrop] keeps the most recent 224x224 face crop so a debug view can
 * show it — the single most effective check that the rotation, mirroring
 * and box conventions are right on a real device.
 */
class ITrackerPreprocessor {
    private val size = ITrackerGeometry.INPUT_SIZE
    private val pixelCount = size * size

    private val faceBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val leftBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val rightBmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888)
    private val pixels = IntArray(pixelCount)
    private val paint = Paint(Paint.FILTER_BITMAP_FLAG)

    private fun imageBuffer() = ByteBuffer.allocateDirect(pixelCount * 3 * 4).order(ByteOrder.nativeOrder())
    private val faceBuf = imageBuffer()
    private val leftBuf = imageBuffer()
    private val rightBuf = imageBuffer()
    private val gridBuf: ByteBuffer = ByteBuffer.allocateDirect(ITrackerGeometry.GRID_LENGTH * 4).order(ByteOrder.nativeOrder())

    val lastFaceCrop: Bitmap get() = faceBmp

    class Inputs(
        val face: ByteBuffer,
        val eyeLeft: ByteBuffer,
        val eyeRight: ByteBuffer,
        val faceGrid: ByteBuffer,
        val boxes: ITrackerGeometry.Boxes
    )

    /**
     * @param frame   the camera frame rotated upright (the space the landmarks are in)
     * @param xs, ys  landmark pixel coordinates in [frame]'s space, 478 each
     */
    fun prepare(frame: Bitmap, xs: FloatArray, ys: FloatArray): Inputs {
        val boxes = ITrackerGeometry.boxes(xs, ys)
        crop(frame, boxes.face, faceBmp, faceBuf)
        crop(frame, boxes.eyeLeft, leftBmp, leftBuf)
        crop(frame, boxes.eyeRight, rightBmp, rightBuf)

        val grid = ITrackerGeometry.faceGrid(frame.width, frame.height, boxes.face)
        gridBuf.rewind()
        for (v in grid) gridBuf.putFloat(v)
        gridBuf.rewind()

        return Inputs(faceBuf, leftBuf, rightBuf, gridBuf, boxes)
    }

    private fun crop(src: Bitmap, box: ITrackerGeometry.Box, dst: Bitmap, out: ByteBuffer) {
        val canvas = Canvas(dst)
        canvas.drawColor(Color.BLACK)

        // In-frame part of the box (source) and where it lands in the 224² crop (dest).
        val ax = maxOf(box.x, 0)
        val ay = maxOf(box.y, 0)
        val bx = minOf(box.x + box.w, src.width)
        val by = minOf(box.y + box.h, src.height)
        if (bx > ax && by > ay && box.w > 0 && box.h > 0) {
            val scaleX = size.toFloat() / box.w
            val scaleY = size.toFloat() / box.h
            val dstRect = Rect(
                ((ax - box.x) * scaleX).toInt(),
                ((ay - box.y) * scaleY).toInt(),
                ((bx - box.x) * scaleX + 0.5f).toInt(),
                ((by - box.y) * scaleY + 0.5f).toInt()
            )
            canvas.drawBitmap(src, Rect(ax, ay, bx, by), dstRect, paint)
        }

        dst.getPixels(pixels, 0, size, 0, 0, size, size)
        out.rewind()
        for (p in pixels) {
            out.putFloat(((p shr 16) and 0xFF) / 255f)
            out.putFloat(((p shr 8) and 0xFF) / 255f)
            out.putFloat((p and 0xFF) / 255f)
        }
        out.rewind()
    }
}
