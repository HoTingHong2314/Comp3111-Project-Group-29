package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.scene.control.*;

/**
 * Handles seller self-registration with profile and password validation.
 */
public class SellerRegisterController {

    @FXML private TextField usernameField;
    @FXML private TextField fullNameField;
    @FXML private PasswordField passwordField;
    @FXML private PasswordField confirmPasswordField;
    @FXML private ComboBox<String> meetupCombo;
    @FXML private TextArea bioArea;
    @FXML private Label errorLabel;
    @FXML private Label successLabel;

    @FXML
    public void initialize() {
        meetupCombo.setItems(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
    }

    @FXML
    private void handleRegister() {
        hideMessages();
        String username = usernameField.getText().trim();
        String fullName = fullNameField.getText().trim();
        String password = passwordField.getText();
        String confirm = confirmPasswordField.getText();

        try {
            DummyDataService.getInstance().registerSeller(
                    username, fullName, password, confirm,
                    meetupCombo.getValue(), bioArea.getText());
            showSuccess("Seller account created! You can now login.");
            usernameField.clear();
            fullNameField.clear();
            passwordField.clear();
            confirmPasswordField.clear();
            meetupCombo.getSelectionModel().clearSelection();
            bioArea.clear();
        } catch (IllegalArgumentException | IllegalStateException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg); errorLabel.setVisible(true); errorLabel.setManaged(true);
    }
    private void showSuccess(String msg) {
        successLabel.setText(msg); successLabel.setVisible(true); successLabel.setManaged(true);
    }
    private void hideMessages() {
        errorLabel.setVisible(false); errorLabel.setManaged(false);
        successLabel.setVisible(false); successLabel.setManaged(false);
    }

    @FXML private void goLogin() { SceneManager.showSellerLogin(); }
    @FXML private void goWelcome() { SceneManager.showWelcome(); }
}
