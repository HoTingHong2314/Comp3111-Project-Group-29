package com.hkust.marketplace.utils;

import com.hkust.marketplace.models.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * File-backed data store. The full state is saved as a JSON snapshot in the
 * user home folder ({@code ~/.hkust-marketplace/marketplace-data.json}) after
 * every mutation and reloaded when the application starts.
 */
public class DummyDataService {

    private static final class Holder {
        private static final DummyDataService INSTANCE = new DummyDataService();
    }

    private final MarketplaceDataStore store;

    private final Map<String, User> users = new LinkedHashMap<>();
    private final Map<String, Listing> listings = new LinkedHashMap<>();
    private final List<Deal> deals = new ArrayList<>();
    private final List<Notification> notifications = new ArrayList<>();
    private final List<Review> reviews = new ArrayList<>();

    private final AtomicInteger listingIdCounter = new AtomicInteger(100);
    private final AtomicInteger dealIdCounter = new AtomicInteger(500);
    private final AtomicInteger notifIdCounter = new AtomicInteger(1);
    private final AtomicInteger reviewIdCounter = new AtomicInteger(1);

    private final Map<String, DealExtras> dealExtras = new HashMap<>();
    private final Map<String, ChatThreadInfo> chatThreads = new LinkedHashMap<>();
    private final Map<String, ChatThreadInfo> chatThreadsById = new LinkedHashMap<>();
    private final Map<String, List<ChatMessage>> chatMessages = new LinkedHashMap<>();
    private final List<ReportRecord> reports = new ArrayList<>();
    private final AtomicInteger chatThreadIdCounter = new AtomicInteger(1);
    private final AtomicInteger chatMessageIdCounter = new AtomicInteger(1);
    private final AtomicInteger reportIdCounter = new AtomicInteger(1);

    /**
     * Additional deal metadata that is stored alongside the core deal record.
     */
    public static final class DealExtras {
        public LocalDateTime borrowStartAt;
        public LocalDateTime borrowEndAt;
        public Double counterPrice;
        public LocalDateTime counterBorrowStartAt;
        public LocalDateTime counterBorrowEndAt;
        public String counterMessage;
        public String listingCategory;
        public boolean dueReminderSent;
    }

    /**
     * A single message in a listing-linked chat thread.
     */
    public record ChatMessage(String id, String threadId, String senderUsername,
                              String content, String type, String relatedDealId,
                              LocalDateTime createdAt) {
    }

    /**
     * Identifying information for a buyer/seller chat thread.
     */
    public record ChatThreadInfo(String id, String listingId, String listingTitle,
                                 String buyerUsername, String sellerUsername,
                                 LocalDateTime lastMessageAt) {
    }

    /**
     * A buyer-submitted listing report awaiting administrator action.
     */
    public record ReportRecord(String id, String listingId, String listingTitle,
                               String buyerUsername, String reason, String status,
                               LocalDateTime createdAt) {
    }

    // Predefined HKUST locations
    public static final List<String> HKUST_LOCATIONS = List.of(
            "Academic Building (LG1)",
            "Library",
            "Student Centre",
            "Hall 1 Lobby",
            "Hall 2 Lobby",
            "Hall 3 Lobby",
            "Hall 4 Lobby",
            "Hall 5 Lobby",
            "Hall 6 Lobby",
            "Hall 7 Lobby",
            "Hall 8 Lobby",
            "Hall 9 Lobby",
            "UG Hall 1",
            "UG Hall 2",
            "North Gate",
            "South Gate",
            "Shaw Auditorium",
            "Coffee Shop (LG5)",
            "Sports Hall"
    );

    public static final List<String> BUYER_RESIDENCES = List.of(
            "Hall 1", "Hall 2", "Hall 3", "Hall 4", "Hall 5",
            "Hall 6", "Hall 7", "Hall 8", "Hall 9",
            "UG Hall 1", "UG Hall 2", "Off-campus"
    );

    public static final List<String> CATEGORIES = List.of(
            "Textbooks", "Electronics", "Furniture", "Clothing",
            "Stationery", "Sports Equipment", "Kitchenware", "Others"
    );

    public static final List<String> CONDITIONS = List.of(
            "New", "Like New", "Used", "For Parts"
    );

    private DummyDataService() {
        this(MarketplaceDataStore.defaultStore());
    }

    public DummyDataService(MarketplaceDataStore store) {
        this.store = store;
        MarketplaceDataStore.Snapshot snapshot = store.load();
        if (snapshot == null) {
            seedData();
            persist();
        } else {
            boolean repaired = restore(snapshot);
            if (repaired) {
                persist();
            }
        }
    }

    public static DummyDataService getInstance() {
        return Holder.INSTANCE;
    }

    private synchronized void persist() {
        store.save(toSnapshot());
    }

    private synchronized MarketplaceDataStore.Snapshot toSnapshot() {
        MarketplaceDataStore.Snapshot snapshot = new MarketplaceDataStore.Snapshot();
        snapshot.users.addAll(users.values());
        snapshot.listings.addAll(listings.values());
        snapshot.deals.addAll(deals);
        snapshot.dealExtras.putAll(dealExtras);
        snapshot.notifications.addAll(notifications);
        snapshot.reviews.addAll(reviews);
        snapshot.chatThreads.addAll(chatThreadsById.values());
        for (Map.Entry<String, List<ChatMessage>> entry : chatMessages.entrySet()) {
            snapshot.chatMessages.put(entry.getKey(), new ArrayList<>(entry.getValue()));
        }
        snapshot.reports.addAll(reports);
        snapshot.listingIdCounter = listingIdCounter.get();
        snapshot.dealIdCounter = dealIdCounter.get();
        snapshot.notifIdCounter = notifIdCounter.get();
        snapshot.reviewIdCounter = reviewIdCounter.get();
        snapshot.chatThreadIdCounter = chatThreadIdCounter.get();
        snapshot.chatMessageIdCounter = chatMessageIdCounter.get();
        snapshot.reportIdCounter = reportIdCounter.get();
        return snapshot;
    }

    private boolean restore(MarketplaceDataStore.Snapshot snapshot) {
        boolean migratedPasswords = false;
        boolean normalizedProfiles = false;
        users.clear();
        if (snapshot.users != null) {
            for (User user : snapshot.users) {
                if (user != null && user.getUsername() != null) {
                    if (user.getPasswordHash() != null && !user.getPasswordHash().isBlank()
                            && !PasswordUtil.isHashed(user.getPasswordHash())) {
                        user.setPasswordHash(PasswordUtil.hash(user.getPasswordHash()));
                        migratedPasswords = true;
                    }
                    if (normalizeProfile(user)) {
                        normalizedProfiles = true;
                    }
                    users.put(user.getUsername().toLowerCase(), user);
                }
            }
        }
        listings.clear();
        if (snapshot.listings != null) {
            for (Listing listing : snapshot.listings) {
                if (listing != null && listing.getId() != null) {
                    listings.put(listing.getId(), listing);
                }
            }
        }
        deals.clear();
        if (snapshot.deals != null) {
            for (Deal deal : snapshot.deals) {
                if (deal != null) {
                    deals.add(deal);
                }
            }
        }
        dealExtras.clear();
        if (snapshot.dealExtras != null) {
            dealExtras.putAll(snapshot.dealExtras);
        }
        notifications.clear();
        if (snapshot.notifications != null) {
            for (Notification notification : snapshot.notifications) {
                if (notification != null) {
                    notifications.add(notification);
                }
            }
        }
        reviews.clear();
        if (snapshot.reviews != null) {
            for (Review review : snapshot.reviews) {
                if (review != null) {
                    reviews.add(review);
                }
            }
        }
        chatThreads.clear();
        chatThreadsById.clear();
        if (snapshot.chatThreads != null) {
            for (ChatThreadInfo thread : snapshot.chatThreads) {
                if (thread == null || thread.id() == null) {
                    continue;
                }
                chatThreadsById.put(thread.id(), thread);
                if (thread.listingId() != null && thread.buyerUsername() != null) {
                    chatThreads.put(chatKey(thread.listingId(), thread.buyerUsername()), thread);
                }
            }
        }
        chatMessages.clear();
        if (snapshot.chatMessages != null) {
            for (Map.Entry<String, List<ChatMessage>> entry : snapshot.chatMessages.entrySet()) {
                if (entry.getKey() != null && entry.getValue() != null) {
                    chatMessages.put(entry.getKey(), new ArrayList<>(entry.getValue()));
                }
            }
        }
        reports.clear();
        if (snapshot.reports != null) {
            for (ReportRecord report : snapshot.reports) {
                if (report != null) {
                    reports.add(report);
                }
            }
        }
        listingIdCounter.set(snapshot.listingIdCounter > 0 ? snapshot.listingIdCounter : 100);
        dealIdCounter.set(snapshot.dealIdCounter > 0 ? snapshot.dealIdCounter : 500);
        notifIdCounter.set(Math.max(snapshot.notifIdCounter, 1));
        reviewIdCounter.set(Math.max(snapshot.reviewIdCounter, 1));
        chatThreadIdCounter.set(Math.max(snapshot.chatThreadIdCounter, 1));
        chatMessageIdCounter.set(Math.max(snapshot.chatMessageIdCounter, 1));
        reportIdCounter.set(Math.max(snapshot.reportIdCounter, 1));
        boolean normalizedNotifications = normalizeNotificationIds();
        return migratedPasswords || normalizedProfiles || normalizedNotifications;
    }

    private static boolean normalizeProfile(User user) {
        if (user.getRole() == null) {
            return false;
        }
        boolean changed = false;
        switch (user.getRole()) {
            case BUYER -> {
                String residence = sanitizeBuyerResidence(user.getResidenceHall());
                if (!Objects.equals(residence, user.getResidenceHall())) {
                    user.setResidenceHall(residence);
                    changed = true;
                }
                if (user.getBio() != null) {
                    user.setBio(null);
                    changed = true;
                }
                if (user.getPreferredMeetup() != null) {
                    user.setPreferredMeetup(null);
                    changed = true;
                }
            }
            case SELLER -> {
                if (user.getResidenceHall() != null) {
                    user.setResidenceHall(null);
                    changed = true;
                }
            }
            case ADMIN -> {
                if (user.getResidenceHall() != null) {
                    user.setResidenceHall(null);
                    changed = true;
                }
                if (user.getBio() != null) {
                    user.setBio(null);
                    changed = true;
                }
                if (user.getPreferredMeetup() != null) {
                    user.setPreferredMeetup(null);
                    changed = true;
                }
            }
        }
        return changed;
    }

    private static String sanitizeBuyerResidence(String residenceHall) {
        if (residenceHall == null || residenceHall.isBlank()) {
            return null;
        }
        String residence = residenceHall.trim();
        return BUYER_RESIDENCES.contains(residence) ? residence : null;
    }

