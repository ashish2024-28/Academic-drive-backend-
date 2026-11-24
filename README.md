# Academic Drive – College Study Material Manager

Academic Drive is an offline-first React PWA for organizing college study materials, with a Spring Boot API for Google sign-in and Google Drive operations.

## Repository layout

- [`frontend/`](./frontend/) — React/Vite app, PWA assets, and Learning Center materials.
- [`backend/`](./backend/) — Java 21 / Spring Boot API, MongoDB persistence, and backend configuration.
- [`compose.yaml`](./compose.yaml) — local containers for the frontend and backend.
- [`render.yaml`](./render.yaml) — Render Blueprint for the backend.
- [`DEPLOYMENT.md`](./DEPLOYMENT.md) — Vercel, Render, Google OAuth, and production cookie setup.
- [`frontend/learning/README.md`](./frontend/learning/README.md) — setup, architecture, data-storage, and interview guides.

## Local development

1. Use Node.js 22.12+ and Java 21. Copy `.env.example` to `.env` and set the MongoDB URI, Google sign-in/Drive OAuth credentials, and token-encryption key.
2. Copy `frontend/.env.example` to `frontend/.env` and set the API URL and restricted, browser-visible Google Picker settings.
3. Configure the Google OAuth web client with `http://localhost:3000` as an authorized JavaScript origin and these redirect URIs:
   - `http://localhost:8080/login/oauth2/code/google`
   - `http://localhost:8080/login/oauth2/code/google-drive`
4. Start the backend from `backend/` with `mvn spring-boot:run`, and start the frontend from `frontend/` with `npm ci` followed by `npm run dev`.

The backend setup guide explains the MongoDB schema, OAuth/Drive flow, and end-to-end verification.

## Docker

With Docker Compose installed and both local environment files configured, run from the repository root:

```powershell
docker compose --env-file frontend/.env up --build
```

The frontend is available at `http://localhost:3000` and the backend at `http://localhost:8080`. Compose reads private backend values from `backend/.env`; the frontend build receives only public `VITE_` values. Stop with `docker compose down`. The optional MongoDB-only Compose file is `backend/docker-compose.yml`.

## Production

Deploy the static app to Vercel and the Spring Boot API to Render. Production requires HTTPS, MongoDB Atlas (or another managed MongoDB service), exact OAuth redirect URIs, and same-site custom domains for reliable session cookies. Follow [`DEPLOYMENT.md`](./DEPLOYMENT.md) before publishing.

Run the frontend type-check, tests, and production build with `npm run check` from `frontend/`; run backend tests with `mvn -B -f backend/pom.xml verify` from the repository root. Google sign-in and Drive sync require valid Google credentials and network access even when the PWA is available offline.

---

## 📚 Features

### 1. 8 Semesters Architecture
- **8 Semesters by Default**: Configured out-of-the-box (`Semester 1` to `Semester 8`), with the ability to add custom semesters, rename, and reorder.
- **Subject-Wise Management**: Subject cards with course codes (e.g., `CS201`, `CS302`), credit ratings, file counts, and color badges.
- **4 Core Categories Per Subject**:
  1. **Theory Notes**: Organized into Unit 1 through Unit 5 by default, with dynamic unit addition (`Unit 6`, etc.), renaming, and deletion.
  2. **Practice & Lab Files**: Lab programs, workshop records, and viva preparation.
  3. **Assignments & Projects**: Homework submissions, seminar presentations, and reports.
  4. **Curriculum & Syllabus**: Official university syllabus, exam blueprints, and previous year question papers.

### 2. High-Performance File Explorer
- **Grid & List Views**: Toggle between visual card grids and detailed list views.
- **Drag-and-Drop & Batch Upload**: Drag single or multiple study files directly into any unit or subfolder with real-time upload progress.
- **In-App Previews**:
  - **PDF Viewer**: Sharp, scrollable in-app PDF.js viewer, loaded only when a PDF is opened.
  - **Image Lightbox**: High-res viewer with zoom in/out controls.
  - **Text & CSV Reader**: In-app text preview with copy and editing support.
  - **Spreadsheets & ZIPs**: First-sheet XLS/XLSX/ODS preview and ZIP archive listing.
  - **Other Documents**: Download or open with a compatible device app.
- **Context Menus**: Three-dot options for Preview, Rename, Move To (across any semester/subject/folder), Download, and Delete.
- **Recycle Bin**: Recover accidentally deleted notes or permanently purge files.

### 3. Progressive Web App (PWA) & Offline-First
- **Installable Application**: In-app "Install App" button for Android, Chromium, and Desktop, with guided steps for iOS Safari.
- **Offline Caching**: All files and structure are saved directly in IndexedDB via Dexie.js. Continues working with zero internet connectivity.
- **Connectivity Indicator**: Real-time status badge showing Online vs Offline Cache mode.

### 4. Backup & Restore
- **Metadata Export**: Generates `.academicdriver-index.json` with versioning, folder hierarchies, and custom ordering.
- **Full ZIP Export**: Packages all actual study files (PDFs, images, notes) alongside `.academicdriver-index.json` using JSZip.
- **Lossless Restore**: Restore from either JSON or full ZIP archives with duplicate detection.

### 5. Exam Events
- **Multiple Countdowns**: Track exams, submissions, and other dated academic events.
- **Completion Prompt**: Confirm before deleting a completed event.
- **Cross-Device Sync**: Events are saved in the Drive data manifest and the `Events/events.json` file when Google Drive is connected.

---

## 🗂 Standard Folder Structure
academic drive structures folders according to the college standard:

```
AcademicDriver/
├── Semester 1/
│   ├── CS101 - Programming Fundamentals/
│   │   ├── Theory/
│   │   │   ├── Unit 1/
│   │   │   ├── Unit 2/
│   │   │   └── ...
│   │   ├── Practice/
│   │   │   ├── Experiment 1/
│   │   │   └── ...
│   │   ├── Assignment/
│   │   └── Syllabus/
│   └── ...
└── .academicdriver-index.json
```
