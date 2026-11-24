# Production deployment

The supported hosted layout is a Vercel static frontend and a Render Spring Boot backend connected to MongoDB Atlas. The repository includes `frontend/vercel.json` and `render.yaml`; the hosting accounts, domain, and secret values remain under your control.

## 1. Choose same-site custom domains

Use HTTPS domains under the same registered domain, for example:

- Frontend: `https://app.example.com`
- Backend: `https://api.example.com`

Different default domains such as `*.vercel.app` and `*.onrender.com` are cross-site. Browser third-party-cookie restrictions can prevent session cookies from working reliably between them. A custom same-site domain avoids that issue.

## 2. Deploy the backend to Render

1. Connect the repository in Render and create a Blueprint from the root `render.yaml`.
2. The Blueprint selects Singapore, close to the current Atlas region. If your Atlas cluster is elsewhere, adjust the Render region before creating the service. Choose a production compute plan appropriate for your traffic and budget; the Blueprint intentionally does not choose a billable plan. Configure Atlas Network Access for the Render service's outbound IP addresses; do not allow `0.0.0.0/0`.
3. Add these values to the Render service's environment settings. Do not commit them:
   - `MONGODB_URI`: the Atlas URI with the `academic_drive` database name.
   - `GOOGLE_CLIENT_ID` and `GOOGLE_CLIENT_SECRET`.
   - `GOOGLE_AI_API_KEY`.
   - `TOKEN_ENCRYPTION_KEY`: the same stable Base64-encoded 32-byte key used for the stored Drive credentials.
   - `APP_FRONTEND_ORIGIN`: the exact frontend origin, such as `https://app.example.com`.
   - `CSRF_COOKIE_DOMAIN`: the shared parent domain, such as `example.com` (without a leading dot).
4. Keep `COOKIE_SECURE=true` and `COOKIE_SAME_SITE=Lax`. The session cookie stays host-only; the readable CSRF cookie is shared with the frontend subdomain.
5. Wait for `/actuator/health` to pass. It exposes only sanitized health status.

Render supplies `PORT`; the backend honors it and uses forwarded headers when constructing Google OAuth callback URLs.

## 3. Deploy the frontend to Vercel

1. Import the repository and set **Root Directory** to `frontend`.
2. Use the checked-in Vite settings: `npm ci`, `npm run build`, and `dist`.
3. Add these production environment variables in Vercel:
   - `VITE_API_BASE_URL=https://api.example.com`
   - `VITE_GOOGLE_API_KEY`: the browser-visible Picker key, restricted to the Picker API and `https://app.example.com/*`.
   - `VITE_GOOGLE_PROJECT_NUMBER`: the Google Cloud project number.
   - `VITE_LEARNING_ACCESS_CODE` is optional and is only a client-side convenience, not security.
4. Attach the frontend custom domain and redeploy after setting environment variables. Vite values are embedded at build time.

## 4. Configure Google OAuth

In the Google OAuth Web client, set the authorized JavaScript origin to `https://app.example.com` and add these authorized redirect URIs:

```text
https://api.example.com/login/oauth2/code/google
https://api.example.com/login/oauth2/code/google-drive
```

The frontend origin and backend callback URLs are different settings. Keep the OAuth client secret only in Render's backend environment.

## 5. Verify production

1. Confirm `https://api.example.com/actuator/health` reports `UP`.
2. Confirm `https://app.example.com` loads and browser requests use the API custom domain.
3. Check that the CSRF cookie is scoped to the shared parent domain and that the session cookie is Secure, HttpOnly, and host-only for the API.
4. Complete Google profile sign-in, Drive consent, a Drive file operation, logout, and reconnect. Verify the backend session and encrypted Drive credentials are persisted in MongoDB.
5. Test the installed PWA offline separately; Google sign-in and Drive sync require a network connection.

Vercel preview URLs are not automatically allowed by the backend's exact-origin CORS policy or the Google OAuth client. Use production for the end-to-end OAuth check, or deliberately configure a separate preview environment and OAuth client.

## Docker

For local containers, configure `backend/.env` and `frontend/.env`, then run from the repository root:

```powershell
docker compose --env-file frontend/.env up --build
```

The frontend image receives only public `VITE_` build arguments. The backend image receives private values at runtime from `backend/.env`. The root Compose file binds ports to loopback; do not expose it directly to the public internet without a TLS reverse proxy, domain-specific cookie/CORS configuration, and a production secret manager.