    private static String normalizeBuyerResidence(String residenceHall) {
        if (residenceHall == null || residenceHall.isBlank()) {
            return null;
        }
        String residence = residenceHall.trim();
        if (!BUYER_RESIDENCES.contains(residence)) {
            throw new IllegalArgumentException("Residence must be one of the listed HKUST halls.");
        }
        return residence;
    }

    private synchronized boolean normalizeNotificationIds() {
        Set<String> seenIds = new HashSet<>();
        int nextId = notifIdCounter.get();
        for (Notification notification : notifications) {
            String id = notification.getId();
            if (id != null && id.matches("N\\d+")) {
                nextId = Math.max(nextId, Integer.parseInt(id.substring(1)) + 1);
            }
        }
        boolean repaired = false;
        for (Notification notification : notifications) {
            String id = notification.getId();
            if (id == null || id.isBlank() || seenIds.contains(id)) {
                String newId;
                do {
                    newId = "N" + nextId++;
                } while (seenIds.contains(newId));
                notification.setId(newId);
                repaired = true;
            }
            seenIds.add(notification.getId());
        }
        notifIdCounter.set(nextId);
        return repaired;
    }

    private void seedData() {
        // Admin
        User admin = new User("admin", "System Admin", PasswordUtil.hash("admin123"), UserRole.ADMIN);
        admin.setFullName("HKUST Marketplace Admin");
        users.put("admin", admin);

        // Sample buyers
        User buyer1 = new User("alice", "Alice Chan", PasswordUtil.hash("password1"), UserRole.BUYER);
        buyer1.setResidenceHall("Hall 3");
        users.put("alice", buyer1);

        User buyer2 = new User("bob", "Bob Wong", PasswordUtil.hash("password1"), UserRole.BUYER);
        buyer2.setResidenceHall("Off-campus");
        users.put("bob", buyer2);

        // Sample sellers
        User seller1 = new User("charlie", "Charlie Lee", PasswordUtil.hash("password1"), UserRole.SELLER);
        seller1.setBio("Final year CSE student selling textbooks and gadgets.");
        seller1.setPreferredMeetup("Library");
        seller1.setAverageRating(5.0);
        seller1.setRatingCount(1);
        users.put("charlie", seller1);

        User seller2 = new User("diana", "Diana Ng", PasswordUtil.hash("password1"), UserRole.SELLER);
        seller2.setBio("Selling furniture and kitchen items before graduation.");
        seller2.setPreferredMeetup("Hall 5 Lobby");
        seller2.setAverageRating(4.3);
        seller2.setRatingCount(8);
        users.put("diana", seller2);

        // Sample listings
        Listing l1 = createListing("Calculus Textbook (Stewart)", "Barely used, 8th edition. Perfect for MATH 1013.",
                "Textbooks", ListingType.SELL, "Like New", 180.0, null, "Library", "charlie", "Charlie Lee");
        l1.setStatus(ListingStatus.LIVE);
        listings.put(l1.getId(), l1);

        Listing l2 = createListing("MacBook Pro Charger 61W", "Original Apple charger, works perfectly.",
                "Electronics", ListingType.SELL, "Used", 250.0, null, "Academic Building (LG1)", "charlie", "Charlie Lee");
        l2.setStatus(ListingStatus.LIVE);
        listings.put(l2.getId(), l2);

        Listing l3 = createListing("IKEA Desk Lamp", "Good condition, LED bulb included.",
                "Furniture", ListingType.GIVEAWAY, "Used", null, null, "Hall 5 Lobby", "diana", "Diana Ng");
        l3.setStatus(ListingStatus.LIVE);
        listings.put(l3.getId(), l3);

        Listing l4 = createListing("Graphing Calculator TI-84", "Can lend for one semester.",
                "Electronics", ListingType.LEND, "Like New", null, 90, "Student Centre", "diana", "Diana Ng");
        l4.setStatus(ListingStatus.LIVE);
        listings.put(l4.getId(), l4);

        Listing l5 = createListing("Mini Fridge", "Works well, cleaning required.",
                "Furniture", ListingType.SELL, "Used", 400.0, null, "Hall 2 Lobby", "diana", "Diana Ng");
        l5.setStatus(ListingStatus.PENDING_REVIEW);
        listings.put(l5.getId(), l5);

        // Sample deals
        Deal d1 = new Deal();
        d1.setId("D" + dealIdCounter.getAndIncrement());
        d1.setListingId(l1.getId());
        d1.setListingTitle(l1.getTitle());
        d1.setBuyerUsername("alice");
        d1.setSellerUsername("charlie");
        d1.setDealType(ListingType.SELL);
        d1.setStatus(DealStatuses.ACCEPTED);
        d1.setOfferPrice(160.0);
        deals.add(d1);
        extras(d1.getId()).listingCategory = l1.getCategory();
        l1.setStatus(ListingStatus.RESERVED);

        Deal d2 = new Deal();
        d2.setId("D" + dealIdCounter.getAndIncrement());
        d2.setListingId("L0");
        d2.setListingTitle("Old Chemistry Book");
        d2.setBuyerUsername("bob");
        d2.setSellerUsername("charlie");
        d2.setDealType(ListingType.SELL);
        d2.setStatus(DealStatuses.COMPLETED);
        d2.setOfferPrice(120.0);
        d2.setCompletedAt(LocalDateTime.now().minusDays(5));
        d2.setRated(true);
        deals.add(d2);
        extras(d2.getId()).listingCategory = "Textbooks";

        Deal d3 = new Deal();
        d3.setId("D" + dealIdCounter.getAndIncrement());
        d3.setListingId(l4.getId());
        d3.setListingTitle(l4.getTitle());
        d3.setBuyerUsername("alice");
        d3.setSellerUsername("diana");
        d3.setDealType(ListingType.LEND);
        d3.setStatus(DealStatuses.ACCEPTED);
        d3.setBorrowStart(LocalDate.now());
        d3.setBorrowEnd(LocalDate.now().plusDays(1));
        deals.add(d3);
        extras(d3.getId()).listingCategory = l4.getCategory();
        extras(d3.getId()).borrowStartAt = LocalDateTime.now();
        extras(d3.getId()).borrowEndAt = LocalDateTime.now().plusDays(1);
        l4.setStatus(ListingStatus.LENT);

        Deal d4 = new Deal();
        d4.setId("D" + dealIdCounter.getAndIncrement());
        d4.setListingId(l2.getId());
        d4.setListingTitle(l2.getTitle());
        d4.setBuyerUsername("bob");
        d4.setSellerUsername("charlie");
        d4.setDealType(ListingType.SELL);
        d4.setStatus(DealStatuses.PENDING);
        d4.setOfferPrice(220.0);
        deals.add(d4);
        extras(d4.getId()).listingCategory = l2.getCategory();

        // Sample notifications
        addNotification(new Notification("alice", "Offer Accepted",
                "Charlie accepted your offer of HK$160 for Calculus Textbook.", "Offer"));
        addNotification(new Notification("charlie", "New Offer Received",
                "Alice offered HK$160 for your Calculus Textbook.", "Offer"));
        addNotification(new Notification("diana", "Listing Pending",
                "Your Mini Fridge listing is awaiting admin review.", "System"));
        addNotification(new Notification("admin", "New Listing Submitted",
                "Diana submitted 'Mini Fridge' for review.", "System"));

        // Sample review
        Review r1 = new Review();
        r1.setId("R" + reviewIdCounter.getAndIncrement());
        r1.setSellerUsername("charlie");
        r1.setBuyerUsername("bob");
        r1.setListingTitle("Old Chemistry Book");
        r1.setRating(5);
        r1.setComment("Very smooth transaction, book in excellent condition!");
        reviews.add(r1);

        ChatThreadInfo seedThread = new ChatThreadInfo(
                "T" + chatThreadIdCounter.getAndIncrement(),
                l1.getId(), l1.getTitle(), "alice", "charlie",
                LocalDateTime.now().minusHours(1));
        chatThreads.put(chatKey(l1.getId(), "alice"), seedThread);
        chatThreadsById.put(seedThread.id(), seedThread);
        List<ChatMessage> seedMessages = new ArrayList<>();
        seedMessages.add(new ChatMessage("M" + chatMessageIdCounter.getAndIncrement(),
                seedThread.id(), "alice", "Hi, is the textbook still available?",
                "Text", null, LocalDateTime.now().minusHours(2)));
        seedMessages.add(new ChatMessage("M" + chatMessageIdCounter.getAndIncrement(),
                seedThread.id(), "charlie", "Yes, it is! You can pick it up at the Library.",
                "Text", null, LocalDateTime.now().minusHours(1)));
        chatMessages.put(seedThread.id(), seedMessages);
    }

    private Listing createListing(String title, String desc, String cat, ListingType type,
                                  String condition, Double price, Integer days,
                                  String meetup, String sellerUser, String sellerName) {
        Listing l = new Listing();
        l.setId("L" + listingIdCounter.getAndIncrement());
        l.setTitle(title);
        l.setDescription(desc);
        l.setCategory(cat);
        l.setType(type);
        l.setCondition(condition);
        l.setPrice(price);
        l.setLendingDays(days);
        l.setMeetupLocation(meetup);
        l.setSellerUsername(sellerUser);
        l.setSellerFullName(sellerName);
        l.setPostedAt(LocalDateTime.now().minusDays(new Random().nextInt(10)));
        return l;
    }

    // ========== User methods ==========
    public boolean usernameExists(String username) {
        return users.containsKey(username.toLowerCase());
    }

    public User findUser(String username) {
        return users.get(username.toLowerCase());
    }

    public void registerUser(User user) {
        users.put(user.getUsername().toLowerCase(), user);
        persist();
    }

    /**
     * Registers a buyer after validating the username, name and password.
     */
    public synchronized User registerBuyer(String username, String fullName,
                                           String rawPassword, String confirmPassword,
                                           String residenceHall) {
        String key = username == null ? "" : username.trim().toLowerCase();
        if (!key.matches("[a-z0-9_]{3,20}")) {
            throw new IllegalArgumentException(
                    "Username must be 3-20 characters: letters, digits or underscore.");
        }
        if (users.containsKey(key)) {
            throw new IllegalArgumentException("Username already taken.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full Name cannot be empty.");
        }
        String passwordCheck = PasswordUtil.validate(rawPassword);
        if (!"OK".equals(passwordCheck)) {
            throw new IllegalArgumentException(passwordCheck);
        }
        if (!rawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        String normalizedResidence = normalizeBuyerResidence(residenceHall);

        User user = new User(key, fullName.trim(),
                PasswordUtil.hash(rawPassword), UserRole.BUYER);
        user.setResidenceHall(normalizedResidence);
        users.put(key, user);
        persist();
        return user;
    }

    /**
     * Authenticates a user for the requested role and rejects inactive accounts.
     */
    public synchronized User login(String username, String rawPassword, UserRole expectedRole) {
        User user = findUser(username);
        if (user == null) {
            throw new IllegalArgumentException("User not found.");
        }
        if (user.getRole() != expectedRole) {
            throw new IllegalArgumentException("This account is not a "
                    + expectedRole.name().toLowerCase() + " account.");
        }
        if (!user.isActive()) {
            throw new IllegalStateException("This account has been deactivated.");
        }
        if (!PasswordUtil.verify(rawPassword, user.getPasswordHash())) {
            throw new IllegalArgumentException("Incorrect password.");
        }
        return user;
    }

    /**
     * Updates a buyer profile and optionally replaces the password.
     */
    public synchronized void updateBuyerProfile(User actor, String fullName,
                                                String newRawPassword, String confirmPassword,
                                                String residenceHall) {
        requireRole(actor, UserRole.BUYER);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full Name cannot be empty.");
        }
        String passwordHash = validatedPasswordHash(newRawPassword, confirmPassword);
        String normalizedResidence = normalizeBuyerResidence(residenceHall);
        actor.setFullName(fullName.trim());
        actor.setResidenceHall(normalizedResidence);
        actor.setBio(null);
        actor.setPreferredMeetup(null);
        if (passwordHash != null) {
            actor.setPasswordHash(passwordHash);
        }
        persist();
    }

