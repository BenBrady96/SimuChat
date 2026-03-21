# =============================================================================
# SimuChat Frontend — Multi-Stage Dockerfile
# =============================================================================
# Stage 1: Build the React app with Node.js
# Stage 2: Serve the static build with Nginx
#
# Build:  docker build -t simuchat-frontend .
# Run:    docker run -p 3000:80 simuchat-frontend
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1: Build
# ---------------------------------------------------------------------------
FROM node:20-alpine AS build

WORKDIR /app

# Copy dependency manifests first for better Docker layer caching
COPY package.json package-lock.json ./
RUN npm ci --silent

# Copy source code and build the production bundle
COPY . .
RUN npm run build

# ---------------------------------------------------------------------------
# Stage 2: Production (Nginx)
# ---------------------------------------------------------------------------
FROM nginx:alpine

# Copy custom Nginx config for SPA routing and API proxy
COPY nginx.conf /etc/nginx/conf.d/default.conf

# Copy the built React app from the build stage
COPY --from=build /app/dist /usr/share/nginx/html

# Expose port 80
EXPOSE 80

# Nginx runs as PID 1
CMD ["nginx", "-g", "daemon off;"]
