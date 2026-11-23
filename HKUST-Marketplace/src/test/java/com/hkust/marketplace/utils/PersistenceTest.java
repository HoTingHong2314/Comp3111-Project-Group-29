package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PersistenceTest {

    @TempDir
    Path tempDir;

    @Test
    void dataSurvivesRestart() {
        Path file = tempDir.resolve("marketplace-data.json");

        DummyDataService first = new DummyDataService(new MarketplaceDataStore(file));
        first.registerBuyer("tester", "Test Buyer", "Password1", "Password1", "Hall 1");

        Listing listing = new Listing();
        listing.setTitle("Persistence Test Item");
        listing.setDescription("Round trip");
        listing.setCategory("Textbooks");
        listing.setType(ListingType.SELL);
        listing.setCondition("Used");
        listing.setPrice(50.0);
        listing.setMeetupLocation("Library");
        listing.setSellerUsername("charlie");
        listing.setSellerFullName("Charlie Lee");
        listing.setStatus(ListingStatus.LIVE);
        first.addListing(listing);

        DummyDataService second = new DummyDataService(new MarketplaceDataStore(file));
        assertNotNull(second.findUser("tester"));
        assertEquals("Test Buyer", second.findUser("tester").getFullName());
        assertTrue(second.usernameExists("admin"));
        assertNotNull(second.findListing(listing.getId()));
        assertEquals("Persistence Test Item", second.findListing(listing.getId()).getTitle());

        User buyer = second.findUser("tester");
        Deal deal = second.submitOffer(buyer, listing.getId(), 40.0);
        var thread = second.getOrCreateChatThread(listing.getId(), "tester");
        second.sendMessage(buyer, thread.id(), "Hello persistence");

        DummyDataService third = new DummyDataService(new MarketplaceDataStore(file));
        Deal reloaded = third.findDeal(deal.getId());
        assertNotNull(reloaded);
        assertEquals(deal.getOfferPrice(), reloaded.getOfferPrice());
        assertEquals(deal.getStatus(), reloaded.getStatus());

        List<DummyDataService.ChatMessage> messages = third.getChatMessages(thread.id());
        assertEquals(1, messages.size());
        assertEquals("Hello persistence", messages.get(0).content());
        assertTrue(third.getNotificationsFor("charlie").size() >= 2);
        assertFalse(third.getLiveListings().isEmpty());
    }

    @Test
    void corruptDataFileIsArchived() throws Exception {
        Path file = tempDir.resolve("corrupt.json");
        Files.writeString(file, "{ this is not valid json");
        MarketplaceDataStore store = new MarketplaceDataStore(file);

        assertNull(store.load());
        assertFalse(Files.exists(file));
        try (var files = Files.list(tempDir)) {
            assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
        }
    }

    @Test
    void newerDataFormatVersionIsRejected() throws Exception {
        Path file = tempDir.resolve("future.json");
        Files.writeString(file, "{\"version\": 999}");
        MarketplaceDataStore store = new MarketplaceDataStore(file);

        assertThrows(IllegalStateException.class, store::load);
    }

    @Test
    void legacyPlaintextPasswordIsMigratedToBCrypt() {
        Path file = tempDir.resolve("legacy-password.json");
        MarketplaceDataStore store = new MarketplaceDataStore(file);
        new DummyDataService(store);

        MarketplaceDataStore.Snapshot snapshot = store.load();
        User alice = snapshot.users.stream()
                .filter(user -> "alice".equals(user.getUsername()))
                .findFirst().orElseThrow();
        alice.setPasswordHash("password1");
        store.save(snapshot);

        DummyDataService migrated = new DummyDataService(store);
        assertNotNull(migrated.login("alice", "password1", UserRole.BUYER));

        User stored = store.load().users.stream()
                .filter(user -> "alice".equals(user.getUsername()))
                .findFirst().orElseThrow();
        assertTrue(PasswordUtil.isHashed(stored.getPasswordHash()));
    }

    @Test
    void saveFailureIsPropagated() throws Exception {
        Path blocker = tempDir.resolve("not-a-directory");
        Files.writeString(blocker, "x");
        MarketplaceDataStore store = new MarketplaceDataStore(blocker.resolve("data.json"));

        assertThrows(UncheckedIOException.class, () -> new DummyDataService(store));
    }
}
