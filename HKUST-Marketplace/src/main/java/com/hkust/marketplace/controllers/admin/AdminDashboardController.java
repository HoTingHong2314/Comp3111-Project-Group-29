package com.hkust.marketplace.controllers.admin;

import com.hkust.marketplace.models.*;
import com.hkust.marketplace.utils.DealStatuses;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.PriceUtil;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

/**
 * Administrator controller for review, users, listings, deals, ratings and reports.
 */
public class AdminDashboardController {

    @FXML private StackPane contentPane;
    @FXML private Label pageTitle;
    @FXML private Label welcomeLabel;
    @FXML private Button btnReview, btnAllListings, btnUsers, btnDeals, btnRatings, btnReports, btnNotifications, btnProfile;

    private final DummyDataService data = DummyDataService.getInstance();
    private Button currentActiveBtn;

    @FXML
    public void initialize() {
        User user = currentUser();
        if (user != null) welcomeLabel.setText("Admin: " + user.getFullName());
        currentActiveBtn = btnReview;
        showReview();
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
    private void showReview() {
        setActive(btnReview, "Listing Review & Approval");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        List<Listing> pending = data.getPendingListings();
        if (pending.isEmpty()) {
            root.getChildren().add(infoLabel("No listings awaiting review."));
        } else {
            for (Listing listing : pending) {
                root.getChildren().add(createPendingCard(listing));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createPendingCard(Listing listing) {
        VBox card = new VBox(8);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(14));

        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 16px;");
        Label meta = new Label("Seller: " + listing.getSellerFullName() + " (@" + listing.getSellerUsername() + ")"
                + " · " + listing.getCategory() + " · " + listing.getTypeDisplay()
                + " · " + listing.getMeetupLocation());
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label details = new Label("Condition: " + listing.getCondition()
                + " · Price: " + PriceUtil.displayListingPrice(listing)
                + " · Photos: " + (listing.getPhotoPaths() == null ? 0 : listing.getPhotoPaths().size()));
        details.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label submitted = new Label("Submitted: " + listing.getPostedAt().format(
                DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm")) + " · Status: Pending Review");
        submitted.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        Label description = new Label(listing.getDescription() == null ? "" : listing.getDescription());
        description.setWrapText(true);
        description.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");

        HBox photoBox = new HBox(6);
        if (listing.getPhotoPaths() != null) {
            listing.getPhotoPaths().stream().limit(6).forEach(path -> {
                File file = new File(path);
                if (file.exists()) {
                    ImageView image = new ImageView(new Image(file.toURI().toString(), 110, 110, true, true));
                    image.setFitWidth(110);
                    image.setFitHeight(110);
                    image.setPreserveRatio(true);
                    photoBox.getChildren().add(image);
                }
            });
        }

        HBox actions = new HBox(8);
        Button approve = new Button("Approve");
        approve.getStyleClass().addAll("btn-success", "btn-sm");
        approve.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION, "Approve this listing?");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    try {
                        data.approveListing(currentUser(), listing.getId());
                        showReview();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                }
            });
        });

        Button reject = new Button("Reject");
        reject.getStyleClass().addAll("btn-danger", "btn-sm");
        reject.setOnAction(event -> {
            TextInputDialog dialog = new TextInputDialog();
            dialog.setTitle("Rejection Reason");
            dialog.setHeaderText("Reject: " + listing.getTitle());
            dialog.setContentText("Reason:");
            dialog.showAndWait().ifPresent(reason -> {
                try {
                    data.rejectListing(currentUser(), listing.getId(), reason);
                    showReview();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });
        });
        actions.getChildren().addAll(approve, reject);
        card.getChildren().addAll(title, meta, details, submitted, description);
        if (!photoBox.getChildren().isEmpty()) card.getChildren().add(photoBox);
        card.getChildren().add(actions);
        return card;
    }

    @FXML
    private void showAllListings() {
        setActive(btnAllListings, "Manage All Listings");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        Button add = new Button("+ Add Listing (on behalf of seller)");
        add.getStyleClass().add("btn-primary");
        add.setOnAction(event -> showListingForm(null));
        root.getChildren().add(add);

        List<Listing> listings = new ArrayList<>(data.getAllListings());
        if (listings.isEmpty()) {
            root.getChildren().add(infoLabel("No listings in the system."));
        } else {
            for (Listing listing : listings) {
                root.getChildren().add(createAdminListingRow(listing));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createAdminListingRow(Listing listing) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label title = new Label(listing.getTitle());
        title.setStyle("-fx-font-weight: bold;");
        Label meta = new Label("@" + listing.getSellerUsername() + " · " + listing.getTypeDisplay()
                + " · " + listing.getCategory() + " · " + PriceUtil.displayListingPrice(listing));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        Label location = new Label(listing.getMeetupLocation() + " · Views: " + listing.getViewCount());
        location.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 11px;");
        info.getChildren().addAll(title, meta, location);

        Label status = statusBadge(listing.getStatus());
        row.getChildren().addAll(info, status);

        if (listing.getStatus() == ListingStatus.REMOVED) {
            row.getChildren().add(infoLabel("Removed - no further action"));
            return row;
        }
        if (listing.getStatus() != ListingStatus.REJECTED && data.isListingEditable(listing.getId())) {
            Button edit = new Button("Edit");
            edit.getStyleClass().addAll("btn-outline", "btn-sm");
            edit.setOnAction(event -> showListingForm(listing));
            row.getChildren().add(edit);
        }
        if (listing.getStatus() == ListingStatus.REJECTED) {
            row.getChildren().add(infoLabel("Rejected - seller must edit and resubmit"));
        }
        Button remove = new Button("Remove");
        remove.getStyleClass().addAll("btn-danger", "btn-sm");
        remove.setOnAction(event -> removeListing(listing));
        row.getChildren().add(remove);
        return row;
    }

    private void removeListing(Listing listing) {
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Remove Listing");
        dialog.setHeaderText("Remove: " + listing.getTitle());
        dialog.setContentText("Reason (buyers with active offers will be notified):");
        dialog.showAndWait().ifPresent(reason -> {
            try {
                data.takeDownListing(currentUser(), listing.getId(), reason);
                showAllListings();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
    }

    private void showListingForm(Listing existing) {
        setActive(btnAllListings, existing == null ? "Add Listing" : "Edit Listing");
        contentPane.getChildren().clear();

        VBox form = new VBox(9);
        form.setMaxWidth(650);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(18));

        Label title = new Label(existing == null ? "Add listing for a seller" : "Edit listing");
        title.getStyleClass().add("section-title");

        List<User> sellers = data.getUsersByRole(UserRole.SELLER);
        ComboBox<String> sellerCombo = new ComboBox<>();
        sellerCombo.setMaxWidth(Double.MAX_VALUE);
        for (User seller : sellers) {
            sellerCombo.getItems().add(seller.getUsername() + " (" + seller.getFullName() + ")");
        }
        sellerCombo.setPromptText("Seller *");
        if (existing != null) {
            sellerCombo.getItems().stream()
                    .filter(value -> value.startsWith(existing.getSellerUsername() + " "))
                    .findFirst().ifPresent(sellerCombo::setValue);
            sellerCombo.setDisable(true);
        }

        ComboBox<String> typeCombo = new ComboBox<>(FXCollections.observableArrayList("Sell", "Lend", "Give Away"));
        typeCombo.setMaxWidth(Double.MAX_VALUE);
        typeCombo.setPromptText("Listing type *");
        if (existing != null) {
            typeCombo.setValue(existing.getType() == ListingType.SELL ? "Sell"
                    : existing.getType() == ListingType.LEND ? "Lend" : "Give Away");
        }

        TextField nameField = new TextField(existing == null ? "" : existing.getTitle());
        nameField.setPromptText("Item name *");
        ComboBox<String> categoryCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CATEGORIES));
        categoryCombo.setMaxWidth(Double.MAX_VALUE);
        categoryCombo.setPromptText("Category *");
        if (existing != null) categoryCombo.setValue(existing.getCategory());
        ComboBox<String> conditionCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.CONDITIONS));
        conditionCombo.setMaxWidth(Double.MAX_VALUE);
        conditionCombo.setPromptText("Condition *");
        if (existing != null) conditionCombo.setValue(existing.getCondition());

        TextArea descriptionArea = new TextArea(existing == null ? "" : existing.getDescription());
        descriptionArea.setPrefRowCount(3);
        descriptionArea.setPromptText("Description");
        TextField priceField = new TextField(existing == null || existing.getPrice() == null
                ? "" : String.valueOf(existing.getPrice()));
        priceField.setPromptText("Price (Sell only)");
        TextField daysField = new TextField(existing == null || existing.getLendingDays() == null
                ? "" : String.valueOf(existing.getLendingDays()));
        daysField.setPromptText("Lending days (Lend only)");
        ComboBox<String> meetupCombo = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetupCombo.setMaxWidth(Double.MAX_VALUE);
        meetupCombo.setPromptText("Meet-up location *");
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
            priceField.setDisable(!"Sell".equals(typeCombo.getValue()));
            daysField.setDisable(!"Lend".equals(typeCombo.getValue()));
        };
        typeCombo.valueProperty().addListener((obs, oldValue, newValue) -> updateTypeFields.run());
        updateTypeFields.run();

        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);

