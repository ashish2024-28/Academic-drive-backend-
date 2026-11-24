# Backend deployment

Use the production topology and environment settings in the repository's [deployment guide](../DEPLOYMENT.md). In particular, both Render backend services must be behind one stable API hostname and share MongoDB, OAuth credentials, and the token encryption key.

For local development, copy `.env.example` to `.env`. For Render, set the production values in each service's environment settings; `.env.production.example` is a reference and should not contain secrets.
