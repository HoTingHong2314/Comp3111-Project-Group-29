package com.hkust.marketplace.utils;

import javafx.animation.Animation;
import javafx.animation.KeyFrame;
import javafx.animation.Timeline;
import javafx.util.Duration;

/**
 * Periodically processes expired borrows and due reminders on the JavaFX thread.
 */
public final class BorrowLifecycleScheduler {

    private static Timeline timeline;
    private static Runnable onChangeListener;

    private BorrowLifecycleScheduler() {
    }

    public static void start() {
        if (timeline != null) {
            return;
        }
        timeline = new Timeline(new KeyFrame(Duration.seconds(1), event -> tick()));
        timeline.setCycleCount(Animation.INDEFINITE);
        timeline.play();
    }

    public static void stop() {
        if (timeline != null) {
            timeline.stop();
            timeline = null;
        }
    }

    public static void setOnChangeListener(Runnable listener) {
        onChangeListener = listener;
    }

    private static void tick() {
        DummyDataService data = DummyDataService.getInstance();
        int changed = data.processExpiredBorrows() + data.processDueReminders();
        if (changed > 0 && onChangeListener != null) {
            onChangeListener.run();
        }
    }
}