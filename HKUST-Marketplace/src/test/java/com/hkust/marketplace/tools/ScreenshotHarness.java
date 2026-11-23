package com.hkust.marketplace.tools;

import com.hkust.marketplace.controllers.buyer.BuyerDashboardController;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.SceneManager;
import com.hkust.marketplace.utils.SessionManager;
import javafx.application.Platform;
import javafx.fxml.FXMLLoader;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.TextField;
import javafx.scene.image.PixelReader;
import javafx.scene.image.WritableImage;
import javafx.stage.Stage;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.atomic.AtomicReference;

public final class ScreenshotHarness {

    private static final int WIDTH = 1400;
    private static final int HEIGHT = 850;
    private static Path output = Path.of("docs", "screenshots");
    private static final AtomicReference<Throwable> FAILURE = new AtomicReference<>();

    private ScreenshotHarness() {
    }

    public static void main(String[] args) throws Exception {
        if (args.length > 0) {
            output = Path.of(args[0]);
        }
        Path dataFile = Files.createTempFile("hkust-marketplace-screens", ".json");
        Files.deleteIfExists(dataFile);
        System.setProperty("marketplace.data.file", dataFile.toString());
        System.setProperty("marketplace.data.reset", "true");
        Files.createDirectories(output);

        CountDownLatch finished = new CountDownLatch(1);
        Platform.startup(() -> { });
        Platform.runLater(() -> {
            try {
                captureAll();
            } catch (Throwable ex) {
                FAILURE.set(ex);
            } finally {
                finished.countDown();
            }
        });
        finished.await();
        Platform.exit();
        Files.deleteIfExists(dataFile);
        if (FAILURE.get() != null) {
            throw new RuntimeException(FAILURE.get());
        }
    }

    private static void captureAll() throws Exception {
        Stage stage = new Stage();
        stage.setTitle("HKUST Marketplace Screenshot Capture");
        SceneManager.init(stage);

        SessionManager.logout();
        show(stage, "/fxml/WelcomeView.fxml");
        capture(stage, "01-welcome.png");

        show(stage, "/fxml/buyer/BuyerLoginView.fxml");
        setText(stage, "#usernameField", "alice");
        setText(stage, "#passwordField", "password1");
        capture(stage, "02-buyer-login.png");

        DummyDataService data = DummyDataService.getInstance();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing liveListing = data.getLiveListings().stream()
                .filter(listing -> listing.getType() == ListingType.SELL)
                .findFirst().orElseThrow();

        SessionManager.login(alice);
        FXMLLoader buyerLoader = show(stage, "/fxml/buyer/BuyerDashboardView.fxml");
        BuyerDashboardController buyer = buyerLoader.getController();
        capture(stage, "03-buyer-browse.png");
        setSearchText(stage, "MacBook");
        capture(stage, "04-buyer-browse-search.png");

        buyer.openListingDetail(liveListing);
        layout(stage);
        capture(stage, "05-listing-detail.png");

        fire(stage, "#btnDeals");
        capture(stage, "06-buyer-deals.png");
        fire(stage, "#btnNotifications");
        capture(stage, "07-buyer-notifications.png");

        SessionManager.login(charlie);
        show(stage, "/fxml/seller/SellerDashboardView.fxml");
        capture(stage, "08-seller-listings.png");
        fire(stage, "#btnOffers");
        capture(stage, "09-seller-offers.png");
        fire(stage, "#btnStats");
        capture(stage, "10-seller-stats.png");
        fire(stage, "#btnCreate");
        capture(stage, "11-seller-create-listing.png");

        data.reportListing(alice, liveListing.getId(), "Price looks suspicious");
        SessionManager.login(admin);
        show(stage, "/fxml/admin/AdminDashboardView.fxml");
        capture(stage, "12-admin-review.png");
        fire(stage, "#btnReports");
        capture(stage, "13-admin-reports.png");
        fire(stage, "#btnUsers");
        capture(stage, "14-admin-users.png");
        fire(stage, "#btnDeals");
        capture(stage, "15-admin-deals.png");

        SessionManager.login(alice);
        var thread = data.getOrCreateChatThread(liveListing.getId(), "alice");
        data.sendMessage(alice, thread.id(), "Hi, is this still available?");
        data.sendMessage(charlie, thread.id(), "Yes, it is! You can pick it up at the Library.");
        FXMLLoader chatLoader = load("/fxml/buyer/ChatView.fxml");
        chatLoader.<com.hkust.marketplace.controllers.buyer.ChatController>getController()
                .setThread(thread, () -> { });
        stage.setScene(newScene(chatLoader.getRoot()));
        stage.show();
        layout(stage);
        capture(stage, "16-buyer-chat.png");

        stage.close();
    }

    private static FXMLLoader show(Stage stage, String fxml) throws IOException {
        FXMLLoader loader = load(fxml);
        stage.setScene(newScene(loader.getRoot()));
        stage.show();
        layout(stage);
        return loader;
    }

    private static FXMLLoader load(String fxml) throws IOException {
        URL resource = ScreenshotHarness.class.getResource(fxml);
        if (resource == null) {
            throw new IOException("Missing FXML resource: " + fxml);
        }
        FXMLLoader loader = new FXMLLoader(resource);
        loader.load();
        return loader;
    }

    private static Scene newScene(Parent root) {
        Scene scene = new Scene(root, WIDTH, HEIGHT);
        URL css = ScreenshotHarness.class.getResource("/css/styles.css");
        if (css != null) {
            scene.getStylesheets().add(css.toExternalForm());
        }
        return scene;
    }

    private static void layout(Stage stage) {
        Scene scene = stage.getScene();
        if (scene != null) {
            scene.getRoot().applyCss();
            scene.getRoot().layout();
        }
    }

    private static void setText(Stage stage, String selector, String value) {
        Node node = stage.getScene().lookup(selector);
        if (node instanceof TextField field) {
            field.setText(value);
        }
    }

    private static void setSearchText(Stage stage, String value) {
        for (Node node : stage.getScene().getRoot().lookupAll(".text-field")) {
            if (node instanceof TextField field
                    && "Search listings...".equals(field.getPromptText())) {
                field.setText(value);
                return;
            }
        }
    }

    private static void fire(Stage stage, String selector) {
        Node node = stage.getScene().lookup(selector);
        if (node instanceof Button button) {
            button.fire();
        }
        layout(stage);
    }

    private static void capture(Stage stage, String fileName) throws IOException {
        layout(stage);
        WritableImage image = stage.getScene().snapshot(null);
        int width = (int) Math.ceil(image.getWidth());
        int height = (int) Math.ceil(image.getHeight());
        BufferedImage buffered = new BufferedImage(width, height, BufferedImage.TYPE_INT_ARGB);
        PixelReader reader = image.getPixelReader();
        for (int y = 0; y < height; y++) {
            for (int x = 0; x < width; x++) {
                buffered.setRGB(x, y, reader.getArgb(x, y));
            }
        }
        ImageIO.write(buffered, "png", output.resolve(fileName).toFile());
    }
}