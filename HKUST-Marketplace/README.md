# HKUST Marketplace

A JavaFX + Maven campus marketplace for buying, borrowing and giving away items.
The application implements the Buyer, Seller and Admin portals from the COMP3111 project brief.

## Prerequisites

1. **JDK 17 or newer** (full JDK, not only JRE)
   - Recommended: https://adoptium.net/ (Temurin 17 or 21)
   - Check:
     ```powershell
     java -version
     javac -version
     ```
2. **Maven is optional** - the project includes the Maven Wrapper (`mvnw.cmd` on Windows).

## How to Run (Windows)

Open PowerShell in the `HKUST-Marketplace` folder:

```powershell
.\mvnw.cmd clean javafx:run
```

If Maven is installed and on PATH:

```powershell
mvn clean javafx:run
```

### IntelliJ IDEA

1. Open the `HKUST-Marketplace` folder as a Maven project.
2. Wait for dependencies to download.
3. Run `MarketplaceApp` through the Maven JavaFX goal, or add the JavaFX module VM options to the run configuration.

### Run tests

```powershell
.\mvnw.cmd clean test
```

The JaCoCo report is generated at:

```text
target/site/jacoco/index.html
```

## Sample Credentials

| Role   | Username  | Password   |
|--------|-----------|------------|
| Admin  | `admin`   | `admin123` |
| Buyer  | `alice`   | `password1` |
| Buyer  | `bob`     | `password1` |
| Seller | `charlie` | `password1` |
| Seller | `diana`   | `password1` |

All passwords are stored as BCrypt hashes, including the seeded accounts; plaintext
password values are rejected during login. These accounts are created automatically on
first launch, when no data file exists yet.

## Implemented Features

### Shared
- Role-based access checks in the service layer (buyer / seller / admin).
- Consistent JavaFX cards, sidebars, badges, notifications and profile forms.
- JSON file-backed data store (`DummyDataService` + `MarketplaceDataStore`) with sample seed data on first run.
- Listing view-count tracking when a live listing's detail screen is opened.
- Searchable, retained chat history.

### Buyer Portal
- Registration and login with duplicate-username, password-strength and role checks.
- Marketplace search, category/type/condition/price filters, newest/price sorting.
- Listing detail with photos, description, seller rating, view count, report and chat.
- Direct Buy Now at the asking price, sell offers below asking price, borrow requests
  and first-wins giveaway claims.
- Private listing-linked chat with searchable history and formal offers.
- My Deals with self-return, mark-complete, counter-offer handling, auto-return and
  automatic cancellation of expired borrow requests.
- Profile editing, categorised notifications with read/unread state.
- Deal history with date/category/seller/search filters.
- Buyer review history and seller average-rating recalculation.

### Seller Portal
- Hashed seller registration and login.
- Create listings with type-specific validation and up to 6 photos.
- My Listings with edit/delete restrictions while a deal is active.
- Offers & Requests screen: view, accept, decline, counter above the buyer offer and
  at most the asking price, cancel a counter-offer, cancel an accepted sale/giveaway
  if the buyer does not follow through, and open the linked chat.
- Seller profile editing (name, password, bio, preferred meet-up).
- Notifications with read/unread state.
- Real seller statistics: views, offers, completed deals, average rating, category bar chart and status pie chart.
- Review handling: reply to buyers and flag inappropriate reviews for admin.

### Admin Portal
- Seeded admin login.
- Listing review and approval/rejection with written rejection reasons.
- Manage all users: edit details, activate/deactivate, permanently delete.
  - Deleting a seller removes the account and takes their listings down; deals, chats and history are retained.
- Manage all listings: add on behalf of a seller, edit any listing without an active deal, remove with a reason and notify buyers with active offers.
- All Deals record with search, type and status filters.
- Seller ratings overview with seller/rating/date filters and hide/unhide moderation.
- Listing reports queue with buyer reason, timestamp and a resolve action.
- Admin notifications and profile editing.

## Password Strength Rules (exact)

- Minimum 8 characters.
- Must contain at least one uppercase letter.
- Must contain at least one lowercase letter.
- Must contain at least one digit.
- Symbols are allowed but not required.

Price inputs (listing price, offers, counter-offers) accept only positive numbers with at most 2 decimal places, e.g. `12`, `12.3`, `12.34`. Sale offers must be below the asking price; seller counter-offers must be above the buyer offer and at most the asking price.

## Data and Architecture Notes

- Data is stored in a local JSON file at `%USERPROFILE%\.hkust-marketplace\marketplace-data.json`
  (in the user home folder). It is created and seeded on first launch and reloaded on every start.
- Every change is saved immediately; delete the JSON file to reset the app back to the seed data.
- Unreadable JSON files are archived as `*.corrupt-<timestamp>` before reseeding, and data files
  written by a newer app version are rejected instead of being overwritten.
- Legacy plaintext password values are upgraded to BCrypt hashes when the data file is loaded.
- The data file location can be overridden with `-Dmarketplace.data.file=<path>`.
- Buyer, Seller and Admin operations are guarded by role checks in the service layer.
- Offer, borrow and claim statuses use the constants in `DealStatuses`.
- Concurrent giveaway claims are serialized; only the first buyer succeeds.

## Project Structure

```text
HKUST-Marketplace/
  mvnw / mvnw.cmd
  pom.xml
  README.md
  src/main/
    java/com/hkust/marketplace/
      MarketplaceApp.java
      controllers/
      models/
      utils/
    resources/
      fxml/
      css/styles.css
      images/hkust_logo.png
  src/test/java/com/hkust/marketplace/
    BuyerFlowTest.java
    MarketplaceRulesTest.java
    utils/LifecycleRulesTest.java
    utils/PersistenceTest.java
```

## Troubleshooting

### `mvn` is not recognized
Use the wrapper:

```powershell
.\mvnw.cmd clean javafx:run
```

### JavaFX runtime components are missing
Do not run `MarketplaceApp` as a plain Java Application in IntelliJ. Use:

```powershell
.\mvnw.cmd clean javafx:run
```

or add JavaFX `--module-path` / `--add-modules` VM options to the run configuration.

### See the full build error

```powershell
.\mvnw.cmd clean javafx:run -e
```

## Licence

Educational use - HKUST COMP project.
