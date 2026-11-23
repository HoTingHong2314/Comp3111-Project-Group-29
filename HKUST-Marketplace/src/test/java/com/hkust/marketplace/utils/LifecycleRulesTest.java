package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.ListingStatus;
import com.hkust.marketplace.models.ListingType;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LifecycleRulesTest {

    @TempDir
    Path tempDir;

    private DummyDataService newService() {
        return new DummyDataService(new MarketplaceDataStore(
                tempDir.resolve("marketplace-" + System.nanoTime() + ".json")));
    }

    private Listing addListing(DummyDataService data, String title, ListingType type,
                               Double price, Integer days) {
        Listing listing = new Listing();
        listing.setTitle(title);
        listing.setDescription("Lifecycle test");
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
    void notificationMigrationBackfillsMissingAndDuplicateIds() {
        MarketplaceDataStore store = new MarketplaceDataStore(tempDir.resolve("notifications.json"));
        DummyDataService first = new DummyDataService(store);

        MarketplaceDataStore.Snapshot damaged = store.load();
        String duplicateId = damaged.notifications.get(2).getId();
        damaged.notifications.get(0).setId(null);
        damaged.notifications.get(1).setId(duplicateId);
        store.save(damaged);

        DummyDataService second = new DummyDataService(store);
        MarketplaceDataStore.Snapshot repaired = store.load();

        Set<String> ids = new HashSet<>();
        for (Notification notification : repaired.notifications) {
            assertNotNull(notification.getId());
            assertTrue(ids.add(notification.getId()));
        }

        String idToRead = repaired.notifications.get(0).getId();
        second.markNotificationRead(idToRead);
        assertTrue(store.load().notifications.stream()
                .filter(n -> idToRead.equals(n.getId()))
                .allMatch(Notification::isRead));
    }

    @Test
    void duplicateOffersAreBlockedUntilClosed() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Duplicate Offer " + System.nanoTime(), ListingType.SELL, 100.0, null);

        Deal first = data.submitOffer(alice, listing.getId(), 80.0);
        assertThrows(IllegalStateException.class,
                () -> data.submitOffer(alice, listing.getId(), 70.0));

        data.declineDeal(charlie, first.getId(), "Too low");
        Deal second = data.submitOffer(alice, listing.getId(), 70.0);
        assertNotNull(second);
    }

    @Test
    void acceptingPendingDealAlsoDeclinesCounteredDeals() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Pending Versus Counter " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        Deal countered = data.submitOffer(alice, listing.getId(), 80.0);
        Deal pending = data.submitOffer(bob, listing.getId(), 90.0);
        data.counterDeal(charlie, countered.getId(), 85.0, null, null, null);

        data.acceptDeal(charlie, pending.getId());

        assertEquals("Accepted", pending.getStatus());
        assertEquals("Declined", countered.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertThrows(IllegalStateException.class,
                () -> data.acceptCounterOffer(alice, countered.getId()));
    }

    @Test
    void acceptingCounterOfferAlsoDeclinesOtherCounteredDeals() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User bob = data.findUser("bob");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Two Counters " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        Deal aliceDeal = data.submitOffer(alice, listing.getId(), 80.0);
        Deal bobDeal = data.submitOffer(bob, listing.getId(), 80.0);
        data.counterDeal(charlie, aliceDeal.getId(), 85.0, null, null, null);
        data.counterDeal(charlie, bobDeal.getId(), 86.0, null, null, null);

        data.acceptCounterOffer(alice, aliceDeal.getId());

        assertEquals("Accepted", aliceDeal.getStatus());
        assertEquals("Declined", bobDeal.getStatus());
        assertEquals(ListingStatus.RESERVED, listing.getStatus());
        assertThrows(IllegalStateException.class,
                () -> data.acceptCounterOffer(bob, bobDeal.getId()));
    }

    @Test
    void removedListingCannotAcceptCounterOffer() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Removed Counter " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        Deal deal = data.submitOffer(alice, listing.getId(), 80.0);
        data.counterDeal(charlie, deal.getId(), 85.0, null, null, null);
        data.takeDownListing(admin, listing.getId(), "Test removal");

        assertThrows(IllegalStateException.class,
                () -> data.acceptCounterOffer(alice, deal.getId()));
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
        assertEquals("Cancelled", deal.getStatus());
    }

    @Test
    void deactivatingSellerTakesDownListingsAndClosesDeals() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Deactivated Seller " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(alice, listing.getId(), 80.0);
        data.acceptDeal(charlie, deal.getId());

        data.setUserActiveAsAdmin(admin, "charlie", false);

        assertEquals(ListingStatus.REMOVED, listing.getStatus());
        assertEquals("Cancelled", deal.getStatus());
        assertThrows(IllegalStateException.class,
                () -> data.login("charlie", "password1", com.hkust.marketplace.models.UserRole.SELLER));

        data.setUserActiveAsAdmin(admin, "charlie", true);
        assertNotNull(data.login("charlie", "password1", com.hkust.marketplace.models.UserRole.SELLER));
    }

    @Test
    void deactivatingBuyerReleasesAcceptedSellListing() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Deactivated Buyer " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(alice, listing.getId(), 80.0);
        data.acceptDeal(charlie, deal.getId());

        data.setUserActiveAsAdmin(admin, "alice", false);

        assertEquals("Cancelled", deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertThrows(IllegalStateException.class,
                () -> data.login("alice", "password1", com.hkust.marketplace.models.UserRole.BUYER));

        data.setUserActiveAsAdmin(admin, "alice", true);
        assertNotNull(data.login("alice", "password1", com.hkust.marketplace.models.UserRole.BUYER));
    }

    @Test
    void deletingBuyerClosesAcceptedLendDealAndReleasesListing() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Deleted Buyer Lend " + System.nanoTime(),
                ListingType.LEND, null, 1);
        Deal deal = data.submitBorrowRequest(alice, listing.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusHours(2));
        data.acceptDeal(charlie, deal.getId());
        assertEquals(ListingStatus.LENT, listing.getStatus());

        data.deleteUserAsAdmin(admin, "alice");

        assertNull(data.findUser("alice"));
        assertEquals("Returned", deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void deletingSellerTakesDownListingsAndClosesAcceptedDeal() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Deleted Seller " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(alice, listing.getId(), 80.0);
        data.acceptDeal(charlie, deal.getId());

        data.deleteUserAsAdmin(admin, "charlie");

        assertNull(data.findUser("charlie"));
        assertEquals(ListingStatus.REMOVED, listing.getStatus());
        assertEquals("Cancelled", deal.getStatus());
    }

    @Test
    void sellerSeesChatStartedBeforeAnyDeal() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Seller Chat " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        var thread = data.getOrCreateChatThread(listing.getId(), "alice");
        data.sendMessage(alice, thread.id(), "Is this still available?");

        List<DummyDataService.ChatThreadInfo> sellerThreads =
                data.getChatThreadsForSeller("charlie");
        assertTrue(sellerThreads.stream().anyMatch(t -> t.id().equals(thread.id())));

        data.sendMessage(charlie, thread.id(), "Yes it is.");
        assertEquals(2, data.getChatMessages(thread.id()).size());
    }

    @Test
    void expiredBorrowIsProcessedOutsideBuyerDashboard() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Auto Return " + System.nanoTime(),
                ListingType.LEND, null, 1);
        Deal deal = data.submitBorrowRequest(alice, listing.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusHours(2));
        data.acceptDeal(charlie, deal.getId());
        data.getDealExtras(deal.getId()).borrowEndAt = LocalDateTime.now().minusSeconds(1);

        assertEquals(1, data.processExpiredBorrows());
        assertEquals("Returned", deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
        assertEquals(0, data.processExpiredBorrows());
    }

    @Test
    void dueReminderIsSentOnlyOnce() {
        DummyDataService data = newService();
        data.processDueReminders();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Due Soon " + System.nanoTime(),
                ListingType.LEND, null, 1);
        Deal deal = data.submitBorrowRequest(alice, listing.getId(),
                LocalDateTime.now(), LocalDateTime.now().plusHours(2));
        data.acceptDeal(charlie, deal.getId());
        data.getDealExtras(deal.getId()).borrowEndAt = LocalDateTime.now().plusHours(2);

        assertEquals(1, data.processDueReminders());
        assertTrue(data.getDealExtras(deal.getId()).dueReminderSent);
        assertEquals(0, data.processDueReminders());
    }

    @Test
    void borrowRequestRejectsPastPeriods() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        Listing listing = addListing(data, "Past Borrow " + System.nanoTime(),
                ListingType.LEND, null, 1);
        LocalDateTime now = LocalDateTime.now();

        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(
                alice, listing.getId(), now.minusHours(1), now.plusMinutes(30)));
        assertThrows(IllegalArgumentException.class, () -> data.submitBorrowRequest(
                alice, listing.getId(), now.minusHours(2), now.minusMinutes(1)));
    }

    @Test
    void acceptingExpiredBorrowRequestIsRejected() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Expired Accept " + System.nanoTime(),
                ListingType.LEND, null, 1);
        LocalDateTime now = LocalDateTime.now();
        Deal deal = data.submitBorrowRequest(alice, listing.getId(), now, now.plusHours(2));
        data.getDealExtras(deal.getId()).borrowEndAt = now.minusSeconds(1);

        assertThrows(IllegalArgumentException.class, () -> data.acceptDeal(charlie, deal.getId()));
        assertEquals("Pending", deal.getStatus());
        assertEquals(ListingStatus.LIVE, listing.getStatus());
    }

    @Test
    void removedListingCannotBeEditedBySeller() {
        DummyDataService data = newService();
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Removed Edit " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        String originalTitle = listing.getTitle();
        data.takeDownListing(admin, listing.getId(), "Test removal");

        assertThrows(IllegalStateException.class, () -> data.updateSellerListing(
                charlie, copyForUpdate(listing, "Renamed after removal")));
        assertEquals(originalTitle, listing.getTitle());
    }

    @Test
    void adminCannotEditRejectedListing() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Rejected Admin Edit " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        data.rejectListing(admin, listing.getId(), "Needs a better photo");
        String originalTitle = listing.getTitle();

        assertThrows(IllegalStateException.class, () -> data.updateListingAsAdmin(
                admin, copyForUpdate(listing, "Renamed after rejection")));

        assertEquals(originalTitle, listing.getTitle());
        assertEquals(ListingStatus.REJECTED, listing.getStatus());
        assertEquals("Needs a better photo", listing.getRejectionReason());
    }

    @Test
    void sellerCanResubmitRejectedListing() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        User seller = data.findUser("charlie");
        Listing listing = addListing(data, "Rejected Seller Edit " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        data.rejectListing(admin, listing.getId(), "Needs a better photo");

        data.updateSellerListing(seller, copyForUpdate(listing, "Seller resubmission"));

        assertEquals("Seller resubmission", listing.getTitle());
        assertEquals(ListingStatus.PENDING_REVIEW, listing.getStatus());
        assertNull(listing.getRejectionReason());
        assertNull(listing.getReviewedAt());
    }

    @Test
    void moderationOnlyAcceptsPendingListings() {
        DummyDataService data = newService();
        User admin = data.findUser("admin");
        Listing live = addListing(data, "Live Moderation " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        assertThrows(IllegalStateException.class, () -> data.approveListing(admin, live.getId()));
        assertThrows(IllegalStateException.class,
                () -> data.rejectListing(admin, live.getId(), "Not pending"));

        Listing rejected = addListing(data, "Rejected Moderation " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        rejected.setStatus(ListingStatus.PENDING_REVIEW);
        data.rejectListing(admin, rejected.getId(), "Rejected once");

        assertThrows(IllegalStateException.class, () -> data.approveListing(admin, rejected.getId()));
    }

    @Test
    void adminCannotEditListingWithActiveDeal() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Admin Active Edit " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal deal = data.submitOffer(alice, listing.getId(), 80.0);
        data.acceptDeal(charlie, deal.getId());

        assertFalse(data.isListingEditable(listing.getId()));
        assertThrows(IllegalStateException.class, () -> data.updateListingAsAdmin(
                admin, copyForUpdate(listing, "Admin rename")));
    }

    @Test
    void expiredPendingBorrowIsCancelledAndCanBeResubmitted() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        Listing listing = addListing(data, "Expired Pending " + System.nanoTime(),
                ListingType.LEND, null, 1);
        LocalDateTime now = LocalDateTime.now();
        Deal deal = data.submitBorrowRequest(alice, listing.getId(), now, now.plusHours(2));
        data.getDealExtras(deal.getId()).borrowEndAt = now.minusSeconds(1);

        assertEquals(1, data.processExpiredBorrows());
        assertEquals("Cancelled", deal.getStatus());

        Deal retry = data.submitBorrowRequest(alice, listing.getId(),
                now.plusMinutes(1), now.plusHours(2));
        assertNotNull(retry);
    }

    @Test
    void expiredBorrowCounterIsCancelled() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Expired Counter " + System.nanoTime(),
                ListingType.LEND, null, 1);
        LocalDateTime now = LocalDateTime.now();
        Deal deal = data.submitBorrowRequest(alice, listing.getId(), now, now.plusHours(2));
        data.counterDeal(charlie, deal.getId(), null, now.plusMinutes(1), now.plusHours(1), null);
        data.getDealExtras(deal.getId()).counterBorrowEndAt = now.minusSeconds(1);

        assertEquals(1, data.processExpiredBorrows());
        assertEquals("Cancelled", deal.getStatus());
    }

    @Test
    void sellerCanWithdrawCounterAndCancelAcceptedSellDeal() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        User bob = data.findUser("bob");
        Listing counteredListing = addListing(data, "Withdraw Counter " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal countered = data.submitOffer(alice, counteredListing.getId(), 80.0);
        data.counterDeal(charlie, countered.getId(), 85.0, null, null, null);

        data.declineDeal(charlie, countered.getId(), "Changed my mind");
        assertEquals("Declined", countered.getStatus());

        Listing acceptedListing = addListing(data, "Cancel Accepted " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        Deal accepted = data.submitOffer(bob, acceptedListing.getId(), 90.0);
        data.acceptDeal(charlie, accepted.getId());
        assertEquals(ListingStatus.RESERVED, acceptedListing.getStatus());

        data.cancelAcceptedDeal(charlie, accepted.getId(), "Buyer did not collect");
        assertEquals("Cancelled", accepted.getStatus());
        assertEquals(ListingStatus.LIVE, acceptedListing.getStatus());
    }

    @Test
    void invalidPasswordDoesNotPartiallyUpdateSellerProfile() {
        DummyDataService data = newService();
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Profile Atomicity " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        String originalBio = charlie.getBio();

        assertThrows(IllegalArgumentException.class, () -> data.updateSellerProfile(
                charlie, "Changed Name", "weak", "weak", "New bio", "Library"));

        assertEquals("Charlie Lee", charlie.getFullName());
        assertEquals("Charlie Lee", listing.getSellerFullName());
        assertEquals(originalBio, charlie.getBio());
    }

    @Test
    void invalidPasswordDoesNotPartiallyUpdateBuyerProfile() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");

        assertThrows(IllegalArgumentException.class, () -> data.updateBuyerProfile(
                alice, "Changed Name", "weak", "weak", "Hall 9"));

        assertEquals("Alice Chan", alice.getFullName());
        assertEquals("Hall 3", alice.getResidenceHall());
    }

    @Test
    void sellerListingCreationClearsTypeSpecificFields() {
        DummyDataService data = newService();
        User charlie = data.findUser("charlie");
        Listing listing = new Listing();
        listing.setTitle("Seller Normalization " + System.nanoTime());
        listing.setDescription("Normalize");
        listing.setCategory("Textbooks");
        listing.setType(ListingType.SELL);
        listing.setCondition("Used");
        listing.setMeetupLocation("Library");
        listing.setPrice(50.0);
        listing.setLendingDays(7);

        Listing created = data.createSellerListing(charlie, listing);

        assertNull(created.getLendingDays());
        assertEquals(ListingStatus.PENDING_REVIEW, created.getStatus());
    }

    @Test
    void formalOfferRequiresChatParticipant() {
        DummyDataService data = newService();
        User bob = data.findUser("bob");
        Listing listing = addListing(data, "Formal Offer Auth " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        var thread = data.getOrCreateChatThread(listing.getId(), "alice");

        assertThrows(SecurityException.class,
                () -> data.sendFormalOffer(bob, thread.id(), 50.0, null, null));
    }

    @Test
    void sellerRenameUpdatesListingDisplayName() {
        DummyDataService data = newService();
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Seller Rename " + System.nanoTime(),
                ListingType.SELL, 100.0, null);

        data.updateSellerProfile(charlie, "Charles Lee", null, null,
                charlie.getBio(), charlie.getPreferredMeetup());

        assertEquals("Charles Lee", listing.getSellerFullName());
    }

    @Test
    void dueReminderFallsBackToBorrowEndDate() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User charlie = data.findUser("charlie");
        Listing listing = addListing(data, "Reminder Fallback " + System.nanoTime(),
                ListingType.LEND, null, 1);
        LocalDateTime now = LocalDateTime.now();
        Deal deal = data.submitBorrowRequest(alice, listing.getId(), now, now.plusHours(2));
        data.acceptDeal(charlie, deal.getId());
        DummyDataService.DealExtras extra = data.getDealExtras(deal.getId());
        extra.borrowEndAt = null;
        extra.dueReminderSent = false;
        deal.setBorrowEnd(java.time.LocalDate.now());

        data.processDueReminders();

        assertTrue(extra.dueReminderSent);
    }

    @Test
    void adminCanResolveListingReport() {
        DummyDataService data = newService();
        User alice = data.findUser("alice");
        User admin = data.findUser("admin");
        Listing listing = addListing(data, "Reported Listing " + System.nanoTime(),
                ListingType.SELL, 100.0, null);
        data.reportListing(alice, listing.getId(), "Suspicious pricing");

        DummyDataService.ReportRecord report = data.getAllReports().get(0);
        data.resolveReport(admin, report.id());

        assertEquals("Resolved", data.getAllReports().get(0).status());
        assertThrows(IllegalStateException.class, () -> data.resolveReport(admin, report.id()));
    }
}
