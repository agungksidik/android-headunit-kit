package com.agung.headhome;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public class TurnDetectorTest {

    @Test
    public void straightBelowTenKmh() {
        TurnDetector d = new TurnDetector();
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(10, 9, 0));
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(40, 9, 1000));
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(90, 9, 2000));
    }

    @Test
    public void rightTurn() {
        TurnDetector d = new TurnDetector();
        d.update(10, 40, 0);
        d.update(16, 40, 1000);
        assertEquals(TurnDetector.Turn.RIGHT, d.update(22, 40, 2000));
    }

    @Test
    public void leftTurnAcrossNorthWrap() {
        TurnDetector d = new TurnDetector();
        d.update(5, 40, 0);
        d.update(359, 40, 1000);
        assertEquals(TurnDetector.Turn.LEFT, d.update(353, 40, 2000));
    }

    @Test
    public void steadyBearingStaysStraight() {
        TurnDetector d = new TurnDetector();
        d.update(100, 50, 0);
        d.update(101, 50, 1000);
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(100, 50, 2000));
    }

    @Test
    public void slowingDownResetsHistory() {
        TurnDetector d = new TurnDetector();
        d.update(10, 40, 0);
        d.update(16, 40, 1000);
        d.update(22, 40, 2000);
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(200, 5, 3000));
        assertEquals(TurnDetector.Turn.STRAIGHT, d.update(300, 40, 4000));
    }

    @Test
    public void signedDeltaWraps() {
        assertEquals(2.0, TurnDetector.signedDelta(359, 1), 1e-9);
        assertEquals(-2.0, TurnDetector.signedDelta(1, 359), 1e-9);
        assertEquals(6.0, TurnDetector.signedDelta(10, 16), 1e-9);
    }
}
