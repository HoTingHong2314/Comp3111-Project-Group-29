package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;
import com.hkust.marketplace.models.UserRole;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceCoverageTest {

    @TempDir
    Path tempDir;

    private DummyDataService newService() {
        return new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("coverage-" + System.nanoTime() + ".json")));
    }

    private Listing addListing(DummyDataService data, String title, ListingType type,
                               Double price, Integer days) {
        return addListing(data, title, type, price, days, "charlie");
    }

    private Listing addListing(DummyDataService data, String title, ListingType type,
                               Double price, Integer days, String seller) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Coverage test");
        listing.setCategory("Textbooks");
        listing.setType(type);
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setPrice(price);
        listing.setLendingDays(days);
        listing.setSellerUsername(seller);
        listing.setSellerFullName(seller.equals("diana") ? "Diana Ng" : "Charlie Lee");
        listing.setStatus(ListingStatus.LIVE);
        data.addListing(listing);
        return listing;
    }

    private Listing copyForUpdate(Listing source, String title) {
        Listing copy = new Listing();
        copy.setId(source.getId());
        copy.setTitle(title);
        copy.setDescription(source.getDescription());
        copy.setCategory(source.getCategory());
        copy.setType(source.getType());
        copy.setCondition(source.getCondition());
        copy.setPrice(source.getPrice());
        copy.setLendingDays(source.getLendingDays());
        copy.setMeetupLocation(source.getMeetupLocation());
        copy.setSellerUsername(source.getSellerUsername());
        copy.setSellerFullName(source.getSellerFullName());
        copy.setStatus(source.getStatus());
        return copy;
    }

    @Test
    void registrationLoginAndProfileBranches() {
        DummyDataService data = newService();

        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer(null, "A", "Password1", "Password1", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("ab", "A", "Password1", "Password1", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("bad-name", "A", "Password1", "Password1", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("alice", "A", "Password1", "Password1", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("newbuyer", " ", "Password1", "Password1", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("newbuyer", "New Buyer", "weak", "weak", "Hall"));
        assertThrows(IllegalArgumentException.class, () -> data.registerBuyer("newbuyer", "New Buyer", "Password1", "Password2", "Hall"));
        User buyer = data.registerBuyer("newbuyer", "New Buyer", "Password1", "Password1", "Hall 1");
        assertEquals(UserRole.BUYER, buyer.getRole());

        assertThrows(IllegalArgumentException.class, () -> data.registerSeller("ab", "S", "Password1", "Password1", "Library", "Bio"));
        assertThrows(IllegalArgumentException.class, () -> data.registerSeller("charlie", "S", "Password1", "Password1", "Library", "Bio"));
        assertThrows(IllegalArgumentException.class, () -> data.registerSeller("newseller", " ", "Password1", "Password1", "Library", "Bio"));
        assertThrows(IllegalArgumentException.class, () -> data.registerSeller("newseller", "New Seller", "weak", "weak", "Library", "Bio"));
        assertThrows(IllegalArgumentException.class, () -> data.registerSeller("newseller", "New Seller", "Password1", "Password2", "Library", "Bio"));
        User seller = data.registerSeller("newseller", "New Seller", "Password1", "Password1", "Library", "Bio");
        assertEquals(UserRole.SELLER, seller.getRole());
        assertNull(data.registerSeller("second_seller", "Second Seller", "Password1", "Password1", "Library", " ").getBio());

        assertThrows(IllegalArgumentException.class, () -> data.login("nobody", "Password1", UserRole.BUYER));
        assertThrows(IllegalArgumentException.class, () -> data.login("alice", "Password1", UserRole.SELLER));
        assertThrows(IllegalArgumentException.class, () -> data.login("alice", "Wrong1", UserRole.BUYER));
        assertNotNull(data.login("alice", "password1", UserRole.BUYER));

        data.updateBuyerProfile(buyer, "Updated Buyer", "Password2", "Password2", "Hall 9");
        assertEquals("Updated Buyer", buyer.getFullName());
        assertEquals("Hall 9", buyer.getResidenceHall());
        assertNotNull(data.login("newbuyer", "Password2", UserRole.BUYER));
        assertThrows(IllegalArgumentException.class, () -> data.updateBuyerProfile(buyer, " ", null, null, "Hall"));
        assertThrows(SecurityException.class, () -> data.updateBuyerProfile(seller, "Nope", null, null, "Hall"));

        data.updateSellerProfile(seller, "Updated Seller", "Password2", "Password2", " ", "Library");
        assertEquals("Updated Seller", seller.getFullName());
        assertNull(seller.getBio());
        assertThrows(IllegalArgumentException.class, () -> data.updateSellerProfile(seller, " ", null, null, "Bio", "Library"));
        assertThrows(SecurityException.class, () -> data.updateSellerProfile(buyer, "Nope", null, null, "Bio", "Library"));

        User admin = data.findUser("admin");
        data.updateAdminProfile(admin, "Updated Admin", "Admin1234", "Admin1234");
        assertEquals("Updated Admin", admin.getFullName());
        assertThrows(IllegalArgumentException.class, () -> data.updateAdminProfile(admin, " ", null, null));
        assertThrows(SecurityException.class, () -> data.updateAdminProfile(seller, "Nope", null, null));
    }

    @Test
    void listingValidationSearchAndSellerListingBranches() {
        DummyDataService data = newService();
        User seller = data.findUser("charlie");
        User buyer = data.findUser("alice");

        Listing invalid = new Listing();
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setTitle(" ");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setTitle("Invalid");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setType(ListingType.SELL);
        invalid.setCategory("Bad");
        invalid.setCondition("Used");
        invalid.setMeetupLocation("Library");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setCategory("Textbooks");
        invalid.setCondition("Bad");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setCondition("Used");
        invalid.setMeetupLocation(" ");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setMeetupLocation("Library");
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));
        invalid.setPrice(0.0);
        assertThrows(IllegalArgumentException.class, () -> data.createSellerListing(seller, invalid));

        Listing lend = addListing(data, "Lend Validation " + System.nanoTime(), ListingType.LEND, 99.0, 4);
        Listing lendInput = copyForUpdate(lend, "Lend Validation " + System.nanoTime());
        lendInput.setId(null);
        Listing createdLend = data.createSellerListing(seller, lendInput);
        assertNull(createdLend.getPrice());
        assertEquals(4, createdLend.getLendingDays());

        Listing giveaway = addListing(data, "Giveaway Validation " + System.nanoTime(), ListingType.GIVEAWAY, 99.0, 4);
        Listing giveawayInput = copyForUpdate(giveaway, "Giveaway Validation " + System.nanoTime());
        giveawayInput.setId(null);
        Listing createdGiveaway = data.createSellerListing(seller, giveawayInput);
        assertNull(createdGiveaway.getPrice());
        assertNull(createdGiveaway.getLendingDays());
        assertThrows(SecurityException.class, () -> data.createSellerListing(buyer, giveawayInput));

        assertTrue(data.searchListings(null, null, null, null, null, null, null).size() > 0);
        assertTrue(data.searchListings("", "", null, null, null, "", null).size() > 0);
        assertTrue(data.searchListings("macbook", "Electronics", ListingType.SELL,
                null, null, "Any", ListingSort.NEWEST).size() > 0);
        assertTrue(data.searchListings("no-such-" + System.nanoTime(), null, null,
                null, null, null, null).isEmpty());
        assertTrue(data.searchListings(null, "All Categories", null, 0.0, null, "Any",
                ListingSort.PRICE_LOW_HIGH).size() > 0);
        assertTrue(data.searchListings(null, null, ListingType.LEND, null, null, "Any",
                ListingSort.PRICE_HIGH_LOW).size() > 0);
        assertTrue(data.searchListings(null, null, null, null, null, "Used", ListingSort.NEWEST).size() > 0);
        assertTrue(data.searchListings(null, "Textbooks", null, 0.0, 1000.0, "Used", ListingSort.NEWEST).size() > 0);

        assertEquals(5.0, data.getSellerAverageRating("charlie"));
        assertEquals(1, data.getSellerRatingCount("charlie"));
        assertEquals(4.3, data.getSellerAverageRating("diana"));
        assertEquals(8, data.getSellerRatingCount("diana"));
        assertEquals(0.0, data.getSellerAverageRating("missing"));
        assertEquals(0, data.getSellerRatingCount("missing"));

        Listing updatable = addListing(data, "Editable " + System.nanoTime(), ListingType.SELL, 10.0, null);
        assertTrue(data.isListingEditable(updatable.getId()));
        assertFalse(data.isListingEditable(null));
        updatable.setStatus(ListingStatus.REMOVED);
        assertFalse(data.isListingEditable(updatable.getId()));
        updatable.setStatus(ListingStatus.RESERVED);
        assertFalse(data.isListingEditable(updatable.getId()));
        updatable.setStatus(ListingStatus.SOLD);
        assertFalse(data.isListingEditable(updatable.getId()));
        updatable.setStatus(ListingStatus.LENT);
        assertFalse(data.isListingEditable(updatable.getId()));
        updatable.setStatus(ListingStatus.GIVEN);
        assertFalse(data.isListingEditable(updatable.getId()));

        Listing missingOwned = copyForUpdate(updatable, "Missing");
        missingOwned.setId("missing");
        assertThrows(IllegalArgumentException.class, () -> data.updateSellerListing(seller, missingOwned));
        Listing owned = addListing(data, "Owned " + System.nanoTime(), ListingType.SELL, 10.0, null);
        Listing other = copyForUpdate(owned, "Other");
        assertThrows(SecurityException.class, () -> data.updateSellerListing(buyer, other));
        other.setId("missing");
        assertThrows(IllegalArgumentException.class, () -> data.updateSellerListing(seller, other));

        String editedTitle = "Edited " + System.nanoTime();
        Listing edited = copyForUpdate(owned, editedTitle);
        data.updateSellerListing(seller, edited);
        assertEquals(editedTitle, owned.getTitle());
        assertEquals(ListingStatus.PENDING_REVIEW, owned.getStatus());

        assertThrows(IllegalArgumentException.class, () -> data.deleteSellerListing(seller, "missing"));
        assertThrows(SecurityException.class, () -> data.deleteSellerListing(buyer, owned.getId()));
        data.deleteSellerListing(seller, owned.getId());
        assertNull(data.findListing(owned.getId()));
    }

    @Test
    void moderationAndAdminBranches() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        User seller = data.findUser("charlie");
        User buyer = data.findUser("alice");

        assertThrows(IllegalArgumentException.class, () -> data.approveListing(admin, "missing"));
        assertThrows(IllegalArgumentException.class, () -> data.rejectListing(admin, "missing", "reason"));
        assertThrows(IllegalArgumentException.class, () -> data.rejectListing(admin, "missing", " "));
        assertThrows(IllegalArgumentException.class, () -> data.takeDownListing(admin, "missing", "reason"));
        assertThrows(IllegalArgumentException.class, () -> data.takeDownListing(admin, "missing", " "));

        Listing pending = addListing(data, "Approve " + System.nanoTime(), ListingType.SELL, 10.0, null);
        pending.setStatus(ListingStatus.PENDING_REVIEW);
        data.approveListing(admin, pending.getId());
        assertEquals(ListingStatus.LIVE, pending.getStatus());
        assertThrows(SecurityException.class, () -> data.approveListing(buyer, pending.getId()));

        Listing rejected = addListing(data, "Reject " + System.nanoTime(), ListingType.SELL, 10.0, null);
        rejected.setStatus(ListingStatus.PENDING_REVIEW);
        data.rejectListing(admin, rejected.getId(), "No photos");
        assertEquals(ListingStatus.REJECTED, rejected.getStatus());
        assertEquals("No photos", rejected.getRejectionReason());

        Listing removed = addListing(data, "Remove " + System.nanoTime(), ListingType.SELL, 10.0, null);
        data.takeDownListing(admin, removed.getId(), "Gone");
        assertEquals(ListingStatus.REMOVED, removed.getStatus());
        assertThrows(IllegalStateException.class, () -> data.takeDownListing(admin, removed.getId(), "Again"));

        Listing adminSell = new Listing();
        adminSell.setTitle("Admin Sell " + System.nanoTime());
        adminSell.setDescription("Admin");
        adminSell.setCategory("Textbooks");
        adminSell.setType(ListingType.SELL);
        adminSell.setCondition("Used");
        adminSell.setMeetupLocation("Library");
        adminSell.setPrice(30.0);
        adminSell.setLendingDays(2);
        adminSell.setSellerUsername("charlie");
        Listing adminCreated = data.addListingAsAdmin(admin, adminSell);
        assertNull(adminCreated.getLendingDays());
        assertEquals(ListingStatus.LIVE, adminCreated.getStatus());

        Listing adminLend = copyForUpdate(adminSell, "Admin Lend " + System.nanoTime());
        adminLend.setId(null);
        adminLend.setType(ListingType.LEND);
        adminLend.setPrice(30.0);
        adminLend.setLendingDays(2);
        assertEquals(2, data.addListingAsAdmin(admin, adminLend).getLendingDays());

        Listing adminGiveaway = copyForUpdate(adminSell, "Admin Giveaway " + System.nanoTime());
        adminGiveaway.setId(null);
        adminGiveaway.setType(ListingType.GIVEAWAY);
        assertNull(data.addListingAsAdmin(admin, adminGiveaway).getPrice());

        Listing invalidAdmin = copyForUpdate(adminSell, " ");
        assertThrows(IllegalArgumentException.class, () -> data.addListingAsAdmin(admin, invalidAdmin));
        Listing missingSeller = copyForUpdate(adminSell, "Missing Seller " + System.nanoTime());
        missingSeller.setSellerUsername("nobody");
        assertThrows(IllegalArgumentException.class, () -> data.addListingAsAdmin(admin, missingSeller));
        Listing inactiveSeller = copyForUpdate(adminSell, "Inactive Seller " + System.nanoTime());
        inactiveSeller.setSellerUsername("diana");
        data.setUserActiveAsAdmin(admin, "diana", false);
        assertThrows(IllegalArgumentException.class, () -> data.addListingAsAdmin(admin, inactiveSeller));
        assertThrows(SecurityException.class, () -> data.addListingAsAdmin(buyer, adminSell));

        Listing missingForUpdate = copyForUpdate(adminSell, "missing");
        missingForUpdate.setId("missing");
        assertThrows(IllegalArgumentException.class, () -> data.updateListingAsAdmin(admin, missingForUpdate));
        Listing removedForUpdate = copyForUpdate(adminSell, "removed");
        removedForUpdate.setId(removed.getId());
        assertThrows(IllegalStateException.class, () -> data.updateListingAsAdmin(admin, removedForUpdate));
        Listing adminUpdated = copyForUpdate(adminCreated, "Admin Updated " + System.nanoTime());
        data.updateListingAsAdmin(admin, adminUpdated);
        assertEquals(adminUpdated.getTitle(), adminCreated.getTitle());
        assertThrows(SecurityException.class, () -> data.updateListingAsAdmin(buyer, adminUpdated));

        assertThrows(IllegalArgumentException.class, () -> data.updateUserAsAdmin(admin, "missing", "Name", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> data.updateUserAsAdmin(admin, "admin", "Name", null, null, null));
        assertThrows(IllegalArgumentException.class, () -> data.updateUserAsAdmin(admin, "charlie", " ", null, null, null));
        data.updateUserAsAdmin(admin, "alice", "Alice Admin Edited", "Hall 4", "Bio", "Library");
        assertEquals("Alice Admin Edited", buyer.getFullName());
        data.updateUserAsAdmin(admin, "charlie", "Charlie Admin Edited", null, "Bio", "Library");
        assertEquals("Charlie Admin Edited", seller.getFullName());
        assertThrows(SecurityException.class, () -> data.updateUserAsAdmin(seller, "alice", "No", null, null, null));

        assertThrows(IllegalArgumentException.class, () -> data.setUserActiveAsAdmin(admin, "missing", false));
        assertThrows(IllegalArgumentException.class, () -> data.setUserActiveAsAdmin(admin, "admin", false));
        data.setUserActiveAsAdmin(admin, "alice", true);
        data.setUserActiveAsAdmin(admin, "alice", false);
        assertFalse(buyer.isActive());
        assertThrows(SecurityException.class, () -> data.setUserActiveAsAdmin(buyer, "alice", true));

        assertThrows(IllegalArgumentException.class, () -> data.deleteUserAsAdmin(admin, "missing"));
        assertThrows(IllegalArgumentException.class, () -> data.deleteUserAsAdmin(admin, "admin"));
        assertThrows(SecurityException.class, () -> data.deleteUserAsAdmin(buyer, "alice"));
    }

    @Test
    void dealValidationBranches() {
        DummyDataService data = newService();
        User buyer = data.findUser("alice");
        User seller = data.findUser("charlie");
        Listing sell = addListing(data, "Deal Validation Sell " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Listing lend = addListing(data, "Deal Validation Lend " + System.nanoTime(), ListingType.LEND, null, 3);
        Listing giveaway = addListing(data, "Deal Validation Giveaway " + System.nanoTime(), ListingType.GIVEAWAY, null, null);

        assertThrows(SecurityException.class, () -> data.submitOffer(seller, sell.getId(), 50.0));
        assertThrows(IllegalArgumentException.class, () -> data.submitOffer(buyer, "missing", 50.0));
        assertThrows(IllegalArgumentException.class, () -> data.submitOffer(buyer, lend.getId(), 50.0));
        assertThrows(IllegalArgumentException.class, () -> data.submitOffer(buyer, sell.getId(), 0.0));
        assertThrows(IllegalArgumentException.class, () -> data.submitOffer(buyer, sell.getId(), 12.345));
        Deal offer = data.submitOffer(buyer, sell.getId(), 80.0);
        assertThrows(IllegalStateException.class, () -> data.submitOffer(buyer, sell.getId(), 70.0));

        assertThrows(SecurityException.class, () -> data.submitBorrowRequest(seller, lend.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(buyer, "missing",
                LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(buyer, sell.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(buyer, lend.getId(),
                null, LocalDateTime.now().plusHours(1)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(buyer, lend.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusSeconds(1)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(buyer, lend.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusDays(4)));
        Deal borrow = data.submitBorrowRequest(buyer, lend.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusDays(2));
        assertNotNull(borrow);

        assertThrows(SecurityException.class, () -> data.claimGiveaway(seller, giveaway.getId()));
        assertThrows(IllegalArgumentException.class, () -> data.claimGiveaway(buyer, "missing"));
        assertThrows(IllegalArgumentException.class, () -> data.claimGiveaway(buyer, sell.getId()));
        Deal claim = data.claimGiveaway(buyer, giveaway.getId());
        assertNotNull(claim);
        assertThrows(IllegalStateException.class, () -> data.claimGiveaway(buyer, giveaway.getId()));

        assertThrows(SecurityException.class, () -> data.acceptCounterOffer(seller, offer.getId()));
        assertThrows(IllegalArgumentException.class, () -> data.acceptCounterOffer(buyer, "missing"));
        assertThrows(IllegalStateException.class, () -> data.acceptCounterOffer(buyer, offer.getId()));
        assertThrows(IllegalStateException.class, () -> data.declineCounterOffer(buyer, offer.getId()));
        assertThrows(IllegalArgumentException.class, () -> data.declineCounterOffer(buyer, "missing"));

        assertThrows(IllegalStateException.class, () -> data.selfReturn(buyer, offer.getId()));
        assertThrows(IllegalStateException.class, () -> data.completeDeal(buyer, offer.getId()));
        assertThrows(IllegalArgumentException.class, () -> data.completeDeal(buyer, "missing"));
    }

    @Test
    void offerLifecycleBranches() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        User charlie = data.findUser("charlie");
        User diana = data.findUser("diana");

        Listing sell = addListing(data, "Lifecycle Sell " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal sellDeal = data.submitOffer(alice, sell.getId(), 80.0);
        assertThrows(SecurityException.class, () -> data.counterDeal(diana, sellDeal.getId(), 90.0, null, null, null));
        assertThrows(IllegalArgumentException.class, () -> data.counterDeal(charlie, sellDeal.getId(), null, null, null, null));
        data.counterDeal(charlie, sellDeal.getId(), 85.0, null, null, "Counter");
        data.acceptCounterOffer(alice, sellDeal.getId());
        assertEquals("Accepted", sellDeal.getStatus());
        assertEquals(ListingStatus.RESERVED, sell.getStatus());

        Listing lend = addListing(data, "Lifecycle Lend " + System.nanoTime(), ListingType.LEND, null, 4);
        LocalDateTime now = LocalDateTime.now();
        Deal lendDeal = data.submitBorrowRequest(alice, lend.getId(), now, now.plusDays(2));
        data.counterDeal(charlie, lendDeal.getId(), null, now.plusDays(1), now.plusDays(3), "Counter dates");
        data.acceptCounterOffer(alice, lendDeal.getId());
        assertEquals("Accepted", lendDeal.getStatus());
        assertEquals(ListingStatus.LENT, lend.getStatus());

        Listing declineLend = addListing(data, "Decline Lend " + System.nanoTime(), ListingType.LEND, null, 4);
        Deal declineLendDeal = data.submitBorrowRequest(alice, declineLend.getId(), now, now.plusDays(2));
        data.declineDeal(charlie, declineLendDeal.getId(), null);
        assertEquals("Declined", declineLendDeal.getStatus());

        assertThrows(SecurityException.class, () -> data.acceptDeal(diana, sellDeal.getId()));
        assertThrows(IllegalStateException.class, () -> data.acceptDeal(charlie, sellDeal.getId()));
        assertThrows(SecurityException.class, () -> data.declineDeal(diana, sellDeal.getId(), null));
        assertThrows(IllegalStateException.class, () -> data.declineDeal(charlie, sellDeal.getId(), null));

        Listing giveaway = addListing(data, "Lifecycle Giveaway " + System.nanoTime(), ListingType.GIVEAWAY, null, null);
        Deal claim = data.claimGiveaway(alice, giveaway.getId());
        assertThrows(IllegalStateException.class, () -> data.counterDeal(charlie, claim.getId(), 1.0, null, null, null));
        data.declineDeal(charlie, claim.getId(), "Declined claim");
        assertEquals(ListingStatus.LIVE, giveaway.getStatus());

        Listing cancelSell = addListing(data, "Cancel Sell " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal cancelSellDeal = data.submitOffer(bob, cancelSell.getId(), 90.0);
        data.acceptDeal(charlie, cancelSellDeal.getId());
        data.cancelAcceptedDeal(charlie, cancelSellDeal.getId(), null);
        assertEquals("Cancelled", cancelSellDeal.getStatus());
        assertEquals(ListingStatus.LIVE, cancelSell.getStatus());
        assertThrows(IllegalStateException.class, () -> data.cancelAcceptedDeal(charlie, cancelSellDeal.getId(), null));

        Listing cancelGiveaway = addListing(data, "Cancel Giveaway " + System.nanoTime(), ListingType.GIVEAWAY, null, null);
        Deal cancelGiveawayDeal = data.claimGiveaway(bob, cancelGiveaway.getId());
        data.acceptDeal(charlie, cancelGiveawayDeal.getId());
        data.cancelAcceptedDeal(charlie, cancelGiveawayDeal.getId(), "No show");
        assertEquals(ListingStatus.LIVE, cancelGiveaway.getStatus());

        Listing lendCancel = addListing(data, "Cancel Lend " + System.nanoTime(), ListingType.LEND, null, 2);
        Deal lendCancelDeal = data.submitBorrowRequest(bob, lendCancel.getId(), now, now.plusDays(1));
        data.acceptDeal(charlie, lendCancelDeal.getId());
        assertThrows(IllegalStateException.class, () -> data.cancelAcceptedDeal(charlie, lendCancelDeal.getId(), null));
        assertThrows(SecurityException.class, () -> data.cancelAcceptedDeal(diana, lendCancelDeal.getId(), null));

        Listing completeSell = addListing(data, "Complete Sell " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal completeSellDeal = data.submitOffer(alice, completeSell.getId(), 90.0);
        data.acceptDeal(charlie, completeSellDeal.getId());
        data.completeDeal(alice, completeSellDeal.getId());
        assertEquals("Completed", completeSellDeal.getStatus());
        assertEquals(ListingStatus.SOLD, completeSell.getStatus());

        Listing completeGiveaway = addListing(data, "Complete Giveaway " + System.nanoTime(), ListingType.GIVEAWAY, null, null);
        Deal completeGiveawayDeal = data.claimGiveaway(alice, completeGiveaway.getId());
        data.acceptDeal(charlie, completeGiveawayDeal.getId());
        data.completeDeal(alice, completeGiveawayDeal.getId());
        assertEquals(ListingStatus.GIVEN, completeGiveaway.getStatus());

        Listing returnLend = addListing(data, "Return Lend " + System.nanoTime(), ListingType.LEND, null, 2);
        Deal returnDeal = data.submitBorrowRequest(alice, returnLend.getId(), now, now.plusDays(1));
        data.acceptDeal(charlie, returnDeal.getId());
        data.selfReturn(alice, returnDeal.getId());
        assertEquals("Returned", returnDeal.getStatus());
        assertEquals(ListingStatus.LIVE, returnLend.getStatus());
        assertThrows(IllegalStateException.class, () -> data.selfReturn(alice, returnDeal.getId()));

        Listing missingListing = addListing(data, "Missing Listing " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal missingDeal = data.submitOffer(alice, missingListing.getId(), 90.0);
        data.removeListing(missingListing.getId());
        assertThrows(IllegalStateException.class, () -> data.acceptDeal(charlie, missingDeal.getId()));

        Listing defaultDates = addListing(data, "Default Dates " + System.nanoTime(), ListingType.LEND, null, 2);
        Deal defaultDatesDeal = data.submitBorrowRequest(alice, defaultDates.getId(), now, now.plusDays(1));
        DummyDataService.DealExtras extras = data.getDealExtras(defaultDatesDeal.getId());
        extras.borrowStartAt = null;
        extras.borrowEndAt = null;
        data.acceptDeal(charlie, defaultDatesDeal.getId());
        assertNotNull(data.getDealExtras(defaultDatesDeal.getId()).borrowStartAt);

        Listing activeListing = addListing(data, "Notify Active " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal activePending = data.submitOffer(alice, activeListing.getId(), 80.0);
        Deal activeCountered = data.submitOffer(bob, activeListing.getId(), 70.0);
        data.counterDeal(charlie, activeCountered.getId(), 75.0, null, null, null);
        data.notifyBuyersWithActiveOffers(activeListing.getId(), null);
        assertEquals("Cancelled", activePending.getStatus());
        assertEquals("Cancelled", activeCountered.getStatus());
        data.notifyBuyersWithActiveOffers(activeListing.getId(), "No active");
    }

    @Test
    void chatNotificationAndReviewBranches() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Chat Coverage " + System.nanoTime(), ListingType.SELL, 100.0, null);

        assertThrows(IllegalArgumentException.class, () -> data.getOrCreateChatThread("missing", "alice"));
        var thread = data.getOrCreateChatThread(listing.getId(), "alice");
        assertSame(thread, data.getOrCreateChatThread(listing.getId(), "alice"));
        assertTrue(data.getChatMessages("missing").isEmpty());
        assertTrue(data.searchMessages(thread.id(), null).isEmpty());
        assertTrue(data.searchMessages(thread.id(), " ").isEmpty());
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(alice, "missing", "Hi"));
        assertThrows(SecurityException.class, () -> data.sendMessage(bob, thread.id(), "Hi"));
        assertThrows(SecurityException.class, () -> data.sendMessage(null, thread.id(), "Hi"));
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(alice, thread.id(), " "));
        assertThrows(IllegalArgumentException.class, () -> data.sendMessage(alice, thread.id(), "x".repeat(2001)));
        data.sendMessage(alice, thread.id(), "Hello");
        data.sendMessage(charlie, thread.id(), "Hi Alice");
        assertEquals(1, data.searchMessages(thread.id(), "hello").size());
        assertEquals(2, data.getChatThreadsForSeller("charlie").size());

        Deal orphanDeal = new Deal();
        orphanDeal.setListingId("missing");
        orphanDeal.setListingTitle("Missing");
        orphanDeal.setBuyerUsername("alice");
        orphanDeal.setSellerUsername("charlie");
        var orphanThread = data.getChatThreadForDeal(orphanDeal);
        assertNotNull(orphanThread);

        assertThrows(IllegalArgumentException.class, () -> data.sendFormalOffer(alice, "missing", 10.0, null, null));
        assertThrows(SecurityException.class, () -> data.sendFormalOffer(bob, thread.id(), 10.0, null, null));
        assertThrows(SecurityException.class, () -> data.sendFormalOffer(charlie, thread.id(), 10.0, null, null));
        assertThrows(IllegalArgumentException.class, () -> data.sendFormalOffer(alice, thread.id(), null, null, null));
        var formal = data.sendFormalOffer(alice, thread.id(), 90.0, null, null);
        assertEquals("Formal Offer", formal.type());

        Listing lend = addListing(data, "Chat Lend " + System.nanoTime(), ListingType.LEND, null, 2);
        var lendThread = data.getOrCreateChatThread(lend.getId(), "alice");
        LocalDateTime now = LocalDateTime.now();
        assertEquals("Formal Offer", data.sendFormalOffer(alice, lendThread.id(), null,
                now, now.plusDays(1)).type());

        Listing giveaway = addListing(data, "Chat Giveaway " + System.nanoTime(), ListingType.GIVEAWAY, null, null);
        var giveawayThread = data.getOrCreateChatThread(giveaway.getId(), "alice");
        assertThrows(IllegalArgumentException.class,
                () -> data.sendFormalOffer(alice, giveawayThread.id(), null, null, null));

        int unreadBefore = data.getUnreadNotificationCount("alice");
        data.markNotificationRead("missing");
        data.markAllNotificationsRead("alice");
        assertEquals(0, data.getUnreadNotificationCount("alice"));
        data.markAllNotificationsRead("alice");
        assertTrue(unreadBefore >= 0);

        Listing reviewSell = addListing(data, "Review Sell " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal reviewDeal = data.submitOffer(alice, reviewSell.getId(), 90.0);
        data.acceptDeal(charlie, reviewDeal.getId());
        data.completeDeal(alice, reviewDeal.getId());
        assertTrue(data.canReviewDeal(alice, reviewDeal.getId()));
        assertFalse(data.canReviewDeal(bob, reviewDeal.getId()));
        assertFalse(data.canReviewDeal(alice, "missing"));
        assertThrows(IllegalArgumentException.class, () -> data.addReview(alice, reviewDeal.getId(), 0, "Bad"));
        assertThrows(IllegalArgumentException.class, () -> data.addReview(alice, reviewDeal.getId(), 6, "Bad"));
        assertThrows(IllegalArgumentException.class, () -> data.addReview(alice, reviewDeal.getId(), 5, " "));
        Review review = data.addReview(alice, reviewDeal.getId(), 5, "Excellent");
        assertFalse(review.isHidden());
        assertTrue(data.getReviewsByBuyer("alice").stream().anyMatch(r -> r.getId().equals(review.getId())));
        assertThrows(IllegalStateException.class, () -> data.addReview(alice, reviewDeal.getId(), 5, "Again"));

        data.addSellerReply(charlie, review.getId(), "Thanks");
        assertEquals("Thanks", review.getSellerReply());
        assertThrows(IllegalArgumentException.class, () -> data.addSellerReply(charlie, "missing", "No"));
        assertThrows(SecurityException.class, () -> data.addSellerReply(data.findUser("diana"), review.getId(), "No"));
        assertThrows(IllegalArgumentException.class, () -> data.addSellerReply(charlie, review.getId(), " "));
        assertThrows(SecurityException.class, () -> data.flagReview(data.findUser("diana"), review.getId(), "No"));
        assertThrows(IllegalArgumentException.class, () -> data.flagReview(charlie, "missing", "No"));
        data.flagReview(charlie, review.getId(), "Inaccurate");
        data.flagReview(charlie, review.getId(), null);

        data.setReviewHidden(admin, review.getId(), true);
        assertTrue(review.isHidden());
        assertTrue(data.getReviewsForSeller("charlie").stream().noneMatch(r -> r.getId().equals(review.getId())));
        data.setReviewHidden(admin, review.getId(), false);
        assertFalse(review.isHidden());
        assertThrows(SecurityException.class, () -> data.setReviewHidden(charlie, review.getId(), true));
        assertThrows(IllegalArgumentException.class, () -> data.setReviewHidden(admin, "missing", true));

        Review manual = new Review();
        manual.setSellerUsername("charlie");
        manual.setBuyerUsername("bob");
        manual.setListingTitle("Manual");
        manual.setRating(3);
        data.addReview(manual);
        assertNotNull(manual.getId());
        data.addReview(manual);
        assertTrue(data.getAllReviews().contains(manual));
        assertEquals(0.0, data.getSellerAverageRating("missing-seller"));
    }

    @Test
    void expirationAndAdminDealBranches() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        LocalDateTime now = LocalDateTime.now();
        data.processDueReminders();

        Listing dueListing = addListing(data, "Due Reminder " + System.nanoTime(), ListingType.LEND, null, 3);
        Deal dueDeal = data.submitBorrowRequest(alice, dueListing.getId(), now, now.plusDays(2));
        data.acceptDeal(charlie, dueDeal.getId());
        assertEquals(0, data.processDueReminders());
        data.getDealExtras(dueDeal.getId()).borrowEndAt = now.plusHours(2);
        assertEquals(1, data.processDueReminders());

        Listing pastListing = addListing(data, "Past Reminder " + System.nanoTime(), ListingType.LEND, null, 2);
        Deal pastDeal = data.submitBorrowRequest(bob, pastListing.getId(), now, now.plusDays(1));
        data.acceptDeal(charlie, pastDeal.getId());
        data.getDealExtras(pastDeal.getId()).borrowEndAt = now.minusHours(1);
        assertEquals(1, data.processExpiredBorrows());
        assertEquals("Returned", pastDeal.getStatus());

        Listing nullDueListing = addListing(data, "Null Due " + System.nanoTime(), ListingType.LEND, null, 2);
        Deal nullDueDeal = data.submitBorrowRequest(bob, nullDueListing.getId(), now, now.plusDays(1));
        data.acceptDeal(charlie, nullDueDeal.getId());
        DummyDataService.DealExtras nullDueExtra = data.getDealExtras(nullDueDeal.getId());
        nullDueExtra.borrowEndAt = null;
        nullDueDeal.setBorrowEnd(null);
        assertEquals(0, data.processExpiredBorrows());

        Listing buyerPendingListing = addListing(data, "Buyer Pending " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal buyerPending = data.submitOffer(alice, buyerPendingListing.getId(), 80.0);
        data.setUserActiveAsAdmin(admin, "alice", false);
        assertEquals("Cancelled", buyerPending.getStatus());
        data.setUserActiveAsAdmin(admin, "alice", true);

        data.deleteUserAsAdmin(admin, "bob");
        assertNull(data.findUser("bob"));

        Listing sellerListing = addListing(data, "Seller Pending " + System.nanoTime(), ListingType.SELL, 100.0, null);
        Deal sellerPending = data.submitOffer(alice, sellerListing.getId(), 80.0);
        data.setUserActiveAsAdmin(admin, "charlie", false);
        assertEquals("Cancelled", sellerPending.getStatus());
    }
}
