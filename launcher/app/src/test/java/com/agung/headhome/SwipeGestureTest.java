package com.agung.headhome;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public class SwipeGestureTest {

    private static final float D = 1f; // density 1: pixels equal dp

    @Test
    public void leftSwipeNeedsAtLeast120dp() {
        assertTrue(SwipeGesture.isLeftSwipe(-120f, 0f, 200, D));
        assertFalse(SwipeGesture.isLeftSwipe(-119f, 0f, 200, D));
        assertFalse(SwipeGesture.isLeftSwipe(150f, 0f, 200, D));
    }

    @Test
    public void rightSwipeIsTheMirror() {
        assertTrue(SwipeGesture.isRightSwipe(120f, 10f, 200, D));
        assertFalse(SwipeGesture.isRightSwipe(-200f, 0f, 200, D));
    }

    @Test
    public void mustBeMoreThanTwiceAsHorizontalAsVertical() {
        assertTrue(SwipeGesture.isLeftSwipe(-241f, 120f, 200, D));
        assertFalse(SwipeGesture.isLeftSwipe(-240f, 120f, 200, D));
        assertFalse(SwipeGesture.isLeftSwipe(-200f, -150f, 200, D));
    }

    @Test
    public void mustBeWithin600Ms() {
        assertTrue(SwipeGesture.isLeftSwipe(-200f, 0f, 600, D));
        assertFalse(SwipeGesture.isLeftSwipe(-200f, 0f, 601, D));
    }

    @Test
    public void distanceScalesWithDensity() {
        assertFalse(SwipeGesture.isLeftSwipe(-200f, 0f, 200, 2f));
        assertTrue(SwipeGesture.isLeftSwipe(-240f, 0f, 200, 2f));
    }
}
