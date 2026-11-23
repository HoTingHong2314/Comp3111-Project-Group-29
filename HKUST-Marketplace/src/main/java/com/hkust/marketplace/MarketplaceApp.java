package com.hkust.marketplace;

import com.hkust.marketplace.utils.BorrowLifecycleScheduler;
import com.hkust.marketplace.utils.SceneManager;
import javafx.application.Application;
import javafx.stage.Stage;

/**
 * Entry point for the HKUST Marketplace application.
 */
public class MarketplaceApp extends Application {

    @Override
    public void start(Stage primaryStage) {
        SceneManager.init(primaryStage);
        BorrowLifecycleScheduler.start();
        SceneManager.showWelcome();
        primaryStage.setTitle("HKUST Marketplace");
        primaryStage.setMinWidth(1100);
        primaryStage.setMinHeight(700);
        primaryStage.show();
    }

    public static void main(String[] args) {
        launch(args);
    }

    @Override
    public void stop() {
        BorrowLifecycleScheduler.stop();
    }
}
