package com.hkust.marketplace.controllers.seller;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DealStatuses;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PriceUtil;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.chart.BarChart;
import javafx.scene.chart.CategoryAxis;
import javafx.scene.chart.NumberAxis;
import javafx.scene.chart.PieChart;
import javafx.scene.chart.XYChart;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Seller portal controller for listings, offers, chats, statistics and reviews.
 */
public class SellerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnListings, btnCreate, btnOffers, btnChats, btnStats, btnReviews, btnNotifications, btnProfile;

    private final DummyDataService data = DummyDataService.getInstance();
    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = currentUser();
        if (user != null) welcomeLabel.setText("Hello, " + user.getFullName());
        currentActiveBtn = btnListings;
        showMyListings();
    }

    private User currentUser() {
        return SessionManager.getCurrentUser();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    @FXML
    private void showMyListings() {
        setActive(btnListings, "My Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = currentUser();
        List<Listing> myListings = data.getListingsBySeller(user == null ? "" : user.getUsername());

        root.getChildren().add(infoLabel("Editing or deleting is blocked while a listing has an active offer or is Reserved/Sold/Lent/Given."));
        if (myListings.isEmpty()) {
            root.getChildren().add(infoLabel("You have not posted any listings yet."));
        } else {
            for (Listing listing : myListings) {
                root.getChildren().add(createListingRow(listing));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createListingRow(Listing listing) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(14));

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label(listing.getCategory() + " · " + listing.getTypeDisplay()
                + " · " + PriceUtil.displayListingPrice(listing) + " · Views: " + listing.getViewCount());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label location = new Label("Meet-up: " + listing.getMeetupLocation());
        location.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        info.getChildren().addAll(title, meta, location);

        Label status = statusBadge(listing.getStatus());

        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(event -> showListingForm(listing));

        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-sm");
        delete.setOnAction(event -> deleteListing(listing));

        row.getChildren().addAll(info, status);
        if (data.isListingEditable(listing.getId())) {
            row.getChildren().addAll(edit, delete);
        } else if (listing.getStatus() == ListingStatus.SOLD
                || listing.getStatus() == ListingStatus.GIVEN) {
            row.getChildren().add(infoLabel("Final status - no further action"));
        } else if (listing.getStatus() != ListingStatus.REMOVED) {
            row.getChildren().add(infoLabel("Locked by an active deal"));
        }
        return row;
    }

    private void deleteListing(Listing listing) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Delete '" + listing.getTitle() + "'?\nThis cannot be undone.");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    data.deleteSellerListing(currentUser(), listing.getId());
                    showMyListings();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    }

    @FXML
    private void showCreateListing() {
        showListingForm(null);
    }

    private void showListingForm(Listing existing) {
        setActive(btnCreate, existing == null ? "Create New Listing" : "Edit Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(10);
        form.setMaxWidth(620);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(18));

        Label title = new Label(existing == null ? "Post a new item" : "Edit listing");
        title.getStyleClass().add("section-title");

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setPromptText("Listing type *");
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) {
            typeCombo.setValue(existing.getType() == ListingType.SELL ? "Sell"
                    : existing.getType() == ListingType.LEND ? "Lend" : "Give Away");
        }

        TextField nameField = new TextField(existing == null ? "" : existing.getTitle());
        nameField.setPromptText("Item name *");
        ComboBox<String> categoryCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        categoryCombo.setPromptText("Category *");
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) categoryCombo.setValue(existing.getCategory());
        ComboBox<String> conditionCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        conditionCombo.setPromptText("Condition *");
        conditionCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) conditionCombo.setValue(existing.getCondition());

        TextArea descriptionArea = new TextArea(existing == null ? "" : existing.getDescription());
        descriptionArea.setPromptText("Description");
        descriptionArea.setPrefRowCount(3);

        TextField priceField = new TextField(existing == null || existing.getPrice() == null
                ? "" : String.valueOf(existing.getPrice()));
        priceField.setPromptText("Price (HK$) * for Sell");
        TextField daysField = new TextField(existing == null || existing.getLendingDays() == null
                ? "" : String.valueOf(existing.getLendingDays()));
        daysField.setPromptText("Lending duration (days) * for Lend");

        ComboBox<String> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetupCombo.setPromptText("Meet-up location *");
        meetupCombo.setMaxWidth(Double.MAX_VALUE);
        if (existing != null) meetupCombo.setValue(existing.getMeetupLocation());

        List<String> photoPaths = new ArrayList<>(existing == null ? List.of() : existing.getPhotoPaths());
        Label photoLabel = new Label("Photos: " + photoPaths.size() + " / 6 selected");
        photoLabel.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Button choosePhotos = new Button("Choose Photos");
        choosePhotos.getStyleClass().addAll("btn-outline", "btn-sm");
        choosePhotos.setOnAction(event -> {
            FileChooser chooser = new FileChooser();
            chooser.setTitle("Choose up to 6 listing photos");
            chooser.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                    "Images", "*.png", "*.jpg", "*.jpeg", "*.gif"));
            List<File> files = chooser.showOpenMultipleDialog(contentPane.getScene().getWindow());
            if (files != null) {
                photoPaths.clear();
                files.stream().limit(6).forEach(file -> photoPaths.add(file.getAbsolutePath()));
                photoLabel.setText("Photos: " + photoPaths.size() + " / 6 selected");
            }
        });

        Runnable updateTypeFields = () -> {
            boolean sell = "Sell".equals(typeCombo.getValue());
            boolean lend = "Lend".equals(typeCombo.getValue());
            priceField.setDisable(!sell);
            daysField.setDisable(!lend);
            if (!sell) priceField.clear();
            if (!lend) daysField.clear();
        };
        typeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateTypeFields.run());
        updateTypeFields.run();

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button submit = new Button(existing == null ? "Submit for Review" : "Save and Resubmit");
        submit.getStyleClass().add("btn-gold");
        submit.setOnAction(event -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            try {
                Listing listing = existing == null ? new Listing() : copyListing(existing);
                listing.setType(mapListingType(typeCombo.getValue()));
                listing.setTitle(nameField.getText());
                listing.setDescription(descriptionArea.getText());
                listing.setCategory(categoryCombo.getValue());
                listing.setCondition(conditionCombo.getValue());
                listing.setMeetupLocation(meetupCombo.getValue());
                listing.setPhotoPaths(new ArrayList<>(photoPaths));
                if (listing.getType() == ListingType.SELL) {
                    listing.setPrice(PriceUtil.parse(priceField.getText()));
                    listing.setLendingDays(null);
                } else if (listing.getType() == ListingType.LEND) {
                    listing.setPrice(null);
                    listing.setLendingDays(Integer.parseInt(daysField.getText().trim()));
                } else {
                    listing.setPrice(null);
                    listing.setLendingDays(null);
                }
                if (existing == null) {
                    data.createSellerListing(currentUser(), listing);
                } else {
                    data.updateSellerListing(currentUser(), listing);
                }
                showInfo(existing == null
                        ? "Listing submitted for admin review."
                        : "Listing updated and resubmitted for admin review.");
                showMyListings();
            } catch (NumberFormatException ex) {
                feedback.setText("Please enter a valid price / lending duration.");
                feedback.setStyle("-fx-text-fill: #c62828;");
                feedback.setVisible(true);
                feedback.setManaged(true);
            } catch (RuntimeException ex) {
                feedback.setText(ex.getMessage());
                feedback.setStyle("-fx-text-fill: #c62828;");
                feedback.setVisible(true);
                feedback.setManaged(true);
            }
        });

        form.getChildren().addAll(title,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), categoryCombo,
                new Label("Condition *"), conditionCombo,
                new Label("Description"), descriptionArea,
                new Label("Price (Sell only)"), priceField,
                new Label("Lending Days (Lend only)"), daysField,
                new Label("Meet-up Location *"), meetupCombo,
                new HBox(8, choosePhotos, photoLabel),
                submit, feedback);

        ScrollPane scroll = new ScrollPane(form);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private Listing copyListing(Listing source) {
        Listing copy = new Listing();
        copy.setId(source.getId());
        copy.setSellerUsername(source.getSellerUsername());
        copy.setSellerFullName(source.getSellerFullName());
        copy.setPostedAt(source.getPostedAt());
        copy.setViewCount(source.getViewCount());
        copy.setStatus(source.getStatus());
        return copy;
    }

    private ListingType mapListingType(String value) {
        if ("Sell".equals(value)) return ListingType.SELL;
        if ("Lend".equals(value)) return ListingType.LEND;
        if ("Give Away".equals(value)) return ListingType.GIVEAWAY;
        throw new IllegalArgumentException("Listing type is required.");
    }

    @FXML
    private void showOffers() {
        setActive(btnOffers, "Offers & Requests");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User seller = currentUser();
        List<Deal> deals = data.getDealsForSeller(seller == null ? "" : seller.getUsername())
                .stream()
                .sorted((a, b) -> Integer.compare(statusRank(a.getStatus()), statusRank(b.getStatus())))
                .collect(Collectors.toList());

        if (deals.isEmpty()) {
            root.getChildren().add(infoLabel("No offers or requests yet."));
        } else {
            for (Deal deal : deals) {
                root.getChildren().add(createOfferCard(deal));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private int statusRank(String status) {
        if (DealStatuses.PENDING.equals(status)) return 0;
        if (DealStatuses.COUNTERED.equals(status)) return 1;
        if (DealStatuses.ACCEPTED.equals(status)) return 2;
        return 3;
    }

    private VBox createOfferCard(Deal deal) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(deal.getListingTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label status = statusBadge(deal.getStatus());
        header.getChildren().addAll(title, spacer, status);

        String type = deal.getDealType() == ListingType.SELL ? "Offer"
                : deal.getDealType() == ListingType.LEND ? "Borrow request" : "Claim";
        Label meta = new Label(type + " from @" + deal.getBuyerUsername());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(header, meta);

        if (deal.getOfferPrice() != null) {
            card.getChildren().add(infoLabel("Offer price: HK$" + deal.getOfferPrice()));
        }
        DummyDataService.DealExtras extra = data.findDealExtras(deal.getId());
        if (extra == null) extra = new DummyDataService.DealExtras();
        if (deal.getDealType() == ListingType.LEND) {
            card.getChildren().add(infoLabel("Borrow period: " + formatDateTime(extra.borrowStartAt)
                    + "  ->  " + formatDateTime(extra.borrowEndAt)));
        }
        if (DealStatuses.COUNTERED.equals(deal.getStatus())) {
            if (extra.counterPrice != null) card.getChildren().add(infoLabel("Your counter price: HK$" + extra.counterPrice));
            if (extra.counterBorrowStartAt != null) {
                card.getChildren().add(infoLabel("Your counter period: " + formatDateTime(extra.counterBorrowStartAt)
                        + "  ->  " + formatDateTime(extra.counterBorrowEndAt)));
            }
        }
        if (deal.getCreatedAt() != null) {
            card.getChildren().add(infoLabel("Received: " + deal.getCreatedAt().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))));
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button chat = smallButton("Open Chat", "btn-outline");
        chat.setOnAction(event -> openChat(deal));
        actions.getChildren().add(chat);

        if (DealStatuses.PENDING.equals(deal.getStatus())) {
            Button accept = smallButton("Accept", "btn-success");
            accept.setOnAction(event -> acceptDeal(deal));
            Button decline = smallButton("Decline", "btn-danger");
            decline.setOnAction(event -> declineDeal(deal));
            actions.getChildren().addAll(accept, decline);
            if (deal.getDealType() != ListingType.GIVEAWAY) {
                Button counter = smallButton("Counter", "btn-gold");
                counter.setOnAction(event -> counterDeal(deal));
                actions.getChildren().add(counter);
            }
        } else if (DealStatuses.COUNTERED.equals(deal.getStatus())) {
            Button cancelCounter = smallButton("Cancel Counter", "btn-danger");
            cancelCounter.setOnAction(event -> declineDeal(deal));
            actions.getChildren().add(cancelCounter);
        } else if (DealStatuses.ACCEPTED.equals(deal.getStatus())
                && deal.getDealType() != ListingType.LEND) {
            Button cancelDeal = smallButton("Cancel Deal", "btn-danger");
            cancelDeal.setOnAction(event -> cancelAcceptedDeal(deal));
            actions.getChildren().add(cancelDeal);
        }
        card.getChildren().add(actions);
        return card;
    }

    private void acceptDeal(Deal deal) {
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Accept this " + (deal.getDealType() == ListingType.SELL ? "offer" : "request") + "?");
        confirm.showAndWait().ifPresent(result -> {
            if (result == ButtonType.OK) {
                try {
                    data.acceptDeal(currentUser(), deal.getId());
                    showOffers();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    }

    private void declineDeal(Deal deal) {
        boolean countered = DealStatuses.COUNTERED.equals(deal.getStatus());
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle(countered ? "Cancel Counter-Offer" : "Decline Deal");
        dialog.setHeaderText((countered ? "Cancel your counter-offer for " : "Decline ")
                + deal.getListingTitle());
        dialog.setContentText("Reason (optional):");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                data.declineDeal(currentUser(), deal.getId(), reason);
                showOffers();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void cancelAcceptedDeal(Deal deal) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Cancel Deal");
        dialog.setHeaderText("Cancel the accepted deal for " + deal.getListingTitle()
                + "?\nThe listing will become available again.");
        dialog.setContentText("Reason (optional):");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                data.cancelAcceptedDeal(currentUser(), deal.getId(), reason);
                showOffers();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void counterDeal(Deal deal) {
        try {
            if (deal.getDealType() == ListingType.SELL) {
                Listing listing = data.findListing(deal.getListingId());
                String asking = listing == null ? "" : PriceUtil.displayListingPrice(listing);
                TextInputDialog dialog = new TextInputDialog();
                dialog.setTitle("Counter Offer");
                dialog.setHeaderText("Counter the price for " + deal.getListingTitle()
                        + "\nBuyer offer: HK$" + deal.getOfferPrice()
                        + " · Asking price: " + asking);
                dialog.setContentText("Your counter price (above the buyer offer):");
                Optional<String> result = dialog.showAndWait();
                if (result.isEmpty()) return;
                double price = PriceUtil.parse(result.get());
                data.counterDeal(currentUser(), deal.getId(), price, null, null, null);
            } else if (deal.getDealType() == ListingType.LEND) {
                LocalDateTime[] period = askBorrowPeriod(deal);
                if (period == null) return;
                data.counterDeal(currentUser(), deal.getId(), null, period[0], period[1], null);
            }
            showOffers();
        } catch (NumberFormatException ex) {
            showError("Please enter a valid number.");
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    private LocalDateTime[] askBorrowPeriod(Deal deal) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Counter Borrow Request");
        dialog.setHeaderText("Propose a new lending period for: " + deal.getListingTitle());
        ButtonType submit = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);

        LocalDateTime now = LocalDateTime.now();
        DatePicker startDate = new DatePicker(now.toLocalDate());
        DatePicker endDate = new DatePicker(now.plusDays(1).toLocalDate());
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Start date"), startDate);
        grid.addRow(1, new Label("End date"), endDate);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) return null;
        if (startDate.getValue() == null || endDate.getValue() == null) {
            showError("Please choose both dates.");
            return null;
        }
        return new LocalDateTime[]{
                LocalDateTime.of(startDate.getValue(), LocalTime.of(0, 0)),
                LocalDateTime.of(endDate.getValue(), LocalTime.of(23, 59, 59))
        };
    }

    @FXML
    private void showChats() {
        setActive(btnChats, "Chats");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User seller = currentUser();
        List<DummyDataService.ChatThreadInfo> threads =
                data.getChatThreadsForSeller(seller == null ? "" : seller.getUsername());

        if (threads.isEmpty()) {
            root.getChildren().add(infoLabel("No chats yet."));
        } else {
            for (DummyDataService.ChatThreadInfo thread : threads) {
                root.getChildren().add(createChatCard(thread));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createChatCard(DummyDataService.ChatThreadInfo thread) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        HBox header = new HBox(10);
        header.setAlignment(Pos.CENTER_LEFT);
        Label title = new Label(thread.listingTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label buyer = new Label("Buyer: @" + thread.buyerUsername());
        buyer.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        header.getChildren().addAll(title, spacer, buyer);
        card.getChildren().add(header);

        List<DummyDataService.ChatMessage> messages = data.getChatMessages(thread.id());
        if (messages.isEmpty()) {
            card.getChildren().add(infoLabel("No messages yet."));
        } else {
            DummyDataService.ChatMessage last = messages.get(messages.size() - 1);
            Label preview = new Label("Last message from @" + last.senderUsername() + ": " + last.content());
            preview.setWrapText(true);
            preview.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
            card.getChildren().add(preview);
        }
        if (thread.lastMessageAt() != null) {
            card.getChildren().add(infoLabel("Last activity: " + thread.lastMessageAt().format(
                    DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))));
        }

        Button open = smallButton("Open Chat", "btn-outline");
        open.setOnAction(event -> openChat(thread, this::showChats));
        card.getChildren().add(open);
        return card;
    }

    private void openChat(Deal deal) {
        openChat(data.getChatThreadForDeal(deal), this::showOffers);
    }

    private void openChat(DummyDataService.ChatThreadInfo thread, Runnable onBack) {
        try {
            FXMLLoader loader = new FXMLLoader(getClass().getResource("/fxml/buyer/ChatView.fxml"));
            Parent view = loader.load();
            com.hkust.marketplace.controllers.buyer.ChatController controller = loader.getController();
            controller.setThread(thread, onBack);
            contentPane.getChildren().setAll(view);
            pageTitle.setText("Chat");
        } catch (IOException ex) {
            showError("Could not open chat: " + ex.getMessage());
        }
    }

    @FXML
    private void showStats() {
        setActive(btnStats, "Seller Stats");
        contentPane.getChildren().clear();

        VBox root = new VBox(18);
        root.setPadding(new Insets(4));
        User seller = currentUser();
        String username = seller == null ? "" : seller.getUsername();
        List<Listing> myListings = data.getListingsBySeller(username);
        List<Deal> myDeals = data.getDealsForSeller(username);

        long views = myListings.stream().mapToInt(Listing::getViewCount).sum();
        long completed = myDeals.stream()
                .filter(d -> DealStatuses.COMPLETED.equals(d.getStatus())
                        || DealStatuses.RETURNED.equals(d.getStatus()))
                .count();
        double avg = data.getSellerAverageRating(username);
        int reviews = data.getSellerRatingCount(username);

        HBox metrics = new HBox(14);
        metrics.getChildren().addAll(
                metricCard("Listings", String.valueOf(myListings.size()), "#003974"),
                metricCard("Total Views", String.valueOf(views), "#1565c0"),
                metricCard("Offers Received", String.valueOf(myDeals.size()), "#A68137"),
                metricCard("Completed Deals", String.valueOf(completed), "#2e7d32"),
                metricCard("Avg Rating", String.format("%.1f", avg), "#A68137"),
                metricCard("Reviews", String.valueOf(reviews), "#2e7d32")
        );
        root.getChildren().add(metrics);

        CategoryAxis xAxis = new CategoryAxis();
        NumberAxis yAxis = new NumberAxis();
        BarChart<String, Number> barChart = new BarChart<>(xAxis, yAxis);
        barChart.setTitle("My Listings by Category");
        barChart.setLegendVisible(false);
        barChart.setPrefHeight(280);
        Map<String, Long> byCategory = myListings.stream()
                .collect(Collectors.groupingBy(Listing::getCategory, LinkedHashMap::new, Collectors.counting()));
        XYChart.Series<String, Number> series = new XYChart.Series<>();
        byCategory.forEach((category, count) -> series.getData().add(new XYChart.Data<>(category, count)));
        barChart.getData().add(series);

        PieChart pie = new PieChart();
        pie.setTitle("My Listing Status Distribution");
        pie.setPrefHeight(280);
        Map<ListingStatus, Long> byStatus = myListings.stream()
                .collect(Collectors.groupingBy(Listing::getStatus, Collectors.counting()));
        byStatus.forEach((status, count) -> pie.getData().add(new PieChart.Data(
                status.name().replace('_', ' '), count)));

        root.getChildren().add(new HBox(18, barChart, pie));
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showReviews() {
        setActive(btnReviews, "Reviews & Feedback");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User seller = currentUser();
        List<Review> reviews = data.getReviewsForSeller(seller == null ? "" : seller.getUsername());

        if (reviews.isEmpty()) {
            root.getChildren().add(infoLabel("No reviews yet."));
        } else {
            for (Review review : reviews) {
                VBox card = new VBox(6);
                card.getStyleClass().add("card");
                card.setPadding(new Insets(12));
                Label stars = new Label("★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(review.getComment());
                comment.setWrapText(true);
                Label meta = new Label("by @" + review.getBuyerUsername() + " · "
                        + review.getListingTitle() + " · "
                        + review.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                card.getChildren().addAll(stars, comment, meta);

                if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
                    Label reply = new Label("Your reply: " + review.getSellerReply());
                    reply.setWrapText(true);
                    reply.setStyle("-fx-text-fill: #003974; -fx-font-size: 12px;");
                    card.getChildren().add(reply);
                }

                HBox actions = new HBox(8);
                Button replyBtn = smallButton(review.getSellerReply() == null ? "Reply" : "Edit Reply", "btn-outline");
                replyBtn.setOnAction(event -> replyToReview(review));
                Button flagBtn = smallButton("Flag", "btn-danger");
                flagBtn.setOnAction(event -> flagReview(review));
                actions.getChildren().addAll(replyBtn, flagBtn);
                card.getChildren().add(actions);
                root.getChildren().add(card);
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private void replyToReview(Review review) {
        TextInputDialog dialog = new TextInputDialog(review.getSellerReply());
        dialog.setTitle("Reply to Review");
        dialog.setHeaderText("Reply to @" + review.getBuyerUsername());
        dialog.setContentText("Your reply:");
        dialog.showAndWait().ifPresent(reply -> {
            try {
                data.addSellerReply(currentUser(), review.getId(), reply);
                showReviews();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void flagReview(Review review) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Flag Review");
        dialog.setHeaderText("Flag review by @" + review.getBuyerUsername());
        dialog.setContentText("Reason for admin:");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                data.flagReview(currentUser(), review.getId(), reason);
                showInfo("Review flagged. The admin will review it.");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Notifications");
        contentPane.getChildren().clear();

        VBox root = new VBox(10);
        root.setPadding(new Insets(4));
        User seller = currentUser();
        List<Notification> notifications = data.getNotificationsFor(seller == null ? "" : seller.getUsername());

        HBox toolbar = new HBox(8);
        Button markAll = new Button("Mark all read");
        markAll.getStyleClass().addAll("btn-outline", "btn-sm");
        markAll.setOnAction(event -> {
            data.markAllNotificationsRead(seller.getUsername());
            showNotifications();
        });
        toolbar.getChildren().add(markAll);
        root.getChildren().add(toolbar);

        if (notifications.isEmpty()) {
            root.getChildren().add(infoLabel("No notifications."));
        } else {
            for (Notification notification : notifications) {
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                if (!notification.isRead()) item.setStyle("-fx-background-color: #eef4ff;");
                Label title = new Label(notification.getTitle());
                title.setStyle("-fx-font-weight: bold;");
                Label message = new Label(notification.getMessage());
                message.setWrapText(true);
                message.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                Label meta = new Label(notification.getCategory() + " · "
                        + notification.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(title, message, meta);
                item.setOnMouseClicked(event -> {
                    data.markNotificationRead(notification.getId());
                    showNotifications();
                });
                root.getChildren().add(item);
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    private void showProfile() {
        setActive(btnProfile, "My Profile");
        contentPane.getChildren().clear();

        User seller = currentUser();
        VBox form = new VBox(10);
        form.setMaxWidth(480);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(18));

        Label title = new Label("Edit Seller Profile");
        title.getStyleClass().add("section-title");
        TextField name = new TextField(seller == null ? "" : seller.getFullName());
        PasswordField password = new PasswordField();
        password.setPromptText("New password (optional)");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        TextArea bio = new TextArea(seller == null || seller.getBio() == null ? "" : seller.getBio());
        bio.setPrefRowCount(3);
        ComboBox<String> meetup = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetup.setMaxWidth(Double.MAX_VALUE);
        if (seller != null && seller.getPreferredMeetup() != null) meetup.setValue(seller.getPreferredMeetup());

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-gold");
        save.setOnAction(event -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            try {
                data.updateSellerProfile(currentUser(), name.getText(), password.getText(),
                        confirm.getText(), bio.getText(), meetup.getValue());
                if (currentUser() != null) welcomeLabel.setText("Hello, " + currentUser().getFullName());
                feedback.setText("Profile updated successfully.");
                feedback.setStyle("-fx-text-fill: #2e7d32;");
                feedback.setVisible(true);
                feedback.setManaged(true);
                password.clear();
                confirm.clear();
            } catch (RuntimeException ex) {
                feedback.setText(ex.getMessage());
                feedback.setStyle("-fx-text-fill: #c62828;");
                feedback.setVisible(true);
                feedback.setManaged(true);
            }
        });

        form.getChildren().addAll(title,
                new Label("Full Name"), name,
                new Label("New Password"), password,
                new Label("Confirm Password"), confirm,
                new Label("Bio"), bio,
                new Label("Preferred Meet-up"), meetup,
                save, feedback);
        contentPane.getChildren().add(form);
    }

    private Label statusBadge(String status) {
        Label label = new Label(status);
        label.getStyleClass().add("badge");
        if (DealStatuses.ACCEPTED.equals(status) || DealStatuses.COMPLETED.equals(status)) {
            label.getStyleClass().add("badge-live");
        } else if (DealStatuses.PENDING.equals(status) || DealStatuses.COUNTERED.equals(status)) {
            label.getStyleClass().add("badge-pending");
        } else if (DealStatuses.DECLINED.equals(status) || DealStatuses.CANCELLED.equals(status)) {
            label.getStyleClass().add("badge-rejected");
        } else {
            label.getStyleClass().add("badge-sold");
        }
        return label;
    }

    private Label statusBadge(ListingStatus status) {
        Label label = new Label(status.name().replace('_', ' '));
        label.getStyleClass().add("badge");
        switch (status) {
            case LIVE -> label.getStyleClass().add("badge-live");
            case PENDING_REVIEW -> label.getStyleClass().add("badge-pending");
            case REJECTED, REMOVED -> label.getStyleClass().add("badge-rejected");
            default -> label.getStyleClass().add("badge-sold");
        }
        return label;
    }

    private VBox metricCard(String label, String value, String color) {
        VBox card = new VBox(4);
        card.getStyleClass().add("card");
        card.setAlignment(Pos.CENTER);
        card.setPrefWidth(130);
        card.setPadding(new Insets(14));
        Label val = new Label(value);
        val.setStyle("-fx-font-size: 26px; -fx-font-weight: bold; -fx-text-fill: " + color + ";");
        Label lbl = new Label(label);
        lbl.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        card.getChildren().addAll(val, lbl);
        return card;
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        return label;
    }

    private static String formatDateTime(LocalDateTime value) {
        return value == null ? "?" : value.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }

    private static Button smallButton(String text, String styleClass) {
        Button button = new Button(text);
        button.getStyleClass().addAll(styleClass, "btn-sm");
        return button;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "Something went wrong." : message);
        alert.showAndWait();
    }

    private void showInfo(String message) {
        Alert alert = new Alert(Alert.AlertType.INFORMATION);
        alert.setTitle("Information");
        alert.setHeaderText(null);
        alert.setContentText(message);
        alert.showAndWait();
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
