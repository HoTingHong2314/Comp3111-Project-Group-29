package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PriceUtil;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Dialog;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.io.File;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * Renders a listing detail view and its buyer actions and seller reviews.
 */
public class ListingDetailController {

    @FXML private Label titleLabel;
    @FXML private Label typeLabel;
    @FXML private Label statusLabel;
    @FXML private Label priceLabel;
    @FXML private Label metaLabel;
    @FXML private Label meetupLabel;
    @FXML private Label postedLabel;
    @FXML private Label lendingLabel;
    @FXML private Label descriptionLabel;
    @FXML private Label sellerNameLabel;
    @FXML private Label sellerRatingLabel;
    @FXML private Label sellerBioLabel;
    @FXML private Label viewCountLabel;
    @FXML private HBox photoBox;
    @FXML private HBox actionBox;
    @FXML private VBox reviewsBox;

    private final DummyDataService data = DummyDataService.getInstance();
    private Listing listing;
    private BuyerDashboardController dashboard;

    public void setListing(Listing listing, BuyerDashboardController dashboard) {
        this.dashboard = dashboard;
        this.listing = data.openListingDetail(listing.getId());
        render();
    }

    private void render() {
        if (listing == null) {
            titleLabel.setText("Listing not found");
            return;
        }
        titleLabel.setText(listing.getTitle());
        typeLabel.setText(listing.getTypeDisplay());
        statusLabel.setText(listing.getStatus().name().replace('_', ' '));
        priceLabel.setText(PriceUtil.displayListingPrice(listing));
        priceLabel.setStyle("-fx-font-size: 20px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        metaLabel.setText("Category: " + listing.getCategory() + "   ·   Condition: " + listing.getCondition());
        meetupLabel.setText("Meet-up location: " + listing.getMeetupLocation());
        postedLabel.setText("Posted: " + listing.getPostedAt().format(
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
        lendingLabel.setText(listing.getType() == ListingType.LEND
                ? "Lending duration: " + listing.getLendingDays() + " days" : "");
        lendingLabel.setVisible(listing.getType() == ListingType.LEND);
        lendingLabel.setManaged(listing.getType() == ListingType.LEND);
        descriptionLabel.setText(listing.getDescription() == null
                ? "No description provided." : listing.getDescription());
        sellerNameLabel.setText("Seller: " + listing.getSellerFullName()
                + " (@" + listing.getSellerUsername() + ")");
        sellerRatingLabel.setText(String.format("★ %.1f / 5  (%d reviews)",
                data.getSellerAverageRating(listing.getSellerUsername()),
                data.getSellerRatingCount(listing.getSellerUsername())));
        sellerRatingLabel.setStyle("-fx-text-fill: #A68137; -fx-font-size: 13px;");
        var seller = data.findUser(listing.getSellerUsername());
        sellerBioLabel.setText(seller != null && seller.getBio() != null ? seller.getBio() : "");
        viewCountLabel.setText("Views: " + listing.getViewCount());

        styleBadge(typeLabel, listing.getType());
        styleStatusBadge(statusLabel, listing.getStatus());
        renderPhotos();
        renderActions();
        renderReviews();
    }

    private void renderPhotos() {
        photoBox.getChildren().clear();
        List<String> photos = listing.getPhotoPaths();
        if (photos == null || photos.isEmpty()) {
            Label placeholder = new Label("No photos uploaded");
            placeholder.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 13px;");
            photoBox.getChildren().add(placeholder);
            return;
        }
        photos.stream().limit(6).forEach(path -> {
            File file = new File(path);
            if (!file.exists()) return;
            ImageView imageView = new ImageView(new Image(
                    file.toURI().toString(), 150, 150, true, true));
            imageView.setFitWidth(150);
            imageView.setFitHeight(150);
            imageView.setPreserveRatio(true);
            photoBox.getChildren().add(imageView);
        });
        if (photoBox.getChildren().isEmpty()) {
            photoBox.getChildren().add(new Label("Photos could not be loaded"));
        }
    }

    private void renderActions() {
        actionBox.getChildren().clear();
        if (listing.getStatus() == ListingStatus.LIVE) {
            switch (listing.getType()) {
                case SELL -> actionBox.getChildren().addAll(
                        actionButton("Buy Now", "btn-primary", () -> dashboard.buyNow(listing)),
                        actionButton("Make Offer", "btn-outline", () -> dashboard.showOfferDialog(listing)));
                case LEND -> actionBox.getChildren().add(actionButton(
                        "Request Borrow", "btn-primary", () -> dashboard.showBorrowDialog(listing)));
                case GIVEAWAY -> actionBox.getChildren().add(actionButton(
                        "Claim Item", "btn-success", () -> dashboard.claimItem(listing)));
            }
        } else {
            Label unavailable = new Label("This listing is no longer available.");
            unavailable.setStyle("-fx-text-fill: #c62828; -fx-font-weight: bold;");
            actionBox.getChildren().add(unavailable);
        }
        actionBox.getChildren().add(actionButton(
                "Seller Profile", "btn-outline", this::showSellerProfile));
        actionBox.getChildren().add(actionButton(
                "Chat with Seller", "btn-outline", () -> dashboard.openChatForListing(listing)));
        actionBox.getChildren().add(actionButton(
                "Report Listing", "btn-danger", () -> dashboard.reportListing(listing)));
    }

    private Button actionButton(String text, String styleClass, Runnable action) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClass, "btn-sm");
        button.setOnAction(event -> action.run());
        return button;
    }

    private void showSellerProfile() {
        User seller = data.findUser(listing.getSellerUsername());
        if (seller == null) return;
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Seller Profile");
        dialog.setHeaderText(seller.getFullName() + " (@" + seller.getUsername() + ")");
        dialog.getDialogPane().getButtonTypes().add(ButtonType.CLOSE);

        VBox content = new VBox(8);
        content.setPadding(new Insets(8));
        Label rating = new Label(String.format("Average rating: %.1f / 5 (%d reviews)",
                data.getSellerAverageRating(seller.getUsername()),
                data.getSellerRatingCount(seller.getUsername())));
        rating.setStyle("-fx-font-weight: bold; -fx-text-fill: #A68137;");
        Label bio = new Label(seller.getBio() == null ? "No bio provided." : seller.getBio());
        bio.setWrapText(true);
        content.getChildren().addAll(rating, bio, new Label("Reviews"));

        List<Review> reviews = data.getReviewsForSeller(seller.getUsername());
        if (reviews.isEmpty()) {
            content.getChildren().add(new Label("No reviews yet."));
        } else {
            for (Review review : reviews) {
                VBox box = new VBox(2);
                Label stars = new Label("★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating()));
                stars.setStyle("-fx-text-fill: #A68137;");
                Label comment = new Label(review.getComment());
                comment.setWrapText(true);
                Label meta = new Label("by @" + review.getBuyerUsername() + " · "
                        + review.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                box.getChildren().addAll(stars, comment, meta);
                content.getChildren().add(box);
            }
        }
        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.setPrefSize(480, 420);
        dialog.getDialogPane().setContent(scroll);
        dialog.showAndWait();
    }

    private void renderReviews() {
        reviewsBox.getChildren().clear();
        List<Review> reviews = data.getReviewsForSeller(listing.getSellerUsername());
        if (reviews.isEmpty()) {
            Label empty = new Label("No reviews yet.");
            empty.setStyle("-fx-text-fill: #5a5a7a;");
            reviewsBox.getChildren().add(empty);
            return;
        }
        for (Review review : reviews) {
            VBox box = new VBox(4);
            box.setStyle("-fx-padding: 8; -fx-background-color: #f8fafc; -fx-background-radius: 6;");
            Label stars = new Label("★".repeat(review.getRating())
                    + "☆".repeat(5 - review.getRating()));
            stars.setStyle("-fx-text-fill: #A68137;");
            Label comment = new Label(review.getComment());
            comment.setWrapText(true);
            Label meta = new Label("by @" + review.getBuyerUsername() + " · "
                    + review.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
            meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
            box.getChildren().addAll(stars, comment, meta);
            if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
                Label reply = new Label("Seller reply: " + review.getSellerReply());
                reply.setWrapText(true);
                reply.setStyle("-fx-text-fill: #003974; -fx-font-size: 11px;");
                box.getChildren().add(reply);
            }
            reviewsBox.getChildren().add(box);
        }
    }

    private void styleBadge(Label label, ListingType type) {
        label.getStyleClass().add("badge");
        if (type == ListingType.SELL) label.getStyleClass().add("badge-sold");
        else if (type == ListingType.LEND) label.getStyleClass().add("badge-pending");
        else label.getStyleClass().add("badge-live");
    }

    private void styleStatusBadge(Label label, ListingStatus status) {
        label.getStyleClass().add("badge");
        switch (status) {
            case LIVE -> label.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> label.getStyleClass().add("badge-pending");
            case REJECTED, REMOVED -> label.getStyleClass().add("badge-rejected");
            default -> label.getStyleClass().add("badge-sold");
        }
    }

    @FXML
    private void goBack() {
        if (dashboard != null) {
            dashboard.showBrowse();
        }
    }
}
