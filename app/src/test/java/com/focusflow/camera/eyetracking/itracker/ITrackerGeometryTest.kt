package com.focusflow.camera.eyetracking.itracker

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Pins the crop conventions shared with tools/itracker/preprocess.py. The
 * "centred" and "off-corner" cases use the same synthetic landmark layout as
 * test_preprocess.py, so the two implementations can be compared number for
 * number.
 */
class ITrackerGeometryTest {

    /** 478 points in a face-sized blob with the eye contours placed where eyes would be. */
    private fun fakeLandmarks(cx: Float, cy: Float, faceW: Float): Pair<FloatArray, FloatArray> {
        val xs = FloatArray(478)
        val ys = FloatArray(478)
        val rnd = java.util.Random(3)
        for (i in 0 until 478) {
            xs[i] = cx - faceW / 2 + rnd.nextFloat() * faceW
            ys[i] = cy - faceW * 0.55f + rnd.nextFloat() * faceW * 1.1f
        }
        val eyeY = cy - faceW * 0.12f
        for (i in ITrackerGeometry.LEFT_EYE_CONTOUR) { xs[i] = cx + faceW * 0.20f; ys[i] = eyeY }
        for (i in ITrackerGeometry.RIGHT_EYE_CONTOUR) { xs[i] = cx - faceW * 0.20f; ys[i] = eyeY }
        return xs to ys
    }

    @Test
    fun roundHalfUp_matchesPythonFloorPlusHalf() {
        assertEquals(2, ITrackerGeometry.roundHalfUp(1.5f))
        assertEquals(3, ITrackerGeometry.roundHalfUp(2.5f))   // not banker's rounding
        assertEquals(-1, ITrackerGeometry.roundHalfUp(-1.5f)) // floor(-1.0) = -1
        assertEquals(1, ITrackerGeometry.roundHalfUp(1.49f))
    }

    @Test
    fun faceBox_isSquareGrownAndShiftedUp() {
        val (xs, ys) = fakeLandmarks(320f, 240f, 200f)
        val box = ITrackerGeometry.faceBox(xs, ys)
        assertEquals("square", box.w, box.h)
        // Landmark extent is ~200 wide, ~220 tall -> side ≈ 220 * 1.15 ≈ 253
        assertTrue("grown past the landmark extent", box.w in 240..262)
        val extentCy = 240f
        val boxCy = box.y + box.h / 2f
        assertTrue("shifted upward to include the forehead", boxCy < extentCy)
    }

    @Test
    fun eyeBoxes_subjectLeftIsOnImageRight() {
        val (xs, ys) = fakeLandmarks(320f, 240f, 200f)
        val boxes = ITrackerGeometry.boxes(xs, ys)
        assertTrue(boxes.eyeLeft.x > boxes.eyeRight.x)
        assertEquals(boxes.eyeLeft.w, boxes.eyeLeft.h)
        assertEquals(ITrackerGeometry.roundHalfUp(boxes.face.w * ITrackerGeometry.EYE_BOX_FRAC_OF_FACE), boxes.eyeLeft.w)
    }

    @Test
    fun faceGrid_isRowMajorAndLightsTheMiddleForACentredFace() {
        val (xs, ys) = fakeLandmarks(320f, 240f, 200f)
        val face = ITrackerGeometry.faceBox(xs, ys)
        val grid = ITrackerGeometry.faceGrid(640, 480, face)
        assertEquals(ITrackerGeometry.GRID_LENGTH, grid.size)
        var lit = 0; var sx = 0f; var sy = 0f
        for (i in grid.indices) if (grid[i] == 1f) { lit++; sx += i % 25; sy += i / 25 }
        assertTrue("some cells lit", lit > 0)
        assertTrue("centred in x", sx / lit in 8f..16f)
        assertTrue("centred in y", sy / lit in 8f..16f)
        // Row-major: a lit cell at (x, y) is at index y*25+x, so the lit set is
        // a contiguous run within each row.
        for (y in 0 until 25) {
            val row = (0 until 25).map { grid[y * 25 + it] }
            val first = row.indexOf(1f); val last = row.lastIndexOf(1f)
            if (first >= 0) assertTrue("contiguous row $y", row.subList(first, last + 1).all { it == 1f })
        }
    }

    @Test
    fun faceGrid_clampsABoxThatLeavesTheFrame() {
        val (xs, ys) = fakeLandmarks(40f, 30f, 200f)
        val face = ITrackerGeometry.faceBox(xs, ys)
        assertTrue(face.x < 0 && face.y < 0)
        val grid = ITrackerGeometry.faceGrid(640, 480, face)
        assertEquals(1f, grid[0])            // top-left cell lit
        assertTrue(grid.count { it == 1f } > 0)
        assertTrue(grid.all { it == 0f || it == 1f })
    }

    @Test
    fun faceGrid_entirelyOutsideTheFrameIsAllZero() {
        val grid = ITrackerGeometry.faceGrid(640, 480, ITrackerGeometry.Box(-500, -500, 100, 100))
        assertTrue(grid.all { it == 0f })
    }
}
