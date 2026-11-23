package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PriceUtil;
import com.hkust.marketplace.utils.SessionManager;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.VBox;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Listing-linked buyer/seller chat with search and formal offer submission.
 */
public class ChatController {

    @FXML private Label titleLabel;
    @FXML private Label subtitleLabel;
    @FXML private TextField searchField;
    @FXML private ScrollPane messageScroll;
    @FXML private VBox messageBox;
    @FXML private TextField inputField;
    @FXML private javafx.scene.control.Button formalOfferButton;

    private final DummyDataService data = DummyDataService.getInstance();
    private DummyDataService.ChatThreadInfo thread;
    private BuyerDashboardController dashboard;
    private Runnable onBack;

    public void setThread(DummyDataService.ChatThreadInfo thread,
                          BuyerDashboardController dashboard, Runnable onBack) {
        this.dashboard = dashboard;
        initThread(thread, onBack);
    }

    public void setThread(DummyDataService.ChatThreadInfo thread, Runnable onBack) {
        this.dashboard = null;
        initThread(thread, onBack);
    }

    private void initThread(DummyDataService.ChatThreadInfo thread, Runnable onBack) {
        this.thread = thread;
        this.onBack = onBack;
        titleLabel.setText(thread.listingTitle());
        User current = SessionManager.getCurrentUser();
        boolean isBuyer = current != null
                && current.getUsername().equalsIgnoreCase(thread.buyerUsername());
        subtitleLabel.setText("Chat with @" + (isBuyer
                ? thread.sellerUsername() : thread.buyerUsername()) + " about this listing");
        Listing listing = data.findListing(thread.listingId());
        boolean offerAllowed = isBuyer && listing != null
                && listing.getStatus() == ListingStatus.LIVE
                && listing.getType() != ListingType.GIVEAWAY;
        formalOfferButton.setVisible(offerAllowed);
        formalOfferButton.setManaged(offerAllowed);
        renderMessages(data.getChatMessages(thread.id()));
        searchField.textProperty().addListener((obs, oldValue, newValue) ->
                renderMessages(data.searchMessages(thread.id(), newValue)));
        inputField.setOnAction(event -> sendMessage());
    }

    private void renderMessages(List<DummyDataService.ChatMessage> messages) {
        messageBox.getChildren().clear();
        User current = SessionManager.getCurrentUser();
        if (messages.isEmpty()) {
            Label empty = new Label("No messages yet. Say hello!");
            empty.setStyle("-fx-text-fill: #9aa0b0;");
            messageBox.getChildren().add(empty);
            return;
        }
        for (DummyDataService.ChatMessage message : messages) {
            boolean mine = current != null
                    && message.senderUsername().equalsIgnoreCase(current.getUsername());
            VBox bubble = new VBox(3);
            bubble.setMaxWidth(520);
            bubble.setPadding(new Insets(8, 12, 8, 12));
            bubble.setStyle("-fx-background-color: " + (mine ? "#dcecff" : "#ffffff")
                    + "; -fx-background-radius: 10; -fx-border-color: #d0d7e2;"
                    + " -fx-border-radius: 10;");
            Label sender = new Label(mine ? "You" : message.senderUsername());
            sender.setStyle("-fx-font-size: 11px; -fx-font-weight: bold; -fx-text-fill: #003974;");
            Label content = new Label(message.content());
            content.setWrapText(true);
            Label meta = new Label(message.type()
                    + " · " + message.createdAt().format(
                    DateTimeFormatter.ofPattern("dd MMM HH:mm:ss")));
            meta.setStyle("-fx-font-size: 10px; -fx-text-fill: #9aa0b0;");
            bubble.getChildren().addAll(sender, content, meta);

            HBox row = new HBox(bubble);
            row.setAlignment(mine ? Pos.CENTER_RIGHT : Pos.CENTER_LEFT);
            Region spacer = new Region();
            HBox.setHgrow(spacer, Priority.ALWAYS);
            if (mine) {
                row.getChildren().add(0, spacer);
            } else {
                row.getChildren().add(spacer);
            }
            messageBox.getChildren().add(row);
        }
        messageScroll.setVvalue(1.0);
    }

    @FXML
    private void sendMessage() {
        String text = inputField.getText();
        if (text == null || text.isBlank()) return;
        try {
            data.sendMessage(SessionManager.getCurrentUser(), thread.id(), text);
            inputField.clear();
            renderMessages(data.getChatMessages(thread.id()));
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void sendFormalOffer() {
        Listing listing = data.findListing(thread.listingId());
        if (listing == null) {
            showError("This listing no longer exists.");
            return;
        }
        try {
            if (listing.getType() == ListingType.SELL) {
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Formal Offer");
                dialog.setHeaderText("Propose a price for: " + listing.getTitle()
                        + "\nAsking price: " + PriceUtil.displayListingPrice(listing));
                dialog.setContentText("Offer price (must be below the asking price):");
                dialog.showAndWait().ifPresent(value -> {
                    try {
                        double price = PriceUtil.parse(value);
                        data.sendFormalOffer(SessionManager.getCurrentUser(),
                                thread.id(), price, null, null);
                        renderMessages(data.getChatMessages(thread.id()));
                    } catch (NumberFormatException ex) {
                        showError("Invalid price.");
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                });
            } else if (listing.getType() == ListingType.LEND) {
                LocalDateTime[] period = dashboard.askBorrowPeriod(listing);
                if (period == null) return;
                data.sendFormalOffer(SessionManager.getCurrentUser(),
                        thread.id(), null, period[0], period[1]);
                renderMessages(data.getChatMessages(thread.id()));
            } else {
                showError("Giveaways do not need a formal offer.");
            }
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    private void goBack() {
        if (onBack != null) {
            onBack.run();
        }
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "Something went wrong." : message);
        alert.showAndWait();
    }
}