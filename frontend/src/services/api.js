// =============================================================================
// SimuChat — API Service Layer
// =============================================================================
// Axios instance with JWT interceptor. Automatically attaches the Bearer
// token to every request and redirects to login on 401 responses.
// =============================================================================

import axios from 'axios';

// Base URL — In development, Vite proxies /api to the backend.
// In production (Docker/K8s), Nginx handles the proxy.
const api = axios.create({
    baseURL: '/api',
    headers: {
        'Content-Type': 'application/json',
    },
});

// ---- Request Interceptor: Attach JWT ----
api.interceptors.request.use(
    (config) => {
        const token = localStorage.getItem('token');
        if (token) {
            config.headers.Authorization = `Bearer ${token}`;
        }
        return config;
    },
    (error) => Promise.reject(error)
);

// ---- Response Interceptor: Handle 401 ----
// Only redirect on 401 for protected endpoints — NOT for /login or /register,
// where we want to display the error message to the user instead.
api.interceptors.response.use(
    (response) => response,
    (error) => {
        const requestUrl = error.config?.url || '';
        const isAuthEndpoint = requestUrl.includes('/login') || requestUrl.includes('/register');

        if (error.response?.status === 401 && !isAuthEndpoint) {
            // Token expired or invalid — clear auth state and redirect
            localStorage.removeItem('token');
            localStorage.removeItem('username');
            window.location.href = '/login';
        }
        return Promise.reject(error);
    }
);

export default api;
