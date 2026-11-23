package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

/**
 * Handles buyer authentication and navigation into the buyer portal.
 */
public class BuyerLoginController {

    @FXML private TextField usernameField;
    @FXML private PasswordField passwordField;
    @FXML private Label errorLabel;

    @FXML
    private void handleLogin() {
        String username = usernameField.getText().trim();
        String password = passwordField.getText();

        errorLabel.setVisible(false);
        errorLabel.setManaged(false);

        if (username.isEmpty() || password.isEmpty()) {
            showError("Please enter both username and password.");
            return;
        }

        try {
            User user = DummyDataService.getInstance()
                    .login(username, password, UserRole.BUYER);
            SessionManager.login(user);
            SceneManager.showBuyerDashboard();
        } catch (IllegalArgumentException | IllegalStateException | SecurityException ex) {
            showError(ex.getMessage());
        }
    }

    private void showError(String msg) {
        errorLabel.setText(msg);
        errorLabel.setVisible(true);
        errorLabel.setManaged(true);
    }

    @FXML
    private void goRegister() {
        SceneManager.showBuyerRegister();
    }

    @FXML
    private void goWelcome() {
        SceneManager.showWelcome();
    }
}
