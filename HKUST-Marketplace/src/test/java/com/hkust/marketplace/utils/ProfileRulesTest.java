package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ProfileRulesTest {

    @TempDir
    Path tempDir;

    private DummyDataService newService() {
        return new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("profiles-" + System.nanoTime() + ".json")));
    }

    @Test
    void adminEditOnlyAppliesRoleSpecificProfileFields() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");

        data.updateUserAsAdmin(admin, "alice", "Alice Buyer",
                "Hall 4", "ignored buyer bio", "Library");

        assertEquals("Alice Buyer", buyer.getFullName());
        assertEquals("Hall 4", buyer.getResidenceHall());
        assertNull(buyer.getBio());
        assertNull(buyer.getPreferredMeetup());

        data.updateUserAsAdmin(admin, "charlie", "Charlie Seller",
                "Hall 5", "Seller bio", "Library");

        assertEquals("Charlie Seller", seller.getFullName());
        assertNull(seller.getResidenceHall());
        assertEquals("Seller bio", seller.getBio());
        assertEquals("Library", seller.getPreferredMeetup());
    }

    @Test
    void buyerResidenceMustUseAllowedOptions() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        User buyer = data.findUser("alice");

        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer(
                "badresidence", "Bad Residence", "Password1", "Password1", "Hall 99"));
        assertFalse(data.usernameExists("badresidence"));

        assertThrows(IllegalArgumentException.class, () -> data.updateBuyerProfile(
                buyer, "Alice Chan", null, null, "Mars"));
        assertEquals("Alice Chan", buyer.getFullName());
        assertEquals("Hall 3", buyer.getResidenceHall());

        assertThrows(IllegalArgumentException.class, () -> data.updateUserAsAdmin(
                admin, "alice", "Alice Admin", "Hall 99", null, null));
        assertEquals("Alice Chan", buyer.getFullName());
        assertEquals("Hall 3", buyer.getResidenceHall());

        User optionalResidence = data.registerBuyer(
                "optionalbuyer", "Optional Buyer", "Password1", "Password1", null);
        assertNull(optionalResidence.getResidenceHall());

        data.updateBuyerProfile(optionalResidence, "Optional Buyer", null, null, " Hall 9 ");
        assertEquals("Hall 9", optionalResidence.getResidenceHall());

        data.updateBuyerProfile(optionalResidence, "Optional Buyer", null, null, " ");
        assertNull(optionalResidence.getResidenceHall());
    }

    @Test
    void profileUpdatesClearFieldsFromOtherRoles() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");

        buyer.setBio("legacy buyer bio");
        buyer.setPreferredMeetup("Library");
        data.updateBuyerProfile(buyer, "Alice Chan", null, null, "Hall 3");

        assertNull(buyer.getBio());
        assertNull(buyer.getPreferredMeetup());

        seller.setResidenceHall("Hall 6");
        data.updateSellerProfile(seller, "Charlie Lee", null, null,
                seller.getBio(), seller.getPreferredMeetup());

        assertNull(seller.getResidenceHall());
    }

    @Test
    void legacyProfileValuesAreNormalizedOnLoad() {
        Path file = tempDir.resolve("legacy-profiles.json");
        MarketplaceDataStore store = new MarketplaceDataStore(file);
        new DummyDataService(store);

        MarketplaceDataStore.Snapshot snapshot = store.load();
        User buyer = snapshot.users.stream()
                .filter(user -> "alice".equals(user.getUsername()))
                .findFirst().orElseThrow();
        buyer.setResidenceHall("Mars");
        buyer.setBio("hidden buyer bio");
        buyer.setPreferredMeetup("Library");

        User seller = snapshot.users.stream()
                .filter(user -> "charlie".equals(user.getUsername()))
                .findFirst().orElseThrow();
        seller.setResidenceHall("Hall 9");
        store.save(snapshot);

        DummyDataService reloaded = new DummyDataService(new MarketplaceDataStore(file));
        User normalizedBuyer = reloaded.findUser("alice");
        User normalizedSeller = reloaded.findUser("charlie");

        assertNull(normalizedBuyer.getResidenceHall());
        assertNull(normalizedBuyer.getBio());
        assertNull(normalizedBuyer.getPreferredMeetup());
        assertNull(normalizedSeller.getResidenceHall());

        MarketplaceDataStore.Snapshot saved = store.load();
        User savedBuyer = saved.users.stream()
                .filter(user -> "alice".equals(user.getUsername()))
                .findFirst().orElseThrow();
        assertNull(savedBuyer.getResidenceHall());
        assertNull(savedBuyer.getBio());
        assertNull(savedBuyer.getPreferredMeetup());
    }
}