    /**
     * Registers a seller with optional bio and preferred meet-up details.
     */
    public synchronized User registerSeller(String username, String fullName,
                                            String rawPassword, String confirmPassword,
                                            String preferredMeetup, String bio) {
        String key = username == null ? "" : username.trim().toLowerCase();
        if (!key.matches("[a-z0-9_]{3,20}")) {
            throw new IllegalArgumentException(
                    "Username must be 3-20 characters: letters, digits or underscore.");
        }
        if (users.containsKey(key)) {
            throw new IllegalArgumentException("Username already taken.");
        }
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full Name cannot be empty.");
        }
        String passwordCheck = PasswordUtil.validate(rawPassword);
        if (!"OK".equals(passwordCheck)) {
            throw new IllegalArgumentException(passwordCheck);
        }
        if (!rawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        User user = new User(key, fullName.trim(), PasswordUtil.hash(rawPassword), UserRole.SELLER);
        user.setPreferredMeetup(preferredMeetup);
        user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        users.put(key, user);
        persist();
        return user;
    }

    /**
     * Updates a seller profile and keeps listing display names in sync.
     */
    public synchronized void updateSellerProfile(User actor, String fullName,
                                                 String newRawPassword, String confirmPassword,
                                                 String bio, String preferredMeetup) {
        requireRole(actor, UserRole.SELLER);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full Name cannot be empty.");
        }
        String passwordHash = validatedPasswordHash(newRawPassword, confirmPassword);
        actor.setFullName(fullName.trim());
        actor.setResidenceHall(null);
        actor.setBio(bio == null || bio.isBlank() ? null : bio.trim());
        actor.setPreferredMeetup(preferredMeetup);
        if (passwordHash != null) {
            actor.setPasswordHash(passwordHash);
        }
        syncSellerDisplayName(actor.getUsername(), actor.getFullName());
        persist();
    }

    /**
     * Updates an administrator profile and optionally replaces the password.
     */
    public synchronized void updateAdminProfile(User actor, String fullName,
                                                String newRawPassword, String confirmPassword) {
        requireRole(actor, UserRole.ADMIN);
        if (fullName == null || fullName.isBlank()) {
            throw new IllegalArgumentException("Full Name cannot be empty.");
        }
        String passwordHash = validatedPasswordHash(newRawPassword, confirmPassword);
        actor.setFullName(fullName.trim());
        if (passwordHash != null) {
            actor.setPasswordHash(passwordHash);
        }
        persist();
    }

    private static String validatedPasswordHash(String newRawPassword, String confirmPassword) {
        if (newRawPassword == null || newRawPassword.isBlank()) {
            return null;
        }
        String passwordCheck = PasswordUtil.validate(newRawPassword);
        if (!"OK".equals(passwordCheck)) {
            throw new IllegalArgumentException(passwordCheck);
        }
        if (!newRawPassword.equals(confirmPassword)) {
            throw new IllegalArgumentException("Passwords do not match.");
        }
        return PasswordUtil.hash(newRawPassword);
    }

    private static void requireRole(User actor, UserRole role) {
        if (actor == null || actor.getRole() != role) {
            throw new SecurityException("You do not have permission to perform this action.");
        }
    }

    private static void requireBuyer(User actor) {
        requireRole(actor, UserRole.BUYER);
    }

    private static void requireSeller(User actor) {
        requireRole(actor, UserRole.SELLER);
    }

    private static void requireAdmin(User actor) {
        requireRole(actor, UserRole.ADMIN);
    }

    public Collection<User> getAllUsers() {
        return users.values();
    }

    public List<User> getUsersByRole(UserRole role) {
        return users.values().stream()
                .filter(u -> u.getRole() == role)
                .collect(Collectors.toList());
    }

    // ========== Listing methods ==========
    public Collection<Listing> getAllListings() {
        return listings.values();
    }

    public List<Listing> getLiveListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .collect(Collectors.toList());
    }

    public List<Listing> getPendingListings() {
        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.PENDING_REVIEW)
                .collect(Collectors.toList());
    }

    public List<Listing> getListingsBySeller(String username) {
        return listings.values().stream()
                .filter(l -> l.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    /**
     * Searches live listings with keyword, category, type, price and condition filters.
     */
    public List<Listing> searchListings(String keyword, String category, ListingType type,
                                        Double minPrice, Double maxPrice, String condition,
                                        ListingSort sort) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        Comparator<Listing> comparator = switch (sort == null ? ListingSort.NEWEST : sort) {
            case NEWEST -> Comparator.comparing(Listing::getPostedAt).reversed();
            case PRICE_LOW_HIGH -> Comparator.comparingDouble(PriceUtil::effectivePrice);
            case PRICE_HIGH_LOW -> Comparator.comparingDouble(PriceUtil::effectivePrice).reversed();
        };

        return listings.values().stream()
                .filter(l -> l.getStatus() == ListingStatus.LIVE)
                .filter(l -> kw.isEmpty()
                        || (l.getTitle() != null && l.getTitle().toLowerCase().contains(kw))
                        || (l.getDescription() != null && l.getDescription().toLowerCase().contains(kw)))
                .filter(l -> category == null || category.isBlank()
                        || category.equals("All Categories") || category.equals(l.getCategory()))
                .filter(l -> type == null || type == l.getType())
                .filter(l -> condition == null || condition.isBlank()
                        || condition.equals("Any") || condition.equals(l.getCondition()))
                .filter(l -> {
                    if (minPrice == null && maxPrice == null) return true;
                    double effective = PriceUtil.effectivePrice(l);
                    if (minPrice != null && effective < minPrice) return false;
                    return maxPrice == null || effective <= maxPrice;
                })
                .sorted(comparator)
                .collect(Collectors.toList());
    }

    public Listing findListing(String id) {
        return listings.get(id);
    }

    /**
     * Returns a listing and records a view when it is still live.
     */
    public synchronized Listing openListingDetail(String listingId) {
        Listing listing = listings.get(listingId);
        if (listing != null && listing.getStatus() == ListingStatus.LIVE) {
            listing.setViewCount(listing.getViewCount() + 1);
            persist();
        }
        return listing;
    }

    public double getSellerAverageRating(String sellerUsername) {
        List<Review> visible = getReviewsForSeller(sellerUsername);
        if (!visible.isEmpty()) {
            return visible.stream().mapToInt(Review::getRating).average().orElse(0.0);
        }
        User seller = findUser(sellerUsername);
        return seller == null ? 0.0 : seller.getAverageRating();
    }

    public int getSellerRatingCount(String sellerUsername) {
        List<Review> visible = getReviewsForSeller(sellerUsername);
        if (!visible.isEmpty()) {
            return visible.size();
        }
        User seller = findUser(sellerUsername);
        return seller == null ? 0 : seller.getRatingCount();
    }

    /**
     * Creates an administrator report for a listing with a buyer reason.
     */
    public synchronized void reportListing(User actor, String listingId, String reason) {
        requireBuyer(actor);
        Listing listing = listings.get(listingId);
        if (listing == null) {
            throw new IllegalArgumentException("Listing not found.");
        }
        if (reason == null || reason.isBlank()) {
            throw new IllegalArgumentException("Please provide a reason.");
        }
        reports.add(new ReportRecord("RP" + reportIdCounter.getAndIncrement(),
                listingId, listing.getTitle(), actor.getUsername(),
                reason.trim(), "Open", LocalDateTime.now()));
        addNotification(new Notification("admin", "Listing Reported",
                actor.getFullName() + " reported '" + listing.getTitle() + "': " + reason.trim(),
                NotificationCategories.SYSTEM));
        persist();
    }

    public List<ReportRecord> getAllReports() {
        return new ArrayList<>(reports);
    }

    /**
     * Marks an open listing report as resolved.
     */
    public synchronized void resolveReport(User admin, String reportId) {
        requireAdmin(admin);
        for (int i = 0; i < reports.size(); i++) {
            ReportRecord report = reports.get(i);
            if (!report.id().equals(reportId)) {
                continue;
            }
            if (!"Open".equals(report.status())) {
                throw new IllegalStateException("This report has already been resolved.");
            }
            reports.set(i, new ReportRecord(report.id(), report.listingId(), report.listingTitle(),
                    report.buyerUsername(), report.reason(), "Resolved", report.createdAt()));
            persist();
            return;
        }
        throw new IllegalArgumentException("Report not found.");
    }

    private static void validateListing(Listing listing) {
        if (listing == null || listing.getTitle() == null || listing.getTitle().isBlank()) {
            throw new IllegalArgumentException("Item name is required.");
        }
        if (listing.getType() == null) {
            throw new IllegalArgumentException("Listing type is required.");
        }
        if (listing.getCategory() == null || !CATEGORIES.contains(listing.getCategory())) {
            throw new IllegalArgumentException("Please choose a valid category.");
        }
        if (listing.getCondition() == null || !CONDITIONS.contains(listing.getCondition())) {
            throw new IllegalArgumentException("Please choose a valid condition.");
        }
        if (listing.getMeetupLocation() == null || listing.getMeetupLocation().isBlank()) {
            throw new IllegalArgumentException("Meet-up location is required.");
        }
        if (listing.getType() == ListingType.SELL
                && (listing.getPrice() == null || !PriceUtil.isValidAmount(listing.getPrice()))) {
            throw new IllegalArgumentException(
                    "A valid price with at most 2 decimal places is required for Sell listings.");
        }
        if (listing.getType() == ListingType.LEND
                && (listing.getLendingDays() == null || listing.getLendingDays() <= 0)) {
            throw new IllegalArgumentException("A valid lending duration is required.");
        }
    }

    private boolean hasActiveDeals(String listingId) {
        return deals.stream().anyMatch(d -> listingId.equals(d.getListingId())
                && (DealStatuses.PENDING.equals(d.getStatus())
                || DealStatuses.COUNTERED.equals(d.getStatus())
                || DealStatuses.ACCEPTED.equals(d.getStatus())));
    }

    /**
     * Returns whether a listing has no final status or active deal.
     */
    public synchronized boolean isListingEditable(String listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null || listing.getStatus() == ListingStatus.REMOVED) {
            return false;
        }
        if (hasActiveDeals(listingId)) {
            return false;
        }
        return listing.getStatus() != ListingStatus.RESERVED
                && listing.getStatus() != ListingStatus.SOLD
                && listing.getStatus() != ListingStatus.LENT
                && listing.getStatus() != ListingStatus.GIVEN;
    }

    /**
     * Creates a pending seller listing and normalizes type-specific fields.
     */
    public synchronized Listing createSellerListing(User seller, Listing listing) {
        requireSeller(seller);
        validateListing(listing);
        listing.setSellerUsername(seller.getUsername());
        listing.setSellerFullName(seller.getFullName());
        listing.setStatus(ListingStatus.PENDING_REVIEW);
        listing.setPostedAt(LocalDateTime.now());
        listing.setReviewedAt(null);
        listing.setRejectionReason(null);
        if (listing.getType() == ListingType.LEND) {
            listing.setPrice(null);
        } else if (listing.getType() == ListingType.GIVEAWAY) {
            listing.setPrice(null);
            listing.setLendingDays(null);
        } else {
            listing.setLendingDays(null);
        }
        addListing(listing);
        addNotification(new Notification("admin", "New Listing Submitted",
                seller.getFullName() + " submitted '" + listing.getTitle() + "' for review.",
                NotificationCategories.SYSTEM));
        persist();
        return listing;
    }

    /**
     * Updates a seller-owned listing when it is eligible for editing.
     */
    public synchronized Listing updateSellerListing(User seller, Listing updated) {
        requireSeller(seller);
        Listing existing = listings.get(updated.getId());
        if (existing == null) {
            throw new IllegalArgumentException("Listing not found.");
        }
        if (!existing.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This listing does not belong to you.");
        }
        if (existing.getStatus() == ListingStatus.REMOVED) {
            throw new IllegalStateException("This listing was removed by the admin and can no longer be edited.");
        }
        if (hasActiveDeals(existing.getId())
                || existing.getStatus() == ListingStatus.RESERVED
                || existing.getStatus() == ListingStatus.SOLD
                || existing.getStatus() == ListingStatus.LENT
                || existing.getStatus() == ListingStatus.GIVEN) {
            throw new IllegalStateException("This listing cannot be edited while a deal is active.");
        }
        validateListing(updated);
        existing.setTitle(updated.getTitle().trim());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setType(updated.getType());
        existing.setCondition(updated.getCondition());
        existing.setPrice(updated.getType() == ListingType.SELL ? updated.getPrice() : null);
        existing.setLendingDays(updated.getType() == ListingType.LEND ? updated.getLendingDays() : null);
        existing.setMeetupLocation(updated.getMeetupLocation());
        existing.setPhotoPaths(updated.getPhotoPaths());
        existing.setStatus(ListingStatus.PENDING_REVIEW);
        existing.setReviewedAt(null);
        existing.setRejectionReason(null);
        updateListing(existing);
        addNotification(new Notification("admin", "Listing Edited",
                seller.getFullName() + " edited '" + existing.getTitle() + "' and it needs review.",
                NotificationCategories.SYSTEM));
        persist();
        return existing;
    }

    /**
     * Deletes a seller listing when no deal is active.
     */
    public synchronized void deleteSellerListing(User seller, String listingId) {
        requireSeller(seller);
        Listing existing = listings.get(listingId);
        if (existing == null) {
            throw new IllegalArgumentException("Listing not found.");
        }
        if (!existing.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This listing does not belong to you.");
        }
        if (hasActiveDeals(listingId)
                || existing.getStatus() == ListingStatus.RESERVED
                || existing.getStatus() == ListingStatus.SOLD
                || existing.getStatus() == ListingStatus.LENT
                || existing.getStatus() == ListingStatus.GIVEN) {
            throw new IllegalStateException("This listing cannot be deleted while a deal is active.");
        }
        removeListing(listingId);
        addNotification(new Notification("admin", "Listing Deleted",
                seller.getFullName() + " deleted '" + existing.getTitle() + "'.",
                NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Makes a pending listing live and notifies the seller.
     */
    public synchronized void approveListing(User admin, String listingId) {
        requireAdmin(admin);
        Listing listing = listings.get(listingId);
        if (listing == null) throw new IllegalArgumentException("Listing not found.");
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only pending listings can be approved.");
        }
        listing.setStatus(ListingStatus.LIVE);
        listing.setReviewedAt(LocalDateTime.now());
        listing.setRejectionReason(null);
        updateListing(listing);
        addNotification(new Notification(listing.getSellerUsername(), "Listing Approved",
                "Your listing '" + listing.getTitle() + "' is now live!", NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Rejects a listing, records the reason and notifies the seller.
     */
    public synchronized void rejectListing(User admin, String listingId, String reason) {
        requireAdmin(admin);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Rejection reason is required.");
        Listing listing = listings.get(listingId);
        if (listing == null) throw new IllegalArgumentException("Listing not found.");
        if (listing.getStatus() != ListingStatus.PENDING_REVIEW) {
            throw new IllegalStateException("Only pending listings can be rejected.");
        }
        listing.setStatus(ListingStatus.REJECTED);
        listing.setRejectionReason(reason.trim());
        listing.setReviewedAt(LocalDateTime.now());
        updateListing(listing);
        addNotification(new Notification(listing.getSellerUsername(), "Listing Rejected",
                "Your listing '" + listing.getTitle() + "' was rejected. Reason: " + reason.trim(),
                NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Removes a listing and cancels its active offers.
     */
    public synchronized void takeDownListing(User admin, String listingId, String reason) {
        requireAdmin(admin);
        if (reason == null || reason.isBlank()) throw new IllegalArgumentException("Removal reason is required.");
        Listing listing = listings.get(listingId);
        if (listing == null) throw new IllegalArgumentException("Listing not found.");
        if (listing.getStatus() == ListingStatus.REMOVED) {
            throw new IllegalStateException("This listing has already been removed.");
        }
        listing.setStatus(ListingStatus.REMOVED);
        listing.setRejectionReason(reason.trim());
        listing.setReviewedAt(LocalDateTime.now());
        updateListing(listing);
        addNotification(new Notification(listing.getSellerUsername(), "Listing Removed",
                "Your listing '" + listing.getTitle() + "' was removed by the admin: " + reason.trim(),
                NotificationCategories.SYSTEM));
        notifyBuyersWithActiveOffers(listingId, reason);
        persist();
    }

    /**
     * Creates a live listing on behalf of a valid active seller.
     */
    public synchronized Listing addListingAsAdmin(User admin, Listing listing) {
        requireAdmin(admin);
        validateListing(listing);
        User seller = findUser(listing.getSellerUsername());
        if (seller == null || seller.getRole() != UserRole.SELLER) {
            throw new IllegalArgumentException("A valid seller account is required.");
        }
        if (!seller.isActive()) {
            throw new IllegalArgumentException("The seller account is deactivated.");
        }
        listing.setSellerFullName(seller.getFullName());
        listing.setStatus(ListingStatus.LIVE);
        listing.setPostedAt(LocalDateTime.now());
        listing.setReviewedAt(LocalDateTime.now());
        if (listing.getType() == ListingType.LEND) {
            listing.setPrice(null);
        } else if (listing.getType() == ListingType.GIVEAWAY) {
            listing.setPrice(null);
            listing.setLendingDays(null);
        } else {
            listing.setLendingDays(null);
        }
        addListing(listing);
        addNotification(new Notification(seller.getUsername(), "Listing Added by Admin",
                "An admin added '" + listing.getTitle() + "' on your behalf.", NotificationCategories.SYSTEM));
        persist();
        return listing;
    }

    /**
     * Updates a listing from the administrator portal.
     */
    public synchronized void updateListingAsAdmin(User admin, Listing updated) {
        requireAdmin(admin);
        Listing existing = listings.get(updated.getId());
        if (existing == null) throw new IllegalArgumentException("Listing not found.");
        if (existing.getStatus() == ListingStatus.REMOVED) {
            throw new IllegalStateException("Removed listings cannot be edited.");
        }
        if (existing.getStatus() == ListingStatus.REJECTED) {
            throw new IllegalStateException("Rejected listings must be edited by the seller and resubmitted.");
        }
        if (hasActiveDeals(existing.getId())
                || existing.getStatus() == ListingStatus.RESERVED
                || existing.getStatus() == ListingStatus.SOLD
                || existing.getStatus() == ListingStatus.LENT
                || existing.getStatus() == ListingStatus.GIVEN) {
            throw new IllegalStateException("This listing cannot be edited while a deal is active.");
        }
        validateListing(updated);
        existing.setTitle(updated.getTitle().trim());
        existing.setDescription(updated.getDescription());
        existing.setCategory(updated.getCategory());
        existing.setType(updated.getType());
        existing.setCondition(updated.getCondition());
        existing.setPrice(updated.getType() == ListingType.SELL ? updated.getPrice() : null);
        existing.setLendingDays(updated.getType() == ListingType.LEND ? updated.getLendingDays() : null);
        existing.setMeetupLocation(updated.getMeetupLocation());
        existing.setPhotoPaths(updated.getPhotoPaths());
        updateListing(existing);
        addNotification(new Notification(existing.getSellerUsername(), "Listing Updated by Admin",
                "An admin updated '" + existing.getTitle() + "'.", NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Updates a buyer or seller account from the administrator portal.
     */
    public synchronized void updateUserAsAdmin(User admin, String username, String fullName,
                                               String residenceHall, String bio, String preferredMeetup) {
        requireAdmin(admin);
        User user = findUser(username);
        if (user == null || user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("User not found.");
        }
        if (fullName == null || fullName.isBlank()) throw new IllegalArgumentException("Full Name cannot be empty.");
        if (user.getRole() == UserRole.BUYER) {
            String normalizedResidence = normalizeBuyerResidence(residenceHall);
            user.setFullName(fullName.trim());
            user.setResidenceHall(normalizedResidence);
            user.setBio(null);
            user.setPreferredMeetup(null);
        } else {
            user.setFullName(fullName.trim());
            user.setResidenceHall(null);
            user.setBio(bio == null || bio.isBlank() ? null : bio.trim());
            user.setPreferredMeetup(preferredMeetup);
            syncSellerDisplayName(user.getUsername(), user.getFullName());
        }
        persist();
    }

    private void syncSellerDisplayName(String username, String fullName) {
        for (Listing listing : getListingsBySeller(username)) {
            listing.setSellerFullName(fullName);
        }
    }

    /**
     * Activates or deactivates an account and closes affected deals.
     */
    public synchronized void setUserActiveAsAdmin(User admin, String username, boolean active) {
        requireAdmin(admin);
        User user = findUser(username);
        if (user == null || user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("User not found.");
        }
        if (!active && user.isActive()) {
            closeDealsForUser(username, "deactivated");
            if (user.getRole() == UserRole.SELLER) {
                takeDownSellerListings(username, "Seller account deactivated by admin");
            }
        }
        user.setActive(active);
        persist();
    }

    /**
     * Permanently deletes a buyer or seller and their active commitments.
     */
    public synchronized void deleteUserAsAdmin(User admin, String username) {
        requireAdmin(admin);
        User user = findUser(username);
        if (user == null || user.getRole() == UserRole.ADMIN) {
            throw new IllegalArgumentException("User not found.");
        }
        closeDealsForUser(username, "deleted");
        if (user.getRole() == UserRole.SELLER) {
            takeDownSellerListings(username, "Seller account deleted by admin");
        }
        users.remove(username.toLowerCase());
        addNotification(new Notification("admin", "User Deleted",
                "Account @" + username + " was permanently deleted.", NotificationCategories.SYSTEM));
        persist();
    }

    private static boolean isDealActive(Deal deal) {
        return DealStatuses.PENDING.equals(deal.getStatus())
                || DealStatuses.COUNTERED.equals(deal.getStatus())
                || DealStatuses.ACCEPTED.equals(deal.getStatus());
    }

    private void closeDealsForUser(String username, String action) {
        for (Deal deal : getDealsForBuyer(username)) {
            closeBuyerDeal(deal, action);
        }
        for (Deal deal : getDealsForSeller(username)) {
            closeSellerDeal(deal, action);
        }
    }

    private void closeBuyerDeal(Deal deal, String action) {
        if (!isDealActive(deal)) {
            return;
        }
        boolean wasAcceptedLend = DealStatuses.ACCEPTED.equals(deal.getStatus())
                && deal.getDealType() == ListingType.LEND;
        deal.setStatus(wasAcceptedLend ? DealStatuses.RETURNED : DealStatuses.CANCELLED);
        deal.setCompletedAt(LocalDateTime.now());
        releaseListingIfNoActiveDeals(deal.getListingId());
        addNotification(new Notification(deal.getSellerUsername(), wasAcceptedLend ? "Deal Closed" : "Buyer Account Removed",
                "The buyer's account was " + action + ". The deal for '" + deal.getListingTitle()
                        + "' was " + (wasAcceptedLend ? "returned and closed." : "cancelled."),
                NotificationCategories.SYSTEM));
    }

    private void closeSellerDeal(Deal deal, String action) {
        if (!isDealActive(deal)) {
            return;
        }
        boolean wasAcceptedLend = DealStatuses.ACCEPTED.equals(deal.getStatus())
                && deal.getDealType() == ListingType.LEND;
        deal.setStatus(wasAcceptedLend ? DealStatuses.RETURNED : DealStatuses.CANCELLED);
        deal.setCompletedAt(LocalDateTime.now());
        addNotification(new Notification(deal.getBuyerUsername(), wasAcceptedLend ? "Deal Closed" : "Seller Account Removed",
                "The seller's account was " + action + ". The deal for '" + deal.getListingTitle()
                        + "' was " + (wasAcceptedLend ? "returned and closed." : "cancelled."),
                NotificationCategories.SYSTEM));
    }

    private void takeDownSellerListings(String sellerUsername, String reason) {
        for (Listing listing : getListingsBySeller(sellerUsername)) {
            if (listing.getStatus() == ListingStatus.PENDING_REVIEW
                    || listing.getStatus() == ListingStatus.LIVE
                    || listing.getStatus() == ListingStatus.RESERVED
                    || listing.getStatus() == ListingStatus.LENT
                    || listing.getStatus() == ListingStatus.REJECTED) {
                listing.setStatus(ListingStatus.REMOVED);
                listing.setRejectionReason(reason);
            }
        }
    }

    private void releaseListingIfNoActiveDeals(String listingId) {
        Listing listing = listings.get(listingId);
        if (listing != null
                && (listing.getStatus() == ListingStatus.RESERVED
                || listing.getStatus() == ListingStatus.LENT)
                && !hasActiveDeals(listingId)) {
            listing.setStatus(ListingStatus.LIVE);
        }
    }

    public void addListing(Listing listing) {
        if (listing.getId() == null) {
            listing.setId("L" + listingIdCounter.getAndIncrement());
        }
        listings.put(listing.getId(), listing);
        persist();
    }

    public void updateListing(Listing listing) {
        listings.put(listing.getId(), listing);
        persist();
    }

    public void removeListing(String id) {
        listings.remove(id);
        persist();
    }

    // ========== Deal methods ==========
    public List<Deal> getDealsForBuyer(String username) {
        return deals.stream()
                .filter(d -> d.getBuyerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Deal> getAllDeals() {
        return new ArrayList<>(deals);
    }

    public void addDeal(Deal deal) {
        if (deal.getId() == null) {
            deal.setId("D" + dealIdCounter.getAndIncrement());
        }
        deals.add(deal);
        persist();
    }

    public synchronized DealExtras extras(String dealId) {
        return dealExtras.computeIfAbsent(dealId, key -> new DealExtras());
    }

    public synchronized DealExtras getDealExtras(String dealId) {
        return extras(dealId);
    }

    public synchronized DealExtras findDealExtras(String dealId) {
        return dealExtras.get(dealId);
    }

    public Deal findDeal(String dealId) {
        return deals.stream()
                .filter(d -> dealId != null && dealId.equals(d.getId()))
                .findFirst().orElse(null);
    }

    public List<Deal> getDealsForSeller(String username) {
        return deals.stream()
                .filter(d -> d.getSellerUsername().equalsIgnoreCase(username))
                .collect(Collectors.toList());
    }

    public List<Deal> getDealsForListing(String listingId) {
        return deals.stream()
                .filter(d -> listingId != null && listingId.equals(d.getListingId()))
                .collect(Collectors.toList());
    }

    private Listing requireAvailableListing(String listingId) {
        Listing listing = listings.get(listingId);
        if (listing == null) {
            throw new IllegalArgumentException("Listing not found.");
        }
        if (listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException("This listing is no longer available.");
        }
        return listing;
    }

    private static void validateBorrowPeriod(Listing listing, LocalDateTime start, LocalDateTime end) {
        if (start == null || end == null) {
            throw new IllegalArgumentException("Please choose start and end time.");
        }
        if (end.isBefore(start.plusSeconds(5))) {
            throw new IllegalArgumentException("Borrow period must be at least 5 seconds.");
        }
        if (listing != null && listing.getLendingDays() != null
                && end.isAfter(start.plusDays(listing.getLendingDays()))) {
            throw new IllegalArgumentException("Borrow period cannot exceed "
                    + listing.getLendingDays() + " day(s).");
        }
    }

    private static void validateBorrowRequest(Listing listing, LocalDateTime start, LocalDateTime end) {
        validateBorrowPeriod(listing, start, end);
        LocalDateTime now = LocalDateTime.now();
        if (start.isBefore(now.minusMinutes(5))) {
            throw new IllegalArgumentException("Borrow start time cannot be in the past.");
        }
        if (!end.isAfter(now)) {
            throw new IllegalArgumentException("Borrow end time must be in the future.");
        }
    }

    private static void validateBorrowAcceptance(Listing listing, LocalDateTime start, LocalDateTime end) {
        validateBorrowPeriod(listing, start, end);
        if (!end.isAfter(LocalDateTime.now())) {
            throw new IllegalArgumentException("The proposed borrow period has already ended.");
        }
    }

    private Deal requireOwnDeal(User actor, String dealId) {
        Deal deal = findDeal(dealId);
        if (deal == null) {
            throw new IllegalArgumentException("Deal not found.");
        }
        if (!deal.getBuyerUsername().equalsIgnoreCase(actor.getUsername())) {
            throw new SecurityException("This deal does not belong to you.");
        }
        return deal;
    }

    private Deal findActiveDealForBuyer(String listingId, String buyerUsername) {
        return deals.stream()
                .filter(deal -> listingId != null && listingId.equals(deal.getListingId())
                        && buyerUsername != null
                        && buyerUsername.equalsIgnoreCase(deal.getBuyerUsername())
                        && (DealStatuses.PENDING.equals(deal.getStatus())
                        || DealStatuses.COUNTERED.equals(deal.getStatus())
                        || DealStatuses.ACCEPTED.equals(deal.getStatus())))
                .findFirst().orElse(null);
    }

    private void requireNoDuplicateActiveDeal(String listingId, String buyerUsername) {
        if (findActiveDealForBuyer(listingId, buyerUsername) != null) {
            throw new IllegalStateException("You already have an active offer for this listing.");
        }
    }

    private void finalizeAcceptedDeal(Deal accepted, Listing listing) {
        accepted.setStatus(DealStatuses.ACCEPTED);
        if (accepted.getDealType() == ListingType.LEND) {
            listing.setStatus(ListingStatus.LENT);
        } else {
            listing.setStatus(ListingStatus.RESERVED);
        }
        for (Deal other : deals) {
            if (other == accepted
                    || !listing.getId().equals(other.getListingId())
                    || (!DealStatuses.PENDING.equals(other.getStatus())
                    && !DealStatuses.COUNTERED.equals(other.getStatus()))) {
                continue;
            }
            other.setStatus(DealStatuses.DECLINED);
            addNotification(new Notification(other.getBuyerUsername(), "Offer Declined",
                    "The seller accepted another offer for '" + other.getListingTitle() + "'.",
                    NotificationCategories.OFFER));
        }
    }

    private Deal createDeal(Listing listing, String buyerUsername) {
        Deal deal = new Deal();
        deal.setListingId(listing.getId());
        deal.setListingTitle(listing.getTitle());
        deal.setBuyerUsername(buyerUsername.toLowerCase());
        deal.setSellerUsername(listing.getSellerUsername());
        deal.setDealType(listing.getType());
        deal.setStatus(DealStatuses.PENDING);
        addDeal(deal);
        extras(deal.getId()).listingCategory = listing.getCategory();
        return deal;
    }

    /**
     * Submits a buy offer for a live sale listing.
     */
    public synchronized Deal submitOffer(User actor, String listingId, double price) {
        requireBuyer(actor);
        Listing listing = requireAvailableListing(listingId);
        if (listing.getType() != ListingType.SELL) {
            throw new IllegalArgumentException("This listing is not for sale.");
        }
        requireNoDuplicateActiveDeal(listing.getId(), actor.getUsername());
        if (!PriceUtil.isValidAmount(price)) {
            throw new IllegalArgumentException(
                    "Offer price must be greater than 0 and have at most 2 decimal places.");
        }
        if (listing.getPrice() == null || !PriceUtil.isValidAmount(listing.getPrice())) {
            throw new IllegalStateException("The asking price for this listing is invalid.");
        }
        if (price >= listing.getPrice()) {
            throw new IllegalArgumentException(
                    "Offer price must be below the asking price. Use Buy Now to pay "
                            + PriceUtil.format(listing.getPrice()) + ".");
        }
        Deal deal = createDeal(listing, actor.getUsername());
        deal.setOfferPrice(price);
        addNotification(new Notification(listing.getSellerUsername(), "New Offer Received",
                actor.getFullName() + " offered HK$" + price + " for '" + listing.getTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
        return deal;
    }

    /**
     * Purchases a live sale listing immediately at its asking price.
     */
    public synchronized Deal buyNow(User actor, String listingId) {
        requireBuyer(actor);
        Listing listing = requireAvailableListing(listingId);
        if (listing.getType() != ListingType.SELL) {
            throw new IllegalArgumentException("This listing is not for sale.");
        }
        if (listing.getPrice() == null || !PriceUtil.isValidAmount(listing.getPrice())) {
            throw new IllegalStateException("The asking price for this listing is invalid.");
        }
        Deal deal = findActiveDealForBuyer(listing.getId(), actor.getUsername());
        if (deal != null && DealStatuses.ACCEPTED.equals(deal.getStatus())) {
            throw new IllegalStateException("You already purchased this listing.");
        }
        if (deal == null) {
            deal = createDeal(listing, actor.getUsername());
        } else {
            DealExtras extra = extras(deal.getId());
            extra.counterPrice = null;
            extra.counterBorrowStartAt = null;
            extra.counterBorrowEndAt = null;
            extra.counterMessage = null;
        }
        deal.setOfferPrice(listing.getPrice());
        finalizeAcceptedDeal(deal, listing);
        addNotification(new Notification(listing.getSellerUsername(), "Buy Now Purchase",
                actor.getFullName() + " purchased '" + listing.getTitle() + "' at HK$"
                        + PriceUtil.format(listing.getPrice()) + ".",
                NotificationCategories.OFFER));
        persist();
        return deal;
    }

    /**
     * Submits a borrow request with a validated time window.
     */
    public synchronized Deal submitBorrowRequest(User actor, String listingId,
                                                 LocalDateTime start, LocalDateTime end) {
        requireBuyer(actor);
        Listing listing = requireAvailableListing(listingId);
        if (listing.getType() != ListingType.LEND) {
            throw new IllegalArgumentException("This listing is not for lending.");
        }
        requireNoDuplicateActiveDeal(listing.getId(), actor.getUsername());
        validateBorrowRequest(listing, start, end);
        Deal deal = createDeal(listing, actor.getUsername());
        deal.setBorrowStart(start.toLocalDate());
        deal.setBorrowEnd(end.toLocalDate());
        extras(deal.getId()).borrowStartAt = start;
        extras(deal.getId()).borrowEndAt = end;
        addNotification(new Notification(listing.getSellerUsername(), "New Borrow Request",
                actor.getFullName() + " requested to borrow '" + listing.getTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
        return deal;
    }

    /**
     * Claims a live giveaway listing for a buyer.
     */
    public synchronized Deal claimGiveaway(User actor, String listingId) {
        requireBuyer(actor);
        Listing listing = requireAvailableListing(listingId);
        if (listing.getType() != ListingType.GIVEAWAY) {
            throw new IllegalArgumentException("This listing is not a giveaway.");
        }
        requireNoDuplicateActiveDeal(listing.getId(), actor.getUsername());
        listing.setStatus(ListingStatus.RESERVED);
        Deal deal = createDeal(listing, actor.getUsername());
        addNotification(new Notification(listing.getSellerUsername(), "New Claim",
                actor.getFullName() + " claimed '" + listing.getTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
        return deal;
    }

    /**
     * Accepts the seller's counter-offer for a deal.
     */
    public synchronized void acceptCounterOffer(User actor, String dealId) {
        requireBuyer(actor);
        Deal deal = requireOwnDeal(actor, dealId);
        if (!DealStatuses.COUNTERED.equals(deal.getStatus())) {
            throw new IllegalStateException("There is no counter-offer to accept.");
        }
        Listing listing = listings.get(deal.getListingId());
        if (listing == null || listing.getStatus() != ListingStatus.LIVE) {
            throw new IllegalStateException("This listing is no longer available.");
        }
        DealExtras extra = extras(dealId);
        if (deal.getDealType() == ListingType.SELL) {
            if (extra.counterPrice == null || !PriceUtil.isValidAmount(extra.counterPrice)) {
                throw new IllegalStateException("Counter-offer price is missing or invalid.");
            }
            deal.setOfferPrice(extra.counterPrice);
        } else if (deal.getDealType() == ListingType.LEND) {
            try {
                validateBorrowAcceptance(listing,
                        extra.counterBorrowStartAt, extra.counterBorrowEndAt);
            } catch (IllegalArgumentException ex) {
                throw new IllegalStateException("Counter-offer dates are invalid: " + ex.getMessage());
            }
            extra.borrowStartAt = extra.counterBorrowStartAt;
            extra.borrowEndAt = extra.counterBorrowEndAt;
            deal.setBorrowStart(extra.borrowStartAt.toLocalDate());
            deal.setBorrowEnd(extra.borrowEndAt.toLocalDate());
        }
        finalizeAcceptedDeal(deal, listing);
        addNotification(new Notification(deal.getSellerUsername(), "Counter-Offer Accepted",
                actor.getFullName() + " accepted your counter-offer for '" + deal.getListingTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
    }

    /**
     * Declines the seller's counter-offer for a deal.
     */
    public synchronized void declineCounterOffer(User actor, String dealId) {
        requireBuyer(actor);
        Deal deal = requireOwnDeal(actor, dealId);
        if (!DealStatuses.COUNTERED.equals(deal.getStatus())) {
            throw new IllegalStateException("There is no counter-offer to decline.");
        }
        deal.setStatus(DealStatuses.DECLINED);
        addNotification(new Notification(deal.getSellerUsername(), "Counter-Offer Declined",
                actor.getFullName() + " declined your counter-offer for '" + deal.getListingTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
    }

    /**
     * Marks an accepted borrow deal as returned by the buyer.
     */
    public synchronized void selfReturn(User actor, String dealId) {
        requireBuyer(actor);
        Deal deal = requireOwnDeal(actor, dealId);
        if (deal.getDealType() != ListingType.LEND
                || !DealStatuses.ACCEPTED.equals(deal.getStatus())) {
            throw new IllegalStateException("This deal cannot be returned.");
        }
        LocalDateTime due = extras(dealId).borrowEndAt != null
                ? extras(dealId).borrowEndAt
                : (deal.getBorrowEnd() == null ? null : deal.getBorrowEnd().atTime(23, 59, 59));
        if (due != null && LocalDateTime.now().isAfter(due)) {
            throw new IllegalStateException("The due date has already passed.");
        }
        deal.setStatus(DealStatuses.RETURNED);
        deal.setCompletedAt(LocalDateTime.now());
        Listing listing = listings.get(deal.getListingId());
        if (listing != null && listing.getStatus() == ListingStatus.LENT) {
            listing.setStatus(ListingStatus.LIVE);
        }
        addNotification(new Notification(deal.getSellerUsername(), "Item Returned",
                actor.getFullName() + " returned '" + deal.getListingTitle() + "'.",
                NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Completes an accepted sale or giveaway deal.
     */
    public synchronized void completeDeal(User actor, String dealId) {
        requireBuyer(actor);
        Deal deal = requireOwnDeal(actor, dealId);
        if (!DealStatuses.ACCEPTED.equals(deal.getStatus())) {
            throw new IllegalStateException("This deal is not active.");
        }
        if (deal.getDealType() == ListingType.LEND) {
            throw new IllegalStateException("Use self-return for borrowed items.");
        }
        deal.setStatus(DealStatuses.COMPLETED);
        deal.setCompletedAt(LocalDateTime.now());
        Listing listing = listings.get(deal.getListingId());
        if (listing != null) {
            listing.setStatus(deal.getDealType() == ListingType.SELL
                    ? ListingStatus.SOLD : ListingStatus.GIVEN);
        }
        addNotification(new Notification(deal.getSellerUsername(), "Deal Completed",
                actor.getFullName() + " marked '" + deal.getListingTitle() + "' as complete.",
                NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Automatically returns accepted borrows and cancels expired requests.
     */
    public synchronized int processExpiredBorrows() {
        int changed = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Deal deal : deals) {
            if (deal.getDealType() != ListingType.LEND) {
                continue;
            }
            DealExtras extra = extras(deal.getId());
            if (DealStatuses.ACCEPTED.equals(deal.getStatus())) {
                LocalDateTime due = extra.borrowEndAt != null
                        ? extra.borrowEndAt
                        : (deal.getBorrowEnd() == null ? null : deal.getBorrowEnd().atTime(23, 59, 59));
                if (due == null || now.isBefore(due)) {
                    continue;
                }
                deal.setStatus(DealStatuses.RETURNED);
                deal.setCompletedAt(now);
                Listing listing = listings.get(deal.getListingId());
                if (listing != null && listing.getStatus() == ListingStatus.LENT) {
                    listing.setStatus(ListingStatus.LIVE);
                }
                addNotification(new Notification(deal.getBuyerUsername(), "Borrow Auto-Returned",
                        "'" + deal.getListingTitle() + "' was returned automatically.",
                        NotificationCategories.DUE));
                addNotification(new Notification(deal.getSellerUsername(), "Item Available Again",
                        "'" + deal.getListingTitle() + "' is available again.",
                        NotificationCategories.DUE));
                changed++;
            } else if (DealStatuses.PENDING.equals(deal.getStatus())
                    || DealStatuses.COUNTERED.equals(deal.getStatus())) {
                LocalDateTime due = DealStatuses.COUNTERED.equals(deal.getStatus())
                        && extra.counterBorrowEndAt != null
                        ? extra.counterBorrowEndAt
                        : (extra.borrowEndAt != null
                        ? extra.borrowEndAt
                        : (deal.getBorrowEnd() == null ? null : deal.getBorrowEnd().atTime(23, 59, 59)));
                if (due == null || now.isBefore(due)) {
                    continue;
                }
                deal.setStatus(DealStatuses.CANCELLED);
                addNotification(new Notification(deal.getBuyerUsername(), "Borrow Request Expired",
                        "'" + deal.getListingTitle() + "' expired before it was accepted. "
                                + "You can submit a new request.",
                        NotificationCategories.DUE));
                addNotification(new Notification(deal.getSellerUsername(), "Borrow Request Expired",
                        "The borrow request from @" + deal.getBuyerUsername() + " for '"
                                + deal.getListingTitle() + "' expired.",
                        NotificationCategories.DUE));
                changed++;
            }
        }
        if (changed > 0) {
            persist();
        }
        return changed;
    }

    /**
     * Sends one due-soon reminder for accepted borrow deals.
     */
    public synchronized int processDueReminders() {
        int sent = 0;
        LocalDateTime now = LocalDateTime.now();
        for (Deal deal : deals) {
            if (deal.getDealType() != ListingType.LEND
                    || !DealStatuses.ACCEPTED.equals(deal.getStatus())) {
                continue;
            }
            DealExtras extra = extras(deal.getId());
            if (extra.dueReminderSent) {
                continue;
            }
            LocalDateTime due = extra.borrowEndAt != null
                    ? extra.borrowEndAt
                    : (deal.getBorrowEnd() == null ? null : deal.getBorrowEnd().atTime(23, 59, 59));
            if (due == null || !now.isBefore(due)) {
                continue;
            }
            if (!now.isBefore(due.minusHours(24))) {
                addNotification(new Notification(deal.getBuyerUsername(), "Borrow Due Soon",
                        "'" + deal.getListingTitle() + "' is due at " + due + ".",
                        NotificationCategories.DUE));
                extra.dueReminderSent = true;
                sent++;
            }
        }
        if (sent > 0) {
            persist();
        }
        return sent;
    }

    /**
     * Accepts a pending seller offer, borrow request or giveaway claim.
     */
    public synchronized void acceptDeal(User seller, String dealId) {
        requireRole(seller, UserRole.SELLER);
        Deal deal = findDeal(dealId);
        if (deal == null || !deal.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This deal does not belong to you.");
        }
        if (!DealStatuses.PENDING.equals(deal.getStatus())) {
            throw new IllegalStateException("Only pending deals can be accepted.");
        }
        Listing listing = listings.get(deal.getListingId());
        if (listing == null) {
            throw new IllegalStateException("Listing no longer exists.");
        }
        if (listing.getStatus() != ListingStatus.LIVE
                && !(listing.getStatus() == ListingStatus.RESERVED
                && deal.getDealType() == ListingType.GIVEAWAY)) {
            throw new IllegalStateException("This listing is no longer available.");
        }
        if (deal.getDealType() == ListingType.LEND) {
            DealExtras extra = extras(dealId);
            if (extra.borrowStartAt == null || extra.borrowEndAt == null) {
                LocalDateTime start = LocalDateTime.now();
                LocalDateTime end = start.plusDays(listing.getLendingDays() == null
                        ? 1 : listing.getLendingDays());
                extra.borrowStartAt = start;
                extra.borrowEndAt = end;
                deal.setBorrowStart(start.toLocalDate());
                deal.setBorrowEnd(end.toLocalDate());
            }
            validateBorrowAcceptance(listing, extra.borrowStartAt, extra.borrowEndAt);
        }
        finalizeAcceptedDeal(deal, listing);
        addNotification(new Notification(deal.getBuyerUsername(), "Offer Accepted",
                seller.getFullName() + " accepted your offer for '" + deal.getListingTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
    }

    /**
     * Declines a pending offer or withdraws a counter-offer.
     */
    public synchronized void declineDeal(User seller, String dealId, String reason) {
        requireRole(seller, UserRole.SELLER);
        Deal deal = findDeal(dealId);
        if (deal == null || !deal.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This deal does not belong to you.");
        }
        boolean pending = DealStatuses.PENDING.equals(deal.getStatus());
        boolean countered = DealStatuses.COUNTERED.equals(deal.getStatus());
        if (!pending && !countered) {
            throw new IllegalStateException("Only pending offers or counter-offers can be declined.");
        }
        deal.setStatus(DealStatuses.DECLINED);
        Listing listing = listings.get(deal.getListingId());
        if (listing != null && deal.getDealType() == ListingType.GIVEAWAY
                && listing.getStatus() == ListingStatus.RESERVED) {
            listing.setStatus(ListingStatus.LIVE);
        }
        String action = countered ? "cancelled their counter-offer" : "declined your offer";
        String title = countered ? "Counter-Offer Cancelled" : "Offer Declined";
        addNotification(new Notification(deal.getBuyerUsername(), title,
                seller.getFullName() + " " + action + " for '" + deal.getListingTitle() + "'"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason),
                NotificationCategories.OFFER));
        persist();
    }

    /**
     * Cancels an accepted sale or giveaway and releases the listing.
     */
    public synchronized void cancelAcceptedDeal(User seller, String dealId, String reason) {
        requireSeller(seller);
        Deal deal = findDeal(dealId);
        if (deal == null || !deal.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This deal does not belong to you.");
        }
        if (!DealStatuses.ACCEPTED.equals(deal.getStatus())) {
            throw new IllegalStateException("Only accepted deals can be cancelled.");
        }
        if (deal.getDealType() == ListingType.LEND) {
            throw new IllegalStateException("Accepted borrow deals cannot be cancelled; they must be returned.");
        }
        deal.setStatus(DealStatuses.CANCELLED);
        deal.setCompletedAt(LocalDateTime.now());
        releaseListingIfNoActiveDeals(deal.getListingId());
        addNotification(new Notification(deal.getBuyerUsername(), "Deal Cancelled",
                seller.getFullName() + " cancelled the deal for '" + deal.getListingTitle() + "'"
                        + (reason == null || reason.isBlank() ? "." : ": " + reason),
                NotificationCategories.SYSTEM));
        persist();
    }

    /**
     * Sends a counter-offer for a pending sale or borrow deal.
     */
    public synchronized void counterDeal(User seller, String dealId, Double price,
                                         LocalDateTime start, LocalDateTime end,
                                         String message) {
        requireRole(seller, UserRole.SELLER);
        Deal deal = findDeal(dealId);
        if (deal == null || !deal.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This deal does not belong to you.");
        }
        if (!DealStatuses.PENDING.equals(deal.getStatus())) {
            throw new IllegalStateException("Only pending deals can be countered.");
        }
        if (deal.getDealType() == ListingType.GIVEAWAY) {
            throw new IllegalStateException("Giveaway claims cannot be countered.");
        }
        if (deal.getDealType() == ListingType.SELL) {
            Listing listing = listings.get(deal.getListingId());
            if (listing == null) {
                throw new IllegalStateException("Listing no longer exists.");
            }
            if (price == null || !PriceUtil.isValidAmount(price)) {
                throw new IllegalArgumentException(
                        "Counter price must be greater than 0 and have at most 2 decimal places.");
            }
            if (deal.getOfferPrice() == null) {
                throw new IllegalStateException("The buyer's offer price is missing.");
            }
            if (price <= deal.getOfferPrice()) {
                throw new IllegalArgumentException(
                        "Counter price must be higher than the buyer's offer. Use Accept instead.");
            }
            if (listing.getPrice() == null || !PriceUtil.isValidAmount(listing.getPrice())) {
                throw new IllegalStateException("The asking price for this listing is invalid.");
            }
            if (price > listing.getPrice()) {
                throw new IllegalArgumentException(
                        "Counter price cannot exceed the asking price of "
                                + PriceUtil.format(listing.getPrice()) + ".");
            }
        } else {
            validateBorrowAcceptance(listings.get(deal.getListingId()), start, end);
        }
        deal.setStatus(DealStatuses.COUNTERED);
        DealExtras extra = extras(dealId);
        extra.counterPrice = price;
        extra.counterBorrowStartAt = start;
        extra.counterBorrowEndAt = end;
        extra.counterMessage = message;
        addNotification(new Notification(deal.getBuyerUsername(), "Offer Countered",
                seller.getFullName() + " countered your offer for '" + deal.getListingTitle() + "'.",
                NotificationCategories.OFFER));
        persist();
    }

    /**
     * Cancels active offers and notifies buyers after a listing removal.
     */
    public synchronized void notifyBuyersWithActiveOffers(String listingId, String reason) {
        boolean changed = false;
        for (Deal deal : deals) {
            if (!listingId.equals(deal.getListingId())
                    || (!DealStatuses.PENDING.equals(deal.getStatus())
                    && !DealStatuses.COUNTERED.equals(deal.getStatus())
                    && !DealStatuses.ACCEPTED.equals(deal.getStatus()))) {
                continue;
            }
            deal.setStatus(DealStatuses.CANCELLED);
            changed = true;
            addNotification(new Notification(deal.getBuyerUsername(), "Listing Removed",
                    "'" + deal.getListingTitle() + "' was removed by the admin"
                            + (reason == null || reason.isBlank() ? "." : ": " + reason),
                    NotificationCategories.SYSTEM));
        }
        if (changed) {
            persist();
        }
    }

    // ========== Notification methods ==========
    public List<Notification> getNotificationsFor(String username) {
        return notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Notification::getTimestamp).reversed())
                .collect(Collectors.toList());
    }

    public synchronized void addNotification(Notification n) {
        if (n.getId() == null || n.getId().isBlank()) {
            n.setId("N" + notifIdCounter.getAndIncrement());
        }
        notifications.add(n);
    }

    /**
     * Marks one notification as read when it exists and is unread.
     */
    public synchronized void markNotificationRead(String notificationId) {
        Notification notification = notifications.stream()
                .filter(n -> notificationId != null && notificationId.equals(n.getId()))
                .findFirst()
                .orElse(null);
        if (notification != null && !notification.isRead()) {
            notification.setRead(true);
            persist();
        }
    }

    /**
     * Marks every unread notification for a user as read.
     */
    public synchronized void markAllNotificationsRead(String username) {
        boolean changed = false;
        for (Notification notification : notifications) {
            if (notification.getUsername().equalsIgnoreCase(username) && !notification.isRead()) {
                notification.setRead(true);
                changed = true;
            }
        }
        if (changed) {
            persist();
        }
    }

    public int getUnreadNotificationCount(String username) {
        return (int) notifications.stream()
                .filter(n -> n.getUsername().equalsIgnoreCase(username) && !n.isRead())
                .count();
    }

    // ========== Chat methods ==========
    private static String chatKey(String listingId, String buyerUsername) {
        return listingId + "|" + buyerUsername.toLowerCase();
    }

    /**
     * Returns the chat thread for a listing/buyer pair, creating it if needed.
     */
    public synchronized ChatThreadInfo getOrCreateChatThread(String listingId, String buyerUsername) {
        Listing listing = listings.get(listingId);
        if (listing == null) {
            throw new IllegalArgumentException("Listing not found.");
        }
        String key = chatKey(listingId, buyerUsername);
        ChatThreadInfo existing = chatThreads.get(key);
        if (existing != null) {
            return existing;
        }
        ChatThreadInfo thread = new ChatThreadInfo("T" + chatThreadIdCounter.getAndIncrement(),
                listingId, listing.getTitle(), buyerUsername.toLowerCase(),
                listing.getSellerUsername(), null);
        chatThreads.put(key, thread);
        chatThreadsById.put(thread.id(), thread);
        chatMessages.put(thread.id(), new ArrayList<>());
        persist();
        return thread;
    }

    /**
     * Returns a chat thread for a deal and creates one when absent.
     */
    public synchronized ChatThreadInfo getChatThreadForDeal(Deal deal) {
        ChatThreadInfo existing = chatThreads.get(chatKey(deal.getListingId(), deal.getBuyerUsername()));
        if (existing != null) {
            return existing;
        }
        Listing listing = listings.get(deal.getListingId());
        if (listing != null) {
            return getOrCreateChatThread(listing.getId(), deal.getBuyerUsername());
        }
        ChatThreadInfo thread = new ChatThreadInfo("T" + chatThreadIdCounter.getAndIncrement(),
                deal.getListingId(), deal.getListingTitle(), deal.getBuyerUsername().toLowerCase(),
                deal.getSellerUsername(), null);
        chatThreads.put(chatKey(deal.getListingId(), deal.getBuyerUsername()), thread);
        chatThreadsById.put(thread.id(), thread);
        chatMessages.put(thread.id(), new ArrayList<>());
        persist();
        return thread;
    }

    public List<ChatMessage> getChatMessages(String threadId) {
        return chatMessages.getOrDefault(threadId, new ArrayList<>()).stream()
                .sorted(Comparator.comparing(ChatMessage::createdAt))
                .collect(Collectors.toList());
    }

    /**
     * Sends a validated chat message from a participant and notifies the recipient.
     */
    public synchronized ChatMessage sendMessage(User actor, String threadId, String text) {
        ChatThreadInfo thread = chatThreadsById.get(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Chat thread not found.");
        }
        if (actor == null || (!actor.getUsername().equalsIgnoreCase(thread.buyerUsername())
                && !actor.getUsername().equalsIgnoreCase(thread.sellerUsername()))) {
            throw new SecurityException("You are not a participant in this chat.");
        }
        if (text == null || text.isBlank()) {
            throw new IllegalArgumentException("Message cannot be empty.");
        }
        if (text.length() > 2000) {
            throw new IllegalArgumentException("Message is too long (max 2000 characters).");
        }

        ChatMessage message = new ChatMessage("M" + chatMessageIdCounter.getAndIncrement(),
                threadId, actor.getUsername(), text.trim(), "Text", null, LocalDateTime.now());
        chatMessages.computeIfAbsent(threadId, key -> new ArrayList<>()).add(message);

        ChatThreadInfo updated = new ChatThreadInfo(thread.id(), thread.listingId(),
                thread.listingTitle(), thread.buyerUsername(), thread.sellerUsername(),
                message.createdAt());
        chatThreads.put(chatKey(thread.listingId(), thread.buyerUsername()), updated);
        chatThreadsById.put(threadId, updated);

        String recipient = actor.getUsername().equalsIgnoreCase(thread.buyerUsername())
                ? thread.sellerUsername() : thread.buyerUsername();
        addNotification(new Notification(recipient, "New Chat Message",
                actor.getFullName() + ": " + text.trim(), NotificationCategories.CHAT));
        persist();
        return message;
    }

    /**
     * Searches messages in a thread by case-insensitive content.
     */
    public List<ChatMessage> searchMessages(String threadId, String keyword) {
        String kw = keyword == null ? "" : keyword.trim().toLowerCase();
        return getChatMessages(threadId).stream()
                .filter(m -> kw.isEmpty() || m.content().toLowerCase().contains(kw))
                .collect(Collectors.toList());
    }

    /**
     * Submits a formal sale or borrow offer from a chat thread.
     */
    public synchronized ChatMessage sendFormalOffer(User actor, String threadId,
                                                    Double price, LocalDateTime start,
                                                    LocalDateTime end) {
        ChatThreadInfo thread = chatThreadsById.get(threadId);
        if (thread == null) {
            throw new IllegalArgumentException("Chat thread not found.");
        }
        if (actor == null || (!actor.getUsername().equalsIgnoreCase(thread.buyerUsername())
                && !actor.getUsername().equalsIgnoreCase(thread.sellerUsername()))) {
            throw new SecurityException("You are not a participant in this chat.");
        }
        requireBuyer(actor);
        Listing listing = listings.get(thread.listingId());
        if (listing == null) {
            throw new IllegalStateException("Listing no longer exists.");
        }

        Deal deal;
        String content;
        if (listing.getType() == ListingType.SELL) {
            if (price == null) {
                throw new IllegalArgumentException("Please enter an offer price.");
            }
            deal = submitOffer(actor, listing.getId(), price);
            content = "Formal offer: HK$" + price;
        } else if (listing.getType() == ListingType.LEND) {
            deal = submitBorrowRequest(actor, listing.getId(), start, end);
            content = "Formal borrow request: " + start + " to " + end;
        } else {
            throw new IllegalArgumentException("Giveaways do not need a formal offer.");
        }

        ChatMessage message = new ChatMessage("M" + chatMessageIdCounter.getAndIncrement(),
                threadId, actor.getUsername(), content, "Formal Offer", deal.getId(),
                LocalDateTime.now());
        chatMessages.computeIfAbsent(threadId, key -> new ArrayList<>()).add(message);
        ChatThreadInfo updated = new ChatThreadInfo(thread.id(), thread.listingId(),
                thread.listingTitle(), thread.buyerUsername(), thread.sellerUsername(),
                message.createdAt());
        chatThreads.put(chatKey(thread.listingId(), thread.buyerUsername()), updated);
        chatThreadsById.put(threadId, updated);
        persist();
        return message;
    }

    public List<ChatThreadInfo> getChatThreadsForSeller(String sellerUsername) {
        return chatThreadsById.values().stream()
                .filter(t -> t.sellerUsername().equalsIgnoreCase(sellerUsername))
                .sorted(Comparator.comparing(ChatThreadInfo::lastMessageAt,
                        Comparator.nullsLast(Comparator.reverseOrder())))
                .collect(Collectors.toList());
    }

    // ========== Review methods ==========
    public List<Review> getReviewsForSeller(String username) {
        return reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(username) && !r.isHidden())
                .collect(Collectors.toList());
    }

    public List<Review> getAllReviews() {
        return new ArrayList<>(reviews);
    }

    public void addReview(Review r) {
        if (r.getId() == null) {
            r.setId("R" + reviewIdCounter.getAndIncrement());
        }
        reviews.add(r);
        persist();
    }

    /**
     * Returns whether a buyer may review a completed or returned deal.
     */
    public boolean canReviewDeal(User actor, String dealId) {
        requireBuyer(actor);
        Deal deal = findDeal(dealId);
        return deal != null
                && deal.getBuyerUsername().equalsIgnoreCase(actor.getUsername())
                && (DealStatuses.COMPLETED.equals(deal.getStatus())
                || DealStatuses.RETURNED.equals(deal.getStatus()))
                && !deal.isRated();
    }

    /**
     * Adds a buyer review and recalculates the seller's visible rating.
     */
    public synchronized Review addReview(User actor, String dealId, int rating, String comment) {
        requireBuyer(actor);
        if (!canReviewDeal(actor, dealId)) {
            throw new IllegalStateException("You cannot review this deal.");
        }
        if (rating < 1 || rating > 5) {
            throw new IllegalArgumentException("Rating must be between 1 and 5.");
        }
        if (comment == null || comment.isBlank()) {
            throw new IllegalArgumentException("Please write a short review.");
        }

        Deal deal = findDeal(dealId);
        Review review = new Review();
        review.setId("R" + reviewIdCounter.getAndIncrement());
        review.setSellerUsername(deal.getSellerUsername());
        review.setBuyerUsername(actor.getUsername());
        review.setListingTitle(deal.getListingTitle());
        review.setRating(rating);
        review.setComment(comment.trim());
        review.setCreatedAt(LocalDateTime.now());
        reviews.add(review);

        deal.setRated(true);
        recalculateSellerRating(deal.getSellerUsername());

        addNotification(new Notification(deal.getSellerUsername(), "New Rating Received",
                actor.getFullName() + " rated you " + rating + "/5 for '"
                        + deal.getListingTitle() + "'.",
                NotificationCategories.RATING));
        persist();
        return review;
    }

    public List<Review> getReviewsByBuyer(String username) {
        return reviews.stream()
                .filter(r -> r.getBuyerUsername().equalsIgnoreCase(username))
                .sorted(Comparator.comparing(Review::getCreatedAt).reversed())
                .collect(Collectors.toList());
    }

    /**
     * Hides or restores a review for administrator moderation.
     */
    public synchronized void setReviewHidden(User admin, String reviewId, boolean hidden) {
        requireRole(admin, UserRole.ADMIN);
        Review review = reviews.stream()
                .filter(r -> reviewId.equals(r.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Review not found."));
        review.setHidden(hidden);
        recalculateSellerRating(review.getSellerUsername());
        persist();
    }

    /**
     * Adds a seller reply to a review they own.
     */
    public synchronized void addSellerReply(User seller, String reviewId, String reply) {
        requireRole(seller, UserRole.SELLER);
        Review review = reviews.stream()
                .filter(r -> reviewId.equals(r.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (!review.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This review does not belong to you.");
        }
        if (reply == null || reply.isBlank()) {
            throw new IllegalArgumentException("Reply cannot be empty.");
        }
        review.setSellerReply(reply.trim());
        addNotification(new Notification(review.getBuyerUsername(), "Seller Replied to Your Review",
                seller.getFullName() + " replied: " + reply.trim(),
                NotificationCategories.RATING));
        persist();
    }

    /**
     * Flags a review for administrator attention and notifies the admin account.
     */
    public synchronized void flagReview(User seller, String reviewId, String reason) {
        requireSeller(seller);
        Review review = reviews.stream()
                .filter(r -> reviewId.equals(r.getId()))
                .findFirst().orElseThrow(() -> new IllegalArgumentException("Review not found."));
        if (!review.getSellerUsername().equalsIgnoreCase(seller.getUsername())) {
            throw new SecurityException("This review does not belong to you.");
        }
        addNotification(new Notification("admin", "Review Flagged",
                seller.getFullName() + " flagged a review by @" + review.getBuyerUsername()
                        + (reason == null || reason.isBlank() ? "." : ": " + reason.trim()),
                NotificationCategories.RATING));
        persist();
    }

    private void recalculateSellerRating(String sellerUsername) {
        List<Review> visible = reviews.stream()
                .filter(r -> r.getSellerUsername().equalsIgnoreCase(sellerUsername)
                        && !r.isHidden())
                .collect(Collectors.toList());
        User seller = findUser(sellerUsername);
        if (seller == null) {
            return;
        }
        seller.setRatingCount(visible.size());
        seller.setAverageRating(visible.isEmpty() ? 0.0
                : visible.stream().mapToInt(Review::getRating).average().orElse(0.0));
    }

    /**
     * Returns the password policy result for the supplied value.
     */
    public static String checkPasswordStrength(String password) {
        return PasswordUtil.validate(password);
    }
}