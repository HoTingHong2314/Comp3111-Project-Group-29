package com.hkust.marketplace.controllers;

import com.hkust.marketplace.utils.BorrowLifecycleScheduler;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonBar;
import javafx.scene.control.ButtonType;

import java.util.Optional;

/**
 * Controller for the role selection landing screen.
 */
public class WelcomeController {

    @FXML
    private void goBuyer() {
        System.out.println("[Welcome] goBuyer()");
        SceneManager.showBuyerLogin();
    }

    @FXML
    private void goSeller() {
        System.out.println("[Welcome] goSeller()");
        SceneManager.showSellerLogin();
    }

    @FXML
    private void goAdmin() {
        System.out.println("[Welcome] goAdmin()");
        SceneManager.showAdminLogin();
    }

    @FXML
    private void resetDatabase() {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Reset Database");
        confirm.setHeaderText("Restore the default marketplace data?");
        confirm.setContentText("This will permanently remove all local users, listings, "
                + "deals, chats, notifications, reviews and reports, then restore the seeded demo data.");

        ButtonType reset = new ButtonType("Reset Database", ButtonBar.ButtonData.OK_DONE);
        ButtonType cancel = new ButtonType("Cancel", ButtonBar.ButtonData.CANCEL_CLOSE);
        confirm.getButtonTypes().setAll(reset, cancel);
        Button resetButton = (Button) confirm.getDialogPane().lookupButton(reset);
        Button cancelButton = (Button) confirm.getDialogPane().lookupButton(cancel);
        resetButton.setDefaultButton(false);
        cancelButton.setDefaultButton(true);

        Optional<ButtonType> result = confirm.showAndWait();
        if (result.isEmpty() || result.get() != reset) {
            return;
        }

        try {
            DummyDataService.getInstance().resetToDefaults();
            SessionManager.logout();
            BorrowLifecycleScheduler.setOnChangeListener(null);

            Alert success = new Alert(Alert.AlertType.INFORMATION);
            success.setTitle("Database Reset");
            success.setHeaderText("Default marketplace data restored");
            success.setContentText("All local marketplace data has been reset to the original seed data.");
            success.showAndWait();
            SceneManager.showWelcome();
        } catch (RuntimeException ex) {
            Alert error = new Alert(Alert.AlertType.ERROR);
            error.setTitle("Database Reset Failed");
            error.setHeaderText("Could not reset the marketplace database");
            error.setContentText(ex.getMessage());
            error.showAndWait();
        }
    }
}
