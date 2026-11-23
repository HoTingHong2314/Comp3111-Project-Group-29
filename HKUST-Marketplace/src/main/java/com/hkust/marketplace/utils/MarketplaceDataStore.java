package com.hkust.marketplace.utils;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.hkust.marketplace.models.Deal;
import com.hkust.marketplace.models.Listing;
import com.hkust.marketplace.models.Notification;
import com.hkust.marketplace.models.Review;
import com.hkust.marketplace.models.User;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Persists the whole marketplace state as a JSON file.
 *
 * The default location is {@code %USERPROFILE%\.hkust-marketplace\marketplace-data.json}
 * in the user home folder. It can be overridden with the
 * {@code marketplace.data.file} system property (used by tests).
 */
public final class MarketplaceDataStore {

    public static final int FORMAT_VERSION = 1;

    private final Path file;
    private final ObjectMapper mapper;

    public MarketplaceDataStore(Path file) {
        this.file = file;
        this.mapper = new ObjectMapper();
        this.mapper.registerModule(new JavaTimeModule());
        this.mapper.disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
        this.mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
    }

    /**
     * Creates the application data store, honouring the test override and reset properties.
     */
    public static MarketplaceDataStore defaultStore() {
        String override = System.getProperty("marketplace.data.file");
        Path path = override != null && !override.isBlank()
                ? Path.of(override)
                : Path.of(System.getProperty("user.home"), ".hkust-marketplace", "marketplace-data.json");
        MarketplaceDataStore store = new MarketplaceDataStore(path);
        if (Boolean.getBoolean("marketplace.data.reset")) {
            store.deleteDataFile();
        }
        return store;
    }

    /**
     * Returns the JSON file used by this store.
     */
    public Path getFile() {
        return file;
    }

    private void deleteDataFile() {
        try {
            Files.deleteIfExists(file);
        } catch (IOException e) {
            System.err.println("[MarketplaceDataStore] Could not delete data file: " + e.getMessage());
        }
    }

    /**
     * Returns the stored snapshot, or {@code null} when no data file exists.
     * An unreadable file is archived as {@code *.corrupt-<timestamp>} so a
     * fresh store can be seeded.
     */
    public Snapshot load() {
        if (!Files.exists(file)) {
            return null;
        }
        try {
            Snapshot snapshot = mapper.readValue(file.toFile(), Snapshot.class);
            if (snapshot == null) {
                archiveCorruptFile();
                return null;
            }
            if (snapshot.version > FORMAT_VERSION) {
                throw new IllegalStateException("Data file " + file
                        + " uses format version " + snapshot.version
                        + " but this app supports version " + FORMAT_VERSION + ".");
            }
            return snapshot;
        } catch (JsonProcessingException e) {
            archiveCorruptFile();
            return null;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read marketplace data from " + file, e);
        }
    }

    private void archiveCorruptFile() {
        Path archived = file.resolveSibling(file.getFileName() + ".corrupt-"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd-HHmmss")));
        try {
            Files.move(file, archived);
            System.err.println("[MarketplaceDataStore] Data file was unreadable, archived to " + archived);
        } catch (IOException moveEx) {
            System.err.println("[MarketplaceDataStore] Data file was unreadable and could not be archived: "
                    + moveEx.getMessage());
        }
    }

    /**
     * Writes the snapshot atomically: data goes to a temp file first and is
     * then moved over the real file, so a crash cannot corrupt it.
     */
    public void save(Snapshot snapshot) {
        try {
            if (file.getParent() != null) {
                Files.createDirectories(file.getParent());
            }
            Path temp = file.resolveSibling(file.getFileName() + ".tmp");
            mapper.writerWithDefaultPrettyPrinter().writeValue(temp.toFile(), snapshot);
            try {
                Files.move(temp, file, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            } catch (AtomicMoveNotSupportedException e) {
                Files.move(temp, file, StandardCopyOption.REPLACE_EXISTING);
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save marketplace data to " + file, e);
        }
    }

    /**
     * Full marketplace state serialized to JSON.
     */
    public static final class Snapshot {
        public int version = FORMAT_VERSION;
        public List<User> users = new ArrayList<>();
        public List<Listing> listings = new ArrayList<>();
        public List<Deal> deals = new ArrayList<>();
        public Map<String, DummyDataService.DealExtras> dealExtras = new LinkedHashMap<>();
        public List<Notification> notifications = new ArrayList<>();
        public List<Review> reviews = new ArrayList<>();
        public List<DummyDataService.ChatThreadInfo> chatThreads = new ArrayList<>();
        public Map<String, List<DummyDataService.ChatMessage>> chatMessages = new LinkedHashMap<>();
        public List<DummyDataService.ReportRecord> reports = new ArrayList<>();
        public int listingIdCounter;
        public int dealIdCounter;
        public int notifIdCounter;
        public int reviewIdCounter;
        public int chatThreadIdCounter;
        public int chatMessageIdCounter;
        public int reportIdCounter;
    }
}
