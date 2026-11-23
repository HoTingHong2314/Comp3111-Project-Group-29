package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class UtilityCoverageTest {

    @TempDir
    Path tempDir;

    @Test
    void priceUtilityBranches() {
        assertEquals("Price is required.", PriceUtil.validateInput(null));
        assertEquals("Price is required.", PriceUtil.validateInput(" "));
        assertNotOk(PriceUtil.validateInput("abc"));
        assertNotOk(PriceUtil.validateInput("12.345"));
        assertNotOk(PriceUtil.validateInput("0"));
        assertNotOk(PriceUtil.validateInput("-1"));
        assertEquals("OK", PriceUtil.validateInput("12.3"));
        assertEquals(12.3, PriceUtil.parse("12.3"));
        assertThrows(IllegalArgumentException.class, () -> PriceUtil.parse("bad"));

        assertFalse(PriceUtil.isValidAmount(0.0));
        assertFalse(PriceUtil.isValidAmount(-1.0));
        assertFalse(PriceUtil.isValidAmount(12.345));
        assertTrue(PriceUtil.isValidAmount(12.34));
        assertEquals("12.30", PriceUtil.format(12.3));

        Listing sell = listing(ListingType.SELL, 12.34);
        assertEquals(12.34, PriceUtil.effectivePrice(sell));
        assertEquals("HK$ 12.34", PriceUtil.displayListingPrice(sell));
        Listing sellWithoutPrice = listing(ListingType.SELL, null);
        assertEquals(0.0, PriceUtil.effectivePrice(sellWithoutPrice));
        assertEquals("Free", PriceUtil.displayListingPrice(sellWithoutPrice));
        Listing lend = listing(ListingType.LEND, null);
        assertEquals(0.0, PriceUtil.effectivePrice(lend));
        assertEquals("Borrow", PriceUtil.displayListingPrice(lend));
        Listing giveaway = listing(ListingType.GIVEAWAY, null);
        assertEquals(0.0, PriceUtil.effectivePrice(giveaway));
        assertEquals("Free", PriceUtil.displayListingPrice(giveaway));
    }

    @Test
    void passwordUtilityBranches() {
        String hash = PasswordUtil.hash("Password1");
        assertTrue(PasswordUtil.isHashed(hash));
        assertFalse(PasswordUtil.isHashed(null));
        assertFalse(PasswordUtil.isHashed("password1"));
        assertTrue(PasswordUtil.verify("Password1", hash));
        assertFalse(PasswordUtil.verify(null, hash));
        assertFalse(PasswordUtil.verify("Password1", null));
        assertFalse(PasswordUtil.verify("Password1", "password1"));
        assertFalse(PasswordUtil.verify("Password1", "$2a$invalid"));

        assertEquals("Password cannot be empty.", PasswordUtil.validate(null));
        assertEquals("Password cannot be empty.", PasswordUtil.validate(""));
        assertEquals("Password must be at least 8 characters.", PasswordUtil.validate("Ab1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.", PasswordUtil.validate("password1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.", PasswordUtil.validate("PASSWORD1"));
        assertEquals("Password should contain uppercase, lowercase and a digit.", PasswordUtil.validate("Password"));
        assertEquals("OK", PasswordUtil.validate("Password1"));
        assertEquals("OK", DummyDataService.checkPasswordStrength("Password1"));
    }

    @Test
    void listingModelBranches() {
        Listing listing = new Listing();
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertEquals(0, listing.getViewCount());
        assertNotNull(listing.getPostedAt());
        assertEquals("Free", listing.getPriceDisplay());
        assertEquals("Free", listing.getPriceDisplay());
        listing.setType(ListingType.SELL);
        assertEquals("Free", listing.getPriceDisplay());
        listing.setPrice(10.0);
        assertEquals("HK$ 10.00", listing.getPriceDisplay());
        listing.setType(ListingType.LEND);
        assertEquals("Borrow", listing.getPriceDisplay());
        assertEquals("For Lending", listing.getTypeDisplay());
        listing.setType(ListingType.GIVEAWAY);
        assertEquals("Giveaway", listing.getTypeDisplay());
        listing.setType(ListingType.SELL);
        assertEquals("For Sale", listing.getTypeDisplay());
    }

    @Test
    void dataStoreDefaultAndSnapshotBranches() throws Exception {
        String oldFile = System.getProperty("marketplace.data.file");
        String oldReset = System.getProperty("marketplace.data.reset");
        try {
            Path override = tempDir.resolve("override.json");
            System.setProperty("marketplace.data.file", override.toString());
            System.setProperty("marketplace.data.reset", "true");
            MarketplaceDataStore store = MarketplaceDataStore.defaultStore();
            assertEquals(override, store.getFile());

            MarketplaceDataStore.Snapshot snapshot = new MarketplaceDataStore.Snapshot();
            assertEquals(MarketplaceDataStore.FORMAT_VERSION, snapshot.version);
            Files.writeString(override, "{}");
            assertNotNull(store.load());
            Files.writeString(override, "null");
            assertNull(store.load());
            assertFalse(Files.exists(override));
            try (var files = Files.list(tempDir)) {
                assertTrue(files.anyMatch(path -> path.getFileName().toString().contains(".corrupt-")));
            }

            Path nested = tempDir.resolve("nested").resolve("data.json");
            MarketplaceDataStore nestedStore = new MarketplaceDataStore(nested);
            nestedStore.save(snapshot);
            assertTrue(Files.exists(nested));
            assertNotNull(nestedStore.load());
        } finally {
            restoreProperty("marketplace.data.file", oldFile);
            restoreProperty("marketplace.data.reset", oldReset);
        }
    }

    private static void assertNotOk(String value) {
        assertFalse("OK".equals(value));
    }

    private static Listing listing(ListingType type, Double price) {
        Listing listing = new Listing();
        listing.setType(type);
        listing.setPrice(price);
        return listing;
    }

    private static void restoreProperty(String key, String value) {
        if (value == null) {
            System.clearProperty(key);
        } else {
            System.setProperty(key, value);
        }
    }
}
