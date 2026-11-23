package com.hkust.marketplace;

import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.utils.DummyDataService;
import com.hkust.marketplace.utils.MarketplaceDataStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class BuyerFlowTest {

    @TempDir
    Path tempDir;

    private DummyDataService data;

    @BeforeEach
    void setUp() {
        data = new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("buyer-flow-" + System.nanoTime() + ".json")));
    }

    private Listing addListing(String title) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setStatus(ListingStatus.LIVE);
        listing.setSellerUsername("charlie");
        data.addListing(listing);
        return listing;
    }

    @Test
    void openListingDetailIncrementsViewCount() {
        Listing listing = addListing("View Count " + System.nanoTime());
        assertEquals(0, listing.getViewCount());

        Listing firstOpen = data.openListingDetail(listing.getId());
        Listing secondOpen = data.openListingDetail(listing.getId());

        assertSame(listing, firstOpen);
        assertSame(listing, secondOpen);
        assertEquals(2, listing.getViewCount());
    }

    @Test
    void openListingDetailReturnsNullForUnknownId() {
        Listing known = addListing("Known Listing " + System.nanoTime());
        int viewCountBefore = known.getViewCount();

        Listing result = data.openListingDetail("NO_SUCH_" + System.nanoTime());

        assertNull(result);
        assertEquals(viewCountBefore, known.getViewCount());
    }

    @Test
    void openListingDetailDoesNotCountViewsForUnavailableListings() {
        Listing listing = addListing("Unavailable View " + System.nanoTime());
        listing.setStatus(ListingStatus.PENDING_REVIEW);

        data.openListingDetail(listing.getId());

        assertEquals(0, listing.getViewCount());
    }

    @Test
    void openListingDetailWorksWithSeededListings() {
        List<Listing> live = data.getLiveListings();
        assertFalse(live.isEmpty());
        assertFalse(data.getPendingListings().isEmpty());

        Listing target = live.get(0);
        assertSame(target, data.findListing(target.getId()));
        assertTrue(data.getAllListings().contains(target));

        int viewCountBefore = target.getViewCount();
        assertSame(target, data.openListingDetail(target.getId()));
        assertEquals(viewCountBefore + 1, target.getViewCount());
    }
}