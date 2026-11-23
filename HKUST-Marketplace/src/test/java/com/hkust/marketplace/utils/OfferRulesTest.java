package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;

class OfferRulesTest {

    @TempDir
    Path tempDir;

    private DummyDataService newService() {
        return new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("offers-" + System.nanoTime() + ".json")));
    }

    private Listing addListing(DummyDataService data, String title, ListingType type,
                               Double price, Integer days) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Offer rules test");
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
    void buyNowAcceptsAtAskingPriceAndDeclinesOtherOffers() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        Listing listing = addListing(data, "Buy Now " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        Deal aliceOffer = data.submitOffer(alice, listing.getId(), 80.0);
        Deal bobOffer = data.submitOffer(bob, listing.getId(), 90.0);

        Deal purchase = data.buyNow(alice, listing.getId());

        assertSame(aliceOffer, purchase);
        assertEquals(DealStatuses.ACCEPTED, purchase.getStatus());
        assertEquals(100.0, purchase.getOfferPrice());
        assertEquals(DealStatuses.DECLINED, bobOffer.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }

    @Test
    void buyNowCreatesAcceptedDealWithoutPriorOffer() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        Listing listing = addListing(data, "Buy Now Fresh " + System.nanoTime(),
                ListingType.SELL, 250.0, null);

        Deal purchase = data.buyNow(buyer, listing.getId());

        assertEquals(DealStatuses.ACCEPTED, purchase.getStatus());
        assertEquals(250.0, purchase.getOfferPrice());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertEquals(DealStatuses.ACCEPTED, data.findDeal(purchase.getId()).getStatus());
    }

    @Test
    void buyNowRejectsInvalidTargets() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");
        Listing lend = addListing(data, "Buy Now Lend " + System.nanoTime(),
                ListingType.LEND, null, 3);
        Listing sell = addListing(data, "Buy Now Duplicate " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        assertThrows(IllegalArgumentException.class, () -> data.buyNow(buyer, "missing"));
        assertThrows(IllegalArgumentException.class, () -> data.buyNow(buyer, lend.getId()));
        assertThrows(SecurityException.class, () -> data.buyNow(seller, sell.getId()));

        data.buyNow(buyer, sell.getId());
        User bob = data.findUser("bob");
        assertThrows(IllegalStateException.class, () -> data.buyNow(bob, sell.getId()));
    }

    @Test
    void offerMustBeBelowAskingPrice() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        Listing listing = addListing(data, "Offer Bounds " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        assertThrows(IllegalArgumentException.class,
                () -> data.submitOffer(buyer, listing.getId(), 100.0));
        assertThrows(IllegalArgumentException.class,
                () -> data.submitOffer(buyer, listing.getId(), 120.0));

        Deal valid = data.submitOffer(buyer, listing.getId(), 99.99);
        assertEquals(99.99, valid.getOfferPrice());
        assertEquals(DealStatuses.PENDING, valid.getStatus());
    }

    @Test
    void counterMustBeAboveBuyerOfferAndAtMostAskingPrice() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");
        Listing listing = addListing(data, "Counter Bounds " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(buyer, listing.getId(), 80.0);

        assertThrows(IllegalArgumentException.class,
                () -> data.counterDeal(seller, deal.getId(), 79.0, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> data.counterDeal(seller, deal.getId(), 80.0, null, null, null));
        assertThrows(IllegalArgumentException.class,
                () -> data.counterDeal(seller, deal.getId(), 101.0, null, null, null));

        data.counterDeal(seller, deal.getId(), 100.0, null, null, null);

        assertEquals(DealStatuses.COUNTERED, deal.getStatus());
        assertEquals(100.0, data.getDealExtras(deal.getId()).counterPrice);

        data.acceptCounterOffer(buyer, deal.getId());

        assertEquals(DealStatuses.ACCEPTED, deal.getStatus());
        assertEquals(100.0, deal.getOfferPrice());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }

    @Test
    void buyNowSupersedesBuyersOwnCounteredOffer() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");
        Listing listing = addListing(data, "Buy Now Counter " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(buyer, listing.getId(), 80.0);
        data.counterDeal(seller, deal.getId(), 90.0, null, null, null);

        Deal purchase = data.buyNow(buyer, listing.getId());

        assertSame(deal, purchase);
        assertEquals(DealStatuses.ACCEPTED, purchase.getStatus());
        assertEquals(100.0, purchase.getOfferPrice());
        assertNull(data.getDealExtras(deal.getId()).counterPrice);
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
    }
}
