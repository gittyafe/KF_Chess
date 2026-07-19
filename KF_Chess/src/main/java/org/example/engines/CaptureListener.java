package org.example.engines;

/**
 * מאזין להתרחשות של אכילת כלי (capture). מאפשר לשכבת התצוגה (ניקוד וכו')
 * להגיב לאירועי אכילה בלי ש-GameEngine יכיר את PlayerInfo/ScoreManager וכו'.
 */
public interface CaptureListener {
    /**
     * @param capturedType   סוג הכלי שנאכל ('P','N','B','R','Q','K')
     * @param capturingColor הצבע של הכלי שביצע את האכילה ('w' / 'b')
     */
    void onPieceCaptured(char capturedType, char capturingColor);
}
