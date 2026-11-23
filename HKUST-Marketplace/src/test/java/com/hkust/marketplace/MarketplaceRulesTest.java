package com.hkust.marketplace;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.ListingSort;
import com.hkust.marketplace.utils.MarketplaceDataStore;
import com.hkust.marketplace.utils.PasswordUtil;
import com.hkust.marketplace.utils.PriceUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class MarketplaceRulesTest {

    @TempDir
    Path tempDir;

    private DummyDataService data;

    @BeforeEach
    void setUp() {
        data = new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("marketplace-rules-" + System.nanoTime() + ".json")));
    }

    private Listing addListing(String title, ListingType type, Double price, Integer days) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Rule test");
        listing.setCategory("Textbooks");
        listing.setType(type);
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setPrice(price);
        listing.setLendingDays(days);
        listing.setSellerUsername("charlie");
        listing.setSellerFullName("Charlie Lee");
        listing.setStatus(ListingStatus.LIVE);
        data.addListing(listing);
        return listing;
    }

    @Test
    void priceFilterIncludesFreeAndLendingListings() {
        String keyword = "PriceFilter" + System.nanoTime();
        Listing low = addListing(keyword + " Low", ListingType.SELL, 30.0, null);
        addListing(keyword + " High", ListingType.SELL, 100.0, null);
        Listing free = addListing(keyword + " Free", ListingType.GIVEAWAY, null, null);
        Listing lend = addListing(keyword + " Lend", ListingType.LEND, null, 7);

        List<Listing> zeroToFifty = data.searchListings(keyword, "All Categories",
                null, 0.0, 50.0, "Any", ListingSort.NEWEST);
        assertTrue(zeroToFifty.stream().anyMatch(l -> l.getId().equals(low.getId())));
        assertTrue(zeroToFifty.stream().anyMatch(l -> l.getId().equals(free.getId())));
        assertTrue(zeroToFifty.stream().anyMatch(l -> l.getId().equals(lend.getId())));

        List<Listing> tenToFifty = data.searchListings(keyword, "All Categories",
                null, 10.0, 50.0, "Any", ListingSort.NEWEST);
        assertTrue(tenToFifty.stream().anyMatch(l -> l.getId().equals(low.getId())));
        assertFalse(tenToFifty.stream().anyMatch(l -> l.getId().equals(free.getId())));
        assertFalse(tenToFifty.stream().anyMatch(l -> l.getId().equals(lend.getId())));
    }

    @Test
    void priceSortTreatsFreeAndLendingAsZero() {
        String keyword = "PriceSort" + System.nanoTime();
        Listing sell50 = addListing(keyword + " Sell50", ListingType.SELL, 50.0, null);
        Listing sell150 = addListing(keyword + " Sell150", ListingType.SELL, 150.0, null);
        Listing free = addListing(keyword + " Free", ListingType.GIVEAWAY, null, null);
        Listing lend = addListing(keyword + " Lend", ListingType.LEND, null, 7);

        List<Listing> ascending = data.searchListings(keyword, "All Categories",
                null, null, null, "Any", ListingSort.PRICE_LOW_HIGH);
        List<Double> ascendingPrices = ascending.stream().map(PriceUtil::effectivePrice).toList();
        assertEquals(List.of(0.0, 0.0, 50.0, 150.0), ascendingPrices);
        assertTrue(ascending.stream().anyMatch(l -> l.getId().equals(free.getId())));
        assertTrue(ascending.stream().anyMatch(l -> l.getId().equals(lend.getId())));

        List<Listing> descending = data.searchListings(keyword, "All Categories",
                null, null, null, "Any", ListingSort.PRICE_HIGH_LOW);
        List<Double> descendingPrices = descending.stream().map(PriceUtil::effectivePrice).toList();
        assertEquals(List.of(150.0, 50.0, 0.0, 0.0), descendingPrices);
        assertTrue(descending.stream().anyMatch(l -> l.getId().equals(sell50.getId())));
        assertTrue(descending.stream().anyMatch(l -> l.getId().equals(sell150.getId())));
    }

    @Test
    void borrowRequestCannotExceedListingDuration() {
        Listing listing = addListing("BorrowLimit" + System.nanoTime(), ListingType.LEND, null, 1);
        LocalDateTime start = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class,
                () -> data.submitBorrowRequest(data.findUser("alice"), listing.getId(),
                        start, start.plusDays(2)));

        assertNotNull(data.submitBorrowRequest(data.findUser("alice"), listing.getId(),
                start, start.plusDays(1)));
    }

    @Test
    void removedListingCannotBeRemovedTwice() {
        User admin = data.findUser("admin");
        Listing listing = addListing("RemoveOnce" + System.nanoTime(), ListingType.GIVEAWAY, null, null);
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        data.approveListing(admin, listing.getId());
        data.takeDownListing(admin, listing.getId(), "Test removal");
        assertEquals(ListingStatus.REMOVED, listing.getStatus());

        assertThrows(IllegalStateException.class,
                () -> data.takeDownListing(admin, listing.getId(), "Remove again"));
    }

    @Test
    void priceAcceptsAtMostTwoDecimals() {
        assertEquals("OK", PriceUtil.validateInput("12"));
        assertEquals("OK", PriceUtil.validateInput("12.3"));
        assertEquals("OK", PriceUtil.validateInput("12.34"));
        assertNotEquals("OK", PriceUtil.validateInput("12.345"));
        assertNotEquals("OK", PriceUtil.validateInput("0"));
        assertNotEquals("OK", PriceUtil.validateInput("-1"));
        assertNotEquals("OK", PriceUtil.validateInput("abc"));

        assertEquals(12.34, PriceUtil.parse("12.34"));
        assertEquals("12.30", PriceUtil.format(12.3));
        assertFalse(PriceUtil.isValidAmount(12.345));
        assertTrue(PriceUtil.isValidAmount(12.34));

        Listing listing = addListing("Price Display " + System.nanoTime(),
                ListingType.SELL, 12.3, null);
        assertEquals("HK$ 12.30", listing.getPriceDisplay());
    }

    @Test
    void plaintextPasswordsAreRejected() {
        assertFalse(PasswordUtil.verify("password1", "password1"));
        assertTrue(PasswordUtil.verify("password1", PasswordUtil.hash("password1")));
    }
}
