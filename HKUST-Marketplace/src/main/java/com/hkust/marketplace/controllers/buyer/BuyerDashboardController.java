package com.hkust.marketplace.controllers.buyer;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.BorrowLifecycleScheduler;
import com.hkust.marketplace.utils.DealStatuses;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.ListingSort;
import com.hkust.marketplace.utils.NotificationCategories;
import com.hkust.marketplace.utils.PriceUtil;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.fxml.FXMLLoader;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.io.File;
import java.io.IOException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Buyer portal controller for browsing, deals, chat, reviews and profile editing.
 */
public class BuyerDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnBrowse, btnDeals, btnHistory, btnReviews, btnNotifications, btnProfile;
    private Button currentActiveBtn;
    private final DummyDataService data = DummyDataService.getInstance();
    private String currentSection = "browse";

    @FXML
    public void initialize() {
        User user = SessionManager.getCurrentUser();
        if (user != null) {
            welcomeLabel.setText("Hello, " + user.getFullName());
        }
        currentActiveBtn = btnBrowse;
        refreshNotificationBadge();
        BorrowLifecycleScheduler.setOnChangeListener(this::onBorrowLifecycleChanged);
        showBrowse();
    }

    private void onBorrowLifecycleChanged() {
        refreshNotificationBadge();
        if ("deals".equals(currentSection)) {
            showMyDeals();
        } else if ("notifications".equals(currentSection)) {
            showNotifications();
        }
    }

    private User currentUser() {
        return SessionManager.getCurrentUser();
    }

    private void setActive(Button btn, String title) {
        if (currentActiveBtn != null) {
            currentActiveBtn.getStyleClass().remove("sidebar-btn-active");
        }
        btn.getStyleClass().add("sidebar-btn-active");
        currentActiveBtn = btn;
        pageTitle.setText(title);
    }

    private void refreshNotificationBadge() {
        User user = currentUser();
        if (user == null || btnNotifications == null) return;
        int unread = data.getUnreadNotificationCount(user.getUsername());
        btnNotifications.setText(unread > 0
                ? "  Notifications (" + unread + ")" : "  Notifications");
    }

    @FXML
    public void showBrowse() {
        currentSection = "browse";
        setActive(btnBrowse, "Browse Marketplace");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        HBox filterBar = new HBox(12);
        filterBar.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> categoryFilter = new ComboBox<>(
                FXCollections.observableArrayList("All Categories"));
        categoryFilter.getItems().addAll(DummyDataService.CATEGORIES);
        categoryFilter.getSelectionModel().selectFirst();
        ComboBox<String> typeFilter = new ComboBox<>(
                FXCollections.observableArrayList("All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        ComboBox<String> conditionFilter = new ComboBox<>(
                FXCollections.observableArrayList("Any", "New", "Like New", "Used", "For Parts"));
        conditionFilter.getSelectionModel().selectFirst();
        ComboBox<String> sortFilter = new ComboBox<>(
                FXCollections.observableArrayList("Newest", "Price: Low to High", "Price: High to Low"));
        sortFilter.getSelectionModel().selectFirst();
        TextField searchField = new TextField();
        searchField.setPromptText("Search listings...");
        searchField.setPrefWidth(200);
        TextField minPriceField = new TextField();
        minPriceField.setPromptText("Min HK$");
        minPriceField.setPrefWidth(90);
        TextField maxPriceField = new TextField();
        maxPriceField.setPromptText("Max HK$");
        maxPriceField.setPrefWidth(90);
        Button searchBtn = new Button("Search");
        searchBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        Button clearBtn = new Button("Clear");
        clearBtn.getStyleClass().addAll("btn-outline", "btn-sm");

        filterBar.getChildren().addAll(
                new Label("Category:"), categoryFilter,
                new Label("Type:"), typeFilter,
                new Label("Condition:"), conditionFilter,
                searchField, minPriceField, maxPriceField, searchBtn, clearBtn
        );
        HBox sortBar = new HBox(12);
        sortBar.setAlignment(Pos.CENTER_LEFT);
        sortBar.getChildren().addAll(new Label("Sort by:"), sortFilter);
        root.getChildren().addAll(filterBar, sortBar);

        FlowPane grid = new FlowPane(16, 16);
        grid.setPrefWrapLength(900);

        Runnable refreshResults = () -> {
            List<Listing> results = data.searchListings(
                    searchField.getText(),
                    categoryFilter.getValue(),
                    mapType(typeFilter.getValue()),
                    parseDoubleOrNull(minPriceField.getText()),
                    parseDoubleOrNull(maxPriceField.getText()),
                    conditionFilter.getValue(),
                    mapSort(sortFilter.getValue()));
            grid.getChildren().clear();
            if (results.isEmpty()) {
                Label empty = new Label("No listings match your filters.");
                empty.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 15px;");
                grid.getChildren().add(empty);
            } else {
                for (Listing listing : results) {
                    grid.getChildren().add(createListingCard(listing));
                }
            }
        };

        searchField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        conditionFilter.valueProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        sortFilter.valueProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        minPriceField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        maxPriceField.textProperty().addListener((obs, oldValue, newValue) -> refreshResults.run());
        searchBtn.setOnAction(event -> refreshResults.run());
        clearBtn.setOnAction(event -> {
            searchField.clear();
            minPriceField.clear();
            maxPriceField.clear();
            categoryFilter.getSelectionModel().selectFirst();
            typeFilter.getSelectionModel().selectFirst();
            conditionFilter.getSelectionModel().selectFirst();
            sortFilter.getSelectionModel().selectFirst();
            refreshResults.run();
        });

        root.getChildren().add(grid);
        refreshResults.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private VBox createListingCard(Listing listing) {
        VBox card = new VBox(8);
        card.getStyleClass().add("listing-card");
        card.setPrefWidth(260);
        card.setMinHeight(200);
        card.setOnMouseClicked(event -> openListingDetail(listing));

        ImageView thumbnail = new ImageView();
        thumbnail.setFitWidth(230);
        thumbnail.setFitHeight(110);
        thumbnail.setPreserveRatio(true);
        Image image = loadThumbnail(listing);
        if (image != null) {
            thumbnail.setImage(image);
        }

        HBox top = new HBox();
        top.setAlignment(Pos.CENTER_LEFT);
        Label typeBadge = new Label(listing.getTypeDisplay());
        typeBadge.getStyleClass().add("badge");
        if (listing.getType() == ListingType.SELL) typeBadge.getStyleClass().add("badge-sold");
        else if (listing.getType() == ListingType.LEND) typeBadge.getStyleClass().add("badge-pending");
        else typeBadge.getStyleClass().add("badge-live");

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        Label price = new Label(PriceUtil.displayListingPrice(listing));
        price.setStyle("-fx-font-weight: bold; -fx-text-fill: #003974; -fx-font-size: 16px;");
        top.getChildren().addAll(typeBadge, spacer, price);

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px; -fx-text-fill: #1a1a2e;");
        title.setWrapText(true);

        Label meta = new Label(listing.getCategory() + " · " + listing.getCondition());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label seller = new Label("Seller: " + listing.getSellerFullName()
                + "  ★ " + String.format("%.1f", data.getSellerAverageRating(listing.getSellerUsername())));
        seller.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label location = new Label("📍 " + listing.getMeetupLocation());
        location.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        Label date = new Label("Posted: " + listing.getPostedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
        date.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");

        Label availability = new Label("Status: " + listing.getStatus().name().replace('_', ' '));
        availability.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 11px;");

        Button actionBtn = new Button("View Details");
        actionBtn.getStyleClass().addAll("btn-primary", "btn-sm");
        actionBtn.setOnAction(event -> openListingDetail(listing));
        actionBtn.setOnMouseClicked(event -> event.consume());

        card.getChildren().addAll(thumbnail, top, title, meta, seller, location, date, availability, actionBtn);
        return card;
    }

    private Image loadThumbnail(Listing listing) {
        if (listing.getPhotoPaths() != null && !listing.getPhotoPaths().isEmpty()) {
            File file = new File(listing.getPhotoPaths().get(0));
            if (file.exists()) {
                return new Image(file.toURI().toString(), 230, 110, true, true);
            }
        }
        java.net.URL logo = getClass().getResource("/images/hkust_logo.png");
        return logo == null ? null : new Image(logo.toExternalForm(), 230, 110, true, true);
    }

    public void openListingDetail(Listing listing) {
        if (listing == null) return;
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/buyer/ListingDetailView.fxml"));
            Parent view = loader.load();
            ListingDetailController controller = loader.getController();
            controller.setListing(listing, this);
            contentPane.getChildren().setAll(view);
            pageTitle.setText("Listing Details");
            currentSection = "detail";
        } catch (IOException ex) {
            showError("Could not open listing details: " + ex.getMessage());
        }
    }

    public void openListingDetailById(String listingId) {
        Listing listing = data.findListing(listingId);
        if (listing == null) {
            showInfo("This listing no longer exists.");
            return;
        }
        openListingDetail(listing);
    }

    public void buyNow(Listing listing) {
        if (listing.getStatus() != com.hkust.marketplace.models.ListingStatus.LIVE) {
            showError("This listing is no longer available.");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                "Buy '" + listing.getTitle() + "' now for "
                        + PriceUtil.displayListingPrice(listing) + "?");
        confirm.setHeaderText("Confirm Buy Now");
        confirm.showAndWait().ifPresent(result -> {
            if (result != ButtonType.OK) {
                return;
            }
            try {
                data.buyNow(currentUser(), listing.getId());
                showInfo("Purchase confirmed at " + PriceUtil.displayListingPrice(listing) + ".");
                showMyDeals();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    public void showOfferDialog(Listing listing) {
        if (listing.getStatus() != com.hkust.marketplace.models.ListingStatus.LIVE) {
            showError("This listing is no longer available.");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Make an Offer");
        dialog.setHeaderText("Offer for: " + listing.getTitle()
                + "\nAsking price: " + PriceUtil.displayListingPrice(listing));
        dialog.setContentText("Your offer price (must be below the asking price):");
        dialog.showAndWait().ifPresent(priceStr -> {
            try {
                double price = PriceUtil.parse(priceStr);
                data.submitOffer(currentUser(), listing.getId(), price);
                showInfo("Your offer of HK$" + price + " was sent to "
                        + listing.getSellerFullName() + ".");
                openListingDetail(listing);
            } catch (NumberFormatException ex) {
                showError("Invalid price.");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    public void showBorrowDialog(Listing listing) {
        if (listing.getStatus() != com.hkust.marketplace.models.ListingStatus.LIVE) {
            showError("This listing is no longer available.");
            return;
        }
        LocalDateTime[] period = askBorrowPeriod(listing);
        if (period == null) return;
        try {
            data.submitBorrowRequest(currentUser(), listing.getId(), period[0], period[1]);
            showInfo("Borrow request sent to " + listing.getSellerFullName() + ".");
            openListingDetail(listing);
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    public LocalDateTime[] askBorrowPeriod(Listing listing) {
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Borrow Request");
        dialog.setHeaderText("Propose a borrow period for: " + listing.getTitle());
        ButtonType submit = new ButtonType("Submit", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);

        LocalDateTime now = LocalDateTime.now();
        int days = listing.getLendingDays() != null ? listing.getLendingDays() : 1;
        LocalDateTime defaultEnd = now.plusDays(days);

        DatePicker startDate = new DatePicker(now.toLocalDate());
        DatePicker endDate = new DatePicker(defaultEnd.toLocalDate());
        Spinner<Integer> startHour = timeSpinner(23, now.getHour());
        Spinner<Integer> startMinute = timeSpinner(59, now.getMinute());
        Spinner<Integer> startSecond = timeSpinner(59, now.getSecond());
        Spinner<Integer> endHour = timeSpinner(23, defaultEnd.getHour());
        Spinner<Integer> endMinute = timeSpinner(59, defaultEnd.getMinute());
        Spinner<Integer> endSecond = timeSpinner(59, defaultEnd.getSecond());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Start date"), startDate, new Label("Time"),
                new HBox(4, startHour, new Label(":"), startMinute, new Label(":"), startSecond));
        grid.addRow(1, new Label("End date"), endDate, new Label("Time"),
                new HBox(4, endHour, new Label(":"), endMinute, new Label(":"), endSecond));
        grid.add(new Label("Maximum borrow period: " + days + " day(s). Minimum: 5 seconds."), 0, 2, 4, 1);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return null;
        }
        if (startDate.getValue() == null || endDate.getValue() == null) {
            showError("Please choose both dates.");
            return null;
        }
        LocalDateTime start = LocalDateTime.of(startDate.getValue(),
                LocalTime.of(startHour.getValue(), startMinute.getValue(), startSecond.getValue()));
        LocalDateTime end = LocalDateTime.of(endDate.getValue(),
                LocalTime.of(endHour.getValue(), endMinute.getValue(), endSecond.getValue()));
        if (listing.getLendingDays() != null
                && end.isAfter(start.plusDays(listing.getLendingDays()))) {
            showError("Borrow period cannot exceed " + listing.getLendingDays() + " day(s).");
            return null;
        }
        return new LocalDateTime[]{start, end};
    }

    private Spinner<Integer> timeSpinner(int max, int value) {
        Spinner<Integer> spinner = new Spinner<>(0, max, value);
        spinner.setEditable(true);
        spinner.setPrefWidth(70);
        return spinner;
    }

    public void claimItem(Listing listing) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION);
        alert.setTitle("Claim Item");
        alert.setHeaderText("Claim: " + listing.getTitle());
        alert.setContentText("Are you sure you want to claim this giveaway item?");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    data.claimGiveaway(currentUser(), listing.getId());
                    showInfo("Item claimed successfully.");
                    openListingDetail(listing);
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    }

    public void reportListing(Listing listing) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Report Listing");
        dialog.setHeaderText("Report: " + listing.getTitle());
        dialog.setContentText("Reason:");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                data.reportListing(currentUser(), listing.getId(), reason);
                showInfo("Thank you. The admin will review this report.");
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    public void openChatForListing(Listing listing) {
        try {
            var thread = data.getOrCreateChatThread(listing.getId(), currentUser().getUsername());
            openChat(thread, () -> openListingDetail(listing));
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    public void openChat(Deal deal) {
        var thread = data.getChatThreadForDeal(deal);
        openChat(thread, this::showMyDeals);
    }

    public void openChat(DummyDataService.ChatThreadInfo thread, Runnable onBack) {
        try {
            FXMLLoader loader = new FXMLLoader(
                    getClass().getResource("/fxml/buyer/ChatView.fxml"));
            Parent view = loader.load();
            ChatController controller = loader.getController();
            controller.setThread(thread, this, onBack);
            contentPane.getChildren().setAll(view);
            pageTitle.setText("Chat");
            currentSection = "chat";
        } catch (IOException ex) {
            showError("Could not open chat: " + ex.getMessage());
        }
    }

    @FXML
    public void showMyDeals() {
        currentSection = "deals";
        setActive(btnDeals, "My Deals");
        contentPane.getChildren().clear();

        VBox root = new VBox(16);
        root.setPadding(new Insets(4));

        User user = currentUser();
        List<Deal> myDeals = user == null ? List.of() : data.getDealsForBuyer(user.getUsername());
        List<Deal> active = myDeals.stream()
                .filter(d -> DealStatuses.PENDING.equals(d.getStatus())
                        || DealStatuses.COUNTERED.equals(d.getStatus())
                        || DealStatuses.ACCEPTED.equals(d.getStatus()))
                .collect(Collectors.toList());
        List<Deal> past = myDeals.stream()
                .filter(d -> !active.contains(d))
                .collect(Collectors.toList());

        root.getChildren().add(sectionTitle("Active Offers / Requests / Claims"));
        if (active.isEmpty()) {
            root.getChildren().add(emptyLabel("No active deals."));
        } else {
            for (Deal deal : active) {
                root.getChildren().add(createDealCard(deal, true));
            }
        }
        root.getChildren().add(sectionTitle("Past Deals"));
        if (past.isEmpty()) {
            root.getChildren().add(emptyLabel("No past deals yet."));
        } else {
            for (Deal deal : past) {
                root.getChildren().add(createDealCard(deal, false));
            }
        }

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        scroll.setStyle("-fx-background-color: transparent;");
        contentPane.getChildren().add(scroll);
    }

    private VBox createDealCard(Deal deal, boolean active) {
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

        Label meta = new Label("Seller: " + deal.getSellerUsername()
                + " · " + dealTypeDisplay(deal.getDealType()));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        VBox info = new VBox(4);
        if (deal.getDealType() == ListingType.SELL && deal.getOfferPrice() != null) {
            info.getChildren().add(infoLabel("Offer: HK$" + deal.getOfferPrice()));
        }
        DummyDataService.DealExtras extra = data.findDealExtras(deal.getId());
        if (extra == null) extra = new DummyDataService.DealExtras();
        if (deal.getDealType() == ListingType.LEND) {
            LocalDateTime start = extra.borrowStartAt;
            LocalDateTime end = extra.borrowEndAt;
            if (start != null || end != null) {
                info.getChildren().add(infoLabel("Borrow period: "
                        + (start == null ? "?" : start.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))
                        + "  →  "
                        + (end == null ? "?" : end.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss")))));
            }
            if (DealStatuses.ACCEPTED.equals(deal.getStatus()) && end != null) {
                info.getChildren().add(infoLabel("Due date: "
                        + end.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm:ss"))));
            }
        }
        if (DealStatuses.COUNTERED.equals(deal.getStatus())) {
            if (extra.counterPrice != null) {
                info.getChildren().add(infoLabel("Seller counter-offer: HK$" + extra.counterPrice));
            }
            if (extra.counterBorrowStartAt != null && extra.counterBorrowEndAt != null) {
                info.getChildren().add(infoLabel("Counter period: "
                        + extra.counterBorrowStartAt + "  →  " + extra.counterBorrowEndAt));
            }
            if (extra.counterMessage != null && !extra.counterMessage.isBlank()) {
                info.getChildren().add(infoLabel("Message: " + extra.counterMessage));
            }
        }
        if (deal.getCompletedAt() != null) {
            info.getChildren().add(infoLabel("Completed: "
                    + deal.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))));
        }

        HBox actions = new HBox(8);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button openBtn = smallButton("Open Listing", "btn-outline");
        openBtn.setOnAction(event -> openListingDetailById(deal.getListingId()));
        Button chatBtn = smallButton("Chat", "btn-outline");
        chatBtn.setOnAction(event -> openChat(deal));
        actions.getChildren().addAll(openBtn, chatBtn);

        if (DealStatuses.COUNTERED.equals(deal.getStatus())) {
            Button acceptCounter = smallButton("Accept Counter", "btn-success");
            acceptCounter.setOnAction(event -> {
                try {
                    data.acceptCounterOffer(currentUser(), deal.getId());
                    showMyDeals();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });
            Button declineCounter = smallButton("Decline Counter", "btn-danger");
            declineCounter.setOnAction(event -> {
                try {
                    data.declineCounterOffer(currentUser(), deal.getId());
                    showMyDeals();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });
            actions.getChildren().addAll(acceptCounter, declineCounter);
        }

        if (DealStatuses.ACCEPTED.equals(deal.getStatus())
                && deal.getDealType() == ListingType.LEND) {
            Button returnBtn = smallButton("Self Return", "btn-gold");
            returnBtn.setOnAction(event -> confirmAndReturn(deal));
            actions.getChildren().add(returnBtn);
        }
        if (DealStatuses.ACCEPTED.equals(deal.getStatus())
                && deal.getDealType() != ListingType.LEND) {
            Button completeBtn = smallButton("Mark Complete", "btn-success");
            completeBtn.setOnAction(event -> confirmAndComplete(deal));
            actions.getChildren().add(completeBtn);
        }
        if (!active && !deal.isRated()
                && (DealStatuses.COMPLETED.equals(deal.getStatus())
                || DealStatuses.RETURNED.equals(deal.getStatus()))) {
            Button rateBtn = smallButton("Rate Seller", "btn-primary");
            rateBtn.setOnAction(event -> promptRating(deal));
            actions.getChildren().add(rateBtn);
        }

        card.getChildren().addAll(header, meta, info, actions);
        return card;
    }

    private void confirmAndReturn(Deal deal) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION, "Return this item now?");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    data.selfReturn(currentUser(), deal.getId());
                    showMyDeals();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    }

    private void confirmAndComplete(Deal deal) {
        Alert alert = new Alert(Alert.AlertType.CONFIRMATION,
                "Mark this deal as complete? You will be able to rate the seller.");
        alert.showAndWait().ifPresent(response -> {
            if (response == ButtonType.OK) {
                try {
                    data.completeDeal(currentUser(), deal.getId());
                    showMyDeals();
                    promptRating(deal);
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            }
        });
    }

    public void promptRating(Deal deal) {
        if (!data.canReviewDeal(currentUser(), deal.getId())) {
            showInfo("This deal cannot be rated.");
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Rate Seller");
        dialog.setHeaderText("Rate " + deal.getSellerUsername() + " for '" + deal.getListingTitle() + "'");
        ButtonType submit = new ButtonType("Submit Review", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(submit, ButtonType.CANCEL);

        ComboBox<Integer> ratingBox = new ComboBox<>(
                FXCollections.observableArrayList(5, 4, 3, 2, 1));
        ratingBox.getSelectionModel().selectFirst();
        TextArea commentArea = new TextArea();
        commentArea.setPromptText("Write your review...");
        commentArea.setPrefRowCount(4);
        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Rating"), ratingBox);
        grid.addRow(1, new Label("Review"), commentArea);
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isEmpty() || result.get() == ButtonType.CANCEL) {
            return;
        }
        try {
            data.addReview(currentUser(), deal.getId(), ratingBox.getValue(), commentArea.getText());
            showInfo("Thank you for your review.");
            if ("history".equals(currentSection)) {
                showHistory();
            } else {
                showMyDeals();
            }
        } catch (RuntimeException ex) {
            showError(ex.getMessage());
        }
    }

    @FXML
    public void showHistory() {
        currentSection = "history";
        setActive(btnHistory, "Deal History");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(10);
        filters.setAlignment(Pos.CENTER_LEFT);
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        ComboBox<String> categoryFilter = new ComboBox<>(
                FXCollections.observableArrayList("All Categories"));
        categoryFilter.getItems().addAll(DummyDataService.CATEGORIES);
        categoryFilter.getSelectionModel().selectFirst();
        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller username");
        sellerField.setPrefWidth(130);
        TextField searchField = new TextField();
        searchField.setPromptText("Search title...");
        searchField.setPrefWidth(150);
        filters.getChildren().addAll(new Label("Date:"), from, new Label("–"), to,
                new Label("Category:"), categoryFilter, sellerField, searchField);
        root.getChildren().add(filters);

        TableView<Deal> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Deal, String> c1 = new TableColumn<>("Item Title");
        c1.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getListingTitle()));
        TableColumn<Deal, String> c2 = new TableColumn<>("Seller");
        c2.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getSellerUsername()));
        TableColumn<Deal, String> c3 = new TableColumn<>("Deal Date");
        c3.setCellValueFactory(d -> new SimpleStringProperty(dealDate(d.getValue())));
        TableColumn<Deal, String> c4 = new TableColumn<>("Deal Type");
        c4.setCellValueFactory(d -> new SimpleStringProperty(dealTypeDisplay(d.getValue().getDealType())));
        TableColumn<Deal, String> c5 = new TableColumn<>("Outcome");
        c5.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().getStatus()));
        TableColumn<Deal, String> c6 = new TableColumn<>("Rated");
        c6.setCellValueFactory(d -> new SimpleStringProperty(d.getValue().isRated() ? "Yes" : "No"));
        table.getColumns().addAll(c1, c2, c3, c4, c5, c6);
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable applyFilters = () -> {
            User user = currentUser();
            List<Deal> all = user == null ? List.of() : data.getDealsForBuyer(user.getUsername());
            String seller = sellerField.getText() == null ? "" : sellerField.getText().trim().toLowerCase();
            String keyword = searchField.getText() == null ? "" : searchField.getText().trim().toLowerCase();
            List<Deal> filtered = all.stream()
                    .filter(d -> DealStatuses.COMPLETED.equals(d.getStatus())
                            || DealStatuses.RETURNED.equals(d.getStatus()))
                    .filter(d -> {
                        if (from.getValue() == null && to.getValue() == null) return true;
                        LocalDate date = (d.getCompletedAt() != null
                                ? d.getCompletedAt().toLocalDate() : d.getCreatedAt().toLocalDate());
                        if (from.getValue() != null && date.isBefore(from.getValue())) return false;
                        return to.getValue() == null || !date.isAfter(to.getValue());
                    })
                    .filter(d -> {
                        var dealExtra = data.findDealExtras(d.getId());
                        String category = dealExtra == null ? null : dealExtra.listingCategory;
                        return "All Categories".equals(categoryFilter.getValue())
                                || (category != null && category.equals(categoryFilter.getValue()));
                    })
                    .filter(d -> seller.isEmpty()
                            || d.getSellerUsername().toLowerCase().contains(seller))
                    .filter(d -> keyword.isEmpty()
                            || d.getListingTitle().toLowerCase().contains(keyword))
                    .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(filtered));
        };

        from.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        to.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        sellerField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        searchField.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        applyFilters.run();

        HBox actions = new HBox(10);
        actions.setAlignment(Pos.CENTER_LEFT);
        Button rateSelected = new Button("Rate Selected Deal");
        rateSelected.getStyleClass().addAll("btn-primary", "btn-sm");
        rateSelected.setOnAction(event -> {
            Deal selected = table.getSelectionModel().getSelectedItem();
            if (selected == null) {
                showError("Please select a deal first.");
            } else {
                promptRating(selected);
            }
        });
        actions.getChildren().add(rateSelected);

        root.getChildren().addAll(actions, table);
        contentPane.getChildren().add(root);
    }

    @FXML
    public void showMyReviews() {
        currentSection = "reviews";
        setActive(btnReviews, "My Reviews");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        User user = currentUser();
        List<Review> myReviews = user == null ? List.of() : data.getReviewsByBuyer(user.getUsername());
        if (myReviews.isEmpty()) {
            root.getChildren().add(emptyLabel("You have not submitted any reviews yet."));
        } else {
            for (Review review : myReviews) {
                VBox card = new VBox(6);
                card.getStyleClass().add("card");
                card.setPadding(new Insets(12));
                Label stars = new Label("★".repeat(review.getRating())
                        + "☆".repeat(5 - review.getRating()));
                stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
                Label comment = new Label(review.getComment());
                comment.setWrapText(true);
                Label meta = new Label("Seller: @" + review.getSellerUsername()
                        + " · " + review.getListingTitle()
                        + " · " + review.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy")));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
                card.getChildren().addAll(stars, comment, meta);
                if (review.getSellerReply() != null && !review.getSellerReply().isBlank()) {
                    Label reply = new Label("Seller reply: " + review.getSellerReply());
                    reply.setWrapText(true);
                    reply.setStyle("-fx-text-fill: #003974; -fx-font-size: 12px;");
                    card.getChildren().add(reply);
                }
                root.getChildren().add(card);
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    public void showNotifications() {
        currentSection = "notifications";
        setActive(btnNotifications, "Notification Board");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox toolbar = new HBox(10);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        ComboBox<String> categoryFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All", NotificationCategories.OFFER, NotificationCategories.CHAT,
                NotificationCategories.DUE, NotificationCategories.SYSTEM,
                NotificationCategories.RATING));
        categoryFilter.getSelectionModel().selectFirst();
        Button markAll = new Button("Mark all read");
        markAll.getStyleClass().addAll("btn-outline", "btn-sm");
        toolbar.getChildren().addAll(new Label("Category:"), categoryFilter, markAll);
        root.getChildren().add(toolbar);

        VBox list = new VBox(10);
        Runnable render = () -> {
            list.getChildren().clear();
            User user = currentUser();
            List<Notification> notifications = user == null ? List.of()
                    : data.getNotificationsFor(user.getUsername());
            String category = categoryFilter.getValue();
            for (Notification notification : notifications) {
                if (category != null && !"All".equals(category)
                        && !category.equals(notification.getCategory())) {
                    continue;
                }
                VBox item = new VBox(4);
                item.getStyleClass().add("notif-item");
                if (!notification.isRead()) {
                    item.setStyle("-fx-background-color: #eef4ff;");
                }
                Label title = new Label(notification.getTitle());
                title.setStyle("-fx-font-weight: bold; -fx-text-fill: #1a1a2e;");
                Label message = new Label(notification.getMessage());
                message.setWrapText(true);
                message.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");
                Label meta = new Label(notification.getCategory() + " · "
                        + notification.getTimestamp().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                        + (notification.isRead() ? "" : " · UNREAD"));
                meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
                item.getChildren().addAll(title, message, meta);
                item.setOnMouseClicked(event -> {
                    data.markNotificationRead(notification.getId());
                    refreshNotificationBadge();
                    showNotifications();
                });
                list.getChildren().add(item);
            }
            if (list.getChildren().isEmpty()) {
                list.getChildren().add(emptyLabel("No notifications in this category."));
            }
            refreshNotificationBadge();
        };

        categoryFilter.valueProperty().addListener((obs, oldValue, newValue) -> render.run());
        markAll.setOnAction(event -> {
            User user = currentUser();
            if (user != null) {
                data.markAllNotificationsRead(user.getUsername());
                refreshNotificationBadge();
                render.run();
            }
        });
        root.getChildren().add(list);
        render.run();

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    @FXML
    public void showProfile() {
        currentSection = "profile";
        setActive(btnProfile, "Manage Profile");
        contentPane.getChildren().clear();

        User user = currentUser();
        VBox form = new VBox(10);
        form.setPadding(new Insets(8));
        form.setMaxWidth(460);
        form.getStyleClass().add("card");

        Label title = new Label("Edit your profile");
        title.getStyleClass().add("section-title");

        TextField nameField = new TextField(user != null ? user.getFullName() : "");
        nameField.setPromptText("Full Name");
        PasswordField newPass = new PasswordField();
        newPass.setPromptText("New password (leave blank to keep)");
        PasswordField confirmPass = new PasswordField();
        confirmPass.setPromptText("Confirm new password");
        ComboBox<String> residence = new ComboBox<>(
                FXCollections.observableArrayList(DummyDataService.BUYER_RESIDENCES));
        if (user != null && user.getResidenceHall() != null) {
            residence.setValue(user.getResidenceHall());
        }
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button saveBtn = new Button("Save Changes");
        saveBtn.getStyleClass().add("btn-primary");
        saveBtn.setOnAction(event -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            try {
                data.updateBuyerProfile(currentUser(), nameField.getText(),
                        newPass.getText(), confirmPass.getText(), residence.getValue());
                if (currentUser() != null) {
                    welcomeLabel.setText("Hello, " + currentUser().getFullName());
                }
                feedback.setText("Profile updated successfully.");
                feedback.setStyle("-fx-text-fill: #2e7d32; -fx-font-size: 13px;");
                feedback.setVisible(true);
                feedback.setManaged(true);
                newPass.clear();
                confirmPass.clear();
            } catch (RuntimeException ex) {
                feedback.setText(ex.getMessage());
                feedback.setStyle("-fx-text-fill: #c62828; -fx-font-size: 13px;");
                feedback.setVisible(true);
                feedback.setManaged(true);
            }
        });

        form.getChildren().addAll(title,
                new Label("Full Name"), nameField,
                new Label("New Password"), newPass,
                new Label("Confirm Password"), confirmPass,
                new Label("Residence"), residence,
                saveBtn, feedback);

        contentPane.getChildren().add(form);
    }

    @FXML
    private void logout() {
        BorrowLifecycleScheduler.setOnChangeListener(null);
        SessionManager.logout();
        SceneManager.showWelcome();
    }

    // ---------- UI helpers ----------
    private static Double parseDoubleOrNull(String text) {
        if (text == null || text.isBlank()) return null;
        try {
            return Double.parseDouble(text.trim());
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static ListingType mapType(String value) {
        if (value == null) return null;
        return switch (value) {
            case "Sell" -> ListingType.SELL;
            case "Lend" -> ListingType.LEND;
            case "Giveaway" -> ListingType.GIVEAWAY;
            default -> null;
        };
    }

    private static ListingSort mapSort(String value) {
        if (value == null) return ListingSort.NEWEST;
        return switch (value) {
            case "Price: Low to High" -> ListingSort.PRICE_LOW_HIGH;
            case "Price: High to Low" -> ListingSort.PRICE_HIGH_LOW;
            default -> ListingSort.NEWEST;
        };
    }

    private static String dealTypeDisplay(ListingType type) {
        if (type == null) return "";
        return switch (type) {
            case SELL -> "Bought";
            case LEND -> "Borrowed";
            case GIVEAWAY -> "Received";
        };
    }

    private static String dealDate(Deal deal) {
        if (deal.getCompletedAt() != null) {
            return deal.getCompletedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
        }
        return deal.getCreatedAt() == null ? ""
                : deal.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"));
    }

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        return label;
    }

    private static Label sectionTitle(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-font-size: 16px; -fx-font-weight: bold; -fx-text-fill: #003974;");
        return label;
    }

    private static Label emptyLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a;");
        return label;
    }

    private static Label statusBadge(String status) {
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
}