        Button save = new Button(existing == null ? "Add Listing" : "Save Changes");
        save.getStyleClass().add("btn-primary");
        save.setOnAction(event -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            try {
                Listing listing = existing == null ? new Listing() : copyListing(existing);
                if (existing == null) {
                    String selected = sellerCombo.getValue();
                    if (selected == null || !selected.contains(" (")) {
                        throw new IllegalArgumentException("Please choose a seller.");
                    }
                    listing.setSellerUsername(selected.substring(0, selected.indexOf(" (")));
                }
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
                    data.addListingAsAdmin(currentUser(), listing);
                } else {
                    data.updateListingAsAdmin(currentUser(), listing);
                }
                showAllListings();
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
                new Label("Seller *"), sellerCombo,
                new Label("Listing Type *"), typeCombo,
                new Label("Item Name *"), nameField,
                new Label("Category *"), categoryCombo,
                new Label("Condition *"), conditionCombo,
                new Label("Description"), descriptionArea,
                new Label("Price (Sell only)"), priceField,
                new Label("Lending Days (Lend only)"), daysField,
                new Label("Meet-up Location *"), meetupCombo,
                new HBox(8, choosePhotos, photoLabel),
                save, feedback);

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
    private void showUsers() {
        setActive(btnUsers, "Manage All Users");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));
        for (User user : data.getAllUsers()) {
            if (user.getRole() == UserRole.ADMIN) continue;
            root.getChildren().add(createUserRow(user));
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private HBox createUserRow(User user) {
        HBox row = new HBox(12);
        row.getStyleClass().add("card");
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(12));

        VBox info = new VBox(3);
        HBox.setHgrow(info, Priority.ALWAYS);
        Label name = new Label(user.getFullName() + " (@" + user.getUsername() + ")");
        name.setStyle("-fx-font-weight: bold;");
        Label meta = new Label(userMeta(user));
        meta.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        info.getChildren().addAll(name, meta);

        if (user.getRole() == UserRole.ADMIN) {
            row.getChildren().add(infoLabel("Administrator account"));
            return row;
        }

        Button edit = new Button("Edit");
        edit.getStyleClass().addAll("btn-outline", "btn-sm");
        edit.setOnAction(event -> editUser(user));

        Button toggle = new Button(user.isActive() ? "Deactivate" : "Activate");
        toggle.getStyleClass().addAll(user.isActive() ? "btn-danger" : "btn-success", "btn-sm");
        toggle.setOnAction(event -> {
            try {
                data.setUserActiveAsAdmin(currentUser(), user.getUsername(), !user.isActive());
                showUsers();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });

        Button delete = new Button("Delete");
        delete.getStyleClass().addAll("btn-danger", "btn-sm");
        delete.setOnAction(event -> {
            Alert confirm = new Alert(Alert.AlertType.CONFIRMATION,
                    "Permanently delete @" + user.getUsername() + "?\n"
                            + "The account is removed. Existing listings/deals/chats are retained for history.");
            confirm.showAndWait().ifPresent(result -> {
                if (result == ButtonType.OK) {
                    try {
                        data.deleteUserAsAdmin(currentUser(), user.getUsername());
                        showUsers();
                    } catch (RuntimeException ex) {
                        showError(ex.getMessage());
                    }
                }
            });
        });

        row.getChildren().addAll(info, edit, toggle, delete);
        return row;
    }

    private static String userMeta(User user) {
        StringBuilder meta = new StringBuilder(user.getRole().name());
        meta.append(user.isActive() ? " · Active" : " · Deactivated");
        if (user.getRole() == UserRole.BUYER && user.getResidenceHall() != null) {
            meta.append(" · ").append(user.getResidenceHall());
        } else if (user.getRole() == UserRole.SELLER && user.getPreferredMeetup() != null) {
            meta.append(" · Meetup: ").append(user.getPreferredMeetup());
        }
        return meta.toString();
    }

    private void editUser(User user) {
        if (user.getRole() == UserRole.ADMIN) {
            return;
        }
        Dialog<ButtonType> dialog = new Dialog<>();
        dialog.setTitle("Edit User");
        dialog.setHeaderText("@" + user.getUsername() + " · " + user.getRole().name());
        ButtonType save = new ButtonType("Save", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(save, ButtonType.CANCEL);

        TextField name = new TextField(user.getFullName());
        ComboBox<String> residence = new ComboBox<>(
                FXCollections.observableArrayList(DummyDataService.BUYER_RESIDENCES));
        residence.setMaxWidth(Double.MAX_VALUE);
        residence.setPromptText("Residence (optional)");
        if (user.getResidenceHall() != null) {
            residence.setValue(user.getResidenceHall());
        }
        TextArea bio = new TextArea(user.getBio() == null ? "" : user.getBio());
        bio.setPrefRowCount(2);
        ComboBox<String> meetup = new ComboBox<>(FXCollections.observableArrayList(DummyDataService.HKUST_LOCATIONS));
        meetup.setMaxWidth(Double.MAX_VALUE);
        meetup.setPromptText("Preferred meet-up (optional)");
        meetup.setValue(user.getPreferredMeetup());

        GridPane grid = new GridPane();
        grid.setHgap(8);
        grid.setVgap(8);
        grid.setPadding(new Insets(10));
        grid.addRow(0, new Label("Full Name"), name);
        if (user.getRole() == UserRole.BUYER) {
            grid.addRow(1, new Label("Residence"), residence);
        } else {
            grid.addRow(1, new Label("Bio"), bio);
            grid.addRow(2, new Label("Preferred Meetup"), meetup);
        }
        dialog.getDialogPane().setContent(grid);

        Optional<ButtonType> result = dialog.showAndWait();
        if (result.isPresent() && result.get() == save) {
            try {
                if (user.getRole() == UserRole.BUYER) {
                    data.updateUserAsAdmin(currentUser(), user.getUsername(),
                            name.getText(), residence.getValue(), null, null);
                } else {
                    data.updateUserAsAdmin(currentUser(), user.getUsername(),
                            name.getText(), null, bio.getText(), meetup.getValue());
                }
                showUsers();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        }
    }

    @FXML
    private void showDeals() {
        setActive(btnDeals, "All Deals Record");
        contentPane.getChildren().clear();

        VBox root = new VBox(12);
        root.setPadding(new Insets(4));

        HBox filters = new HBox(8);
        filters.setAlignment(Pos.CENTER_LEFT);
        TextField search = new TextField();
        search.setPromptText("Search item / buyer / seller...");
        search.setPrefWidth(220);
        ComboBox<String> typeFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Types", "Sell", "Lend", "Giveaway"));
        typeFilter.getSelectionModel().selectFirst();
        ComboBox<String> statusFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All Statuses", DealStatuses.PENDING, DealStatuses.COUNTERED,
                DealStatuses.ACCEPTED, DealStatuses.COMPLETED, DealStatuses.RETURNED,
                DealStatuses.DECLINED, DealStatuses.CANCELLED));
        statusFilter.getSelectionModel().selectFirst();
        filters.getChildren().addAll(search, typeFilter, statusFilter);
        root.getChildren().add(filters);

        TableView<Deal> table = new TableView<>();
        table.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        TableColumn<Deal, String> item = new TableColumn<>("Item");
        item.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getListingTitle()));
        TableColumn<Deal, String> buyer = new TableColumn<>("Buyer");
        buyer.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getBuyerUsername()));
        TableColumn<Deal, String> seller = new TableColumn<>("Seller");
        seller.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getSellerUsername()));
        TableColumn<Deal, String> type = new TableColumn<>("Type");
        type.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getDealType() == null
                ? "" : c.getValue().getDealType().name()));
        TableColumn<Deal, String> start = new TableColumn<>("Start Date");
        start.setCellValueFactory(c -> new SimpleStringProperty(formatDealStart(c.getValue())));
        TableColumn<Deal, String> end = new TableColumn<>("Return / Complete");
        end.setCellValueFactory(c -> new SimpleStringProperty(formatDealEnd(c.getValue())));
        TableColumn<Deal, String> status = new TableColumn<>("Status");
        status.setCellValueFactory(c -> new SimpleStringProperty(c.getValue().getStatus()));
        table.getColumns().addAll(item, buyer, seller, type, start, end, status);
        VBox.setVgrow(table, Priority.ALWAYS);

        Runnable applyFilters = () -> {
            String keyword = search.getText() == null ? "" : search.getText().trim().toLowerCase();
            String typeValue = typeFilter.getValue();
            String statusValue = statusFilter.getValue();
            List<Deal> filtered = data.getAllDeals().stream()
                    .filter(d -> keyword.isEmpty()
                            || d.getListingTitle().toLowerCase().contains(keyword)
                            || d.getBuyerUsername().toLowerCase().contains(keyword)
                            || d.getSellerUsername().toLowerCase().contains(keyword))
                    .filter(d -> "All Types".equals(typeValue)
                            || (d.getDealType() != null && d.getDealType().name().equalsIgnoreCase(typeValue)))
                    .filter(d -> "All Statuses".equals(statusValue)
                            || statusValue.equals(d.getStatus()))
                    .collect(Collectors.toList());
            table.setItems(FXCollections.observableArrayList(filtered));
        };
        search.textProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        typeFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        statusFilter.valueProperty().addListener((obs, oldValue, newValue) -> applyFilters.run());
        applyFilters.run();

        root.getChildren().add(table);
        contentPane.getChildren().add(root);
    }

    private String formatDealStart(Deal deal) {
        DummyDataService.DealExtras extra = data.findDealExtras(deal.getId());
        LocalDateTime value = deal.getDealType() == ListingType.LEND
                && extra != null && extra.borrowStartAt != null
                ? extra.borrowStartAt
                : (deal.getBorrowStart() == null
                ? deal.getCreatedAt() : deal.getBorrowStart().atStartOfDay());
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }

    private String formatDealEnd(Deal deal) {
        DummyDataService.DealExtras extra = data.findDealExtras(deal.getId());
        LocalDateTime value = deal.getCompletedAt();
        if (value == null && deal.getDealType() == ListingType.LEND) {
            value = extra == null ? null : extra.borrowEndAt;
            if (value == null && deal.getBorrowEnd() != null) {
                value = deal.getBorrowEnd().atTime(23, 59);
            }
        }
        return value == null ? "" : value.format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"));
    }

    @FXML
    private void showRatings() {
        setActive(btnRatings, "Seller Ratings Overview");
        contentPane.getChildren().clear();

        VBox root = new VBox(10);
        root.setPadding(new Insets(4));
        HBox filters = new HBox(8);
        filters.setAlignment(Pos.CENTER_LEFT);
        TextField sellerField = new TextField();
        sellerField.setPromptText("Seller username");
        sellerField.setPrefWidth(150);
        ComboBox<String> scoreFilter = new ComboBox<>(FXCollections.observableArrayList(
                "All scores", "5", "4", "3", "2", "1"));
        scoreFilter.getSelectionModel().selectFirst();
        DatePicker from = new DatePicker();
        from.setPromptText("From");
        DatePicker to = new DatePicker();
        to.setPromptText("To");
        filters.getChildren().addAll(sellerField, scoreFilter, new Label("Date:"), from, new Label("-"), to);
        root.getChildren().add(filters);

        VBox list = new VBox(10);
        Runnable render = () -> {
            list.getChildren().clear();
            String sellerFilter = sellerField.getText() == null ? "" : sellerField.getText().trim().toLowerCase();
            int score = scoreFilter.getValue() == null || "All scores".equals(scoreFilter.getValue())
                    ? 0 : Integer.parseInt(scoreFilter.getValue());
            List<Review> reviews = data.getAllReviews().stream()
                    .filter(r -> sellerFilter.isEmpty()
                            || r.getSellerUsername().toLowerCase().contains(sellerFilter))
                    .filter(r -> score == 0 || r.getRating() == score)
                    .filter(r -> {
                        LocalDate date = r.getCreatedAt().toLocalDate();
                        if (from.getValue() != null && date.isBefore(from.getValue())) return false;
                        return to.getValue() == null || !date.isAfter(to.getValue());
                    })
                    .collect(Collectors.toList());
            if (reviews.isEmpty()) {
                list.getChildren().add(infoLabel("No ratings match the filters."));
            } else {
                for (Review review : reviews) {
                    list.getChildren().add(createRatingCard(review));
                }
            }
        };
        sellerField.textProperty().addListener((obs, oldValue, newValue) -> render.run());
        scoreFilter.valueProperty().addListener((obs, oldValue, newValue) -> render.run());
        from.valueProperty().addListener((obs, oldValue, newValue) -> render.run());
        to.valueProperty().addListener((obs, oldValue, newValue) -> render.run());
        render.run();
        root.getChildren().add(list);

        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createRatingCard(Review review) {
        VBox card = new VBox(5);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));
        Label stars = new Label("★".repeat(review.getRating()) + "☆".repeat(5 - review.getRating()));
        stars.setStyle("-fx-text-fill: #A68137; -fx-font-size: 16px;");
        Label comment = new Label(review.getComment());
        comment.setWrapText(true);
        Label meta = new Label("@" + review.getSellerUsername() + " by @" + review.getBuyerUsername()
                + " · " + review.getListingTitle() + " · "
                + review.getCreatedAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy"))
                + (review.isHidden() ? " · HIDDEN" : ""));
        meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        Button toggle = new Button(review.isHidden() ? "Unhide" : "Hide");
        toggle.getStyleClass().addAll("btn-outline", "btn-sm");
        toggle.setOnAction(event -> {
            try {
                data.setReviewHidden(currentUser(), review.getId(), !review.isHidden());
                showRatings();
            } catch (RuntimeException ex) {
                showError(ex.getMessage());
            }
        });
        card.getChildren().addAll(stars, comment, meta, toggle);
        return card;
    }

    @FXML
    private void showReports() {
        setActive(btnReports, "Listing Reports");
        contentPane.getChildren().clear();

        VBox root = new VBox(10);
        root.setPadding(new Insets(4));
        List<DummyDataService.ReportRecord> reports = data.getAllReports().stream()
                .sorted(Comparator.comparing(DummyDataService.ReportRecord::createdAt).reversed())
                .collect(Collectors.toList());
        if (reports.isEmpty()) {
            root.getChildren().add(infoLabel("No listing reports."));
        } else {
            for (DummyDataService.ReportRecord report : reports) {
                root.getChildren().add(createReportCard(report));
            }
        }
        ScrollPane scroll = new ScrollPane(root);
        scroll.setFitToWidth(true);
        contentPane.getChildren().add(scroll);
    }

    private VBox createReportCard(DummyDataService.ReportRecord report) {
        VBox card = new VBox(6);
        card.getStyleClass().add("card");
        card.setPadding(new Insets(12));

        Label title = new Label(report.listingTitle());
        title.setStyle("-fx-font-weight: bold; -fx-font-size: 15px;");
        Label meta = new Label("Reported by @" + report.buyerUsername()
                + " · " + report.createdAt().format(DateTimeFormatter.ofPattern("dd MMM yyyy HH:mm"))
                + " · Status: " + report.status());
        meta.setStyle("-fx-text-fill: #9aa0b0; -fx-font-size: 12px;");
        Label reason = new Label("Reason: " + report.reason());
        reason.setWrapText(true);
        reason.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 13px;");

        card.getChildren().addAll(title, meta, reason);
        if ("Open".equals(report.status())) {
            Button resolve = new Button("Mark Resolved");
            resolve.getStyleClass().addAll("btn-success", "btn-sm");
            resolve.setOnAction(event -> {
                try {
                    data.resolveReport(currentUser(), report.id());
                    showReports();
                } catch (RuntimeException ex) {
                    showError(ex.getMessage());
                }
            });
            card.getChildren().add(resolve);
        }
        return card;
    }

    @FXML
    private void showNotifications() {
        setActive(btnNotifications, "Admin Notifications");
        contentPane.getChildren().clear();

        VBox root = new VBox(10);
        root.setPadding(new Insets(4));
        Button markAll = new Button("Mark all read");
        markAll.getStyleClass().addAll("btn-outline", "btn-sm");
        markAll.setOnAction(event -> {
            data.markAllNotificationsRead("admin");
            showNotifications();
        });
        root.getChildren().add(markAll);

        List<Notification> notifications = data.getNotificationsFor("admin");
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

        User admin = currentUser();
        VBox form = new VBox(10);
        form.setMaxWidth(440);
        form.getStyleClass().add("card");
        form.setPadding(new Insets(18));
        Label title = new Label("Admin Profile");
        title.getStyleClass().add("section-title");
        TextField name = new TextField(admin == null ? "" : admin.getFullName());
        PasswordField password = new PasswordField();
        password.setPromptText("New password (optional)");
        PasswordField confirm = new PasswordField();
        confirm.setPromptText("Confirm new password");
        Label feedback = new Label();
        feedback.setWrapText(true);
        feedback.setVisible(false);
        feedback.setManaged(false);
        Button save = new Button("Save Changes");
        save.getStyleClass().add("btn-success");
        save.setOnAction(event -> {
            feedback.setVisible(false);
            feedback.setManaged(false);
            try {
                data.updateAdminProfile(currentUser(), name.getText(), password.getText(), confirm.getText());
                if (currentUser() != null) welcomeLabel.setText("Admin: " + currentUser().getFullName());
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
                save, feedback);
        contentPane.getChildren().add(form);
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

    private static Label infoLabel(String text) {
        Label label = new Label(text);
        label.setStyle("-fx-text-fill: #5a5a7a; -fx-font-size: 12px;");
        return label;
    }

    private void showError(String message) {
        Alert alert = new Alert(Alert.AlertType.ERROR);
        alert.setTitle("Error");
        alert.setHeaderText(null);
        alert.setContentText(message == null ? "Something went wrong." : message);
        alert.showAndWait();
    }

    @FXML
    private void logout() {
        SessionManager.logout();
        SceneManager.showWelcome();
    }
}
