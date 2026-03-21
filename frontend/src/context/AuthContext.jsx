// =============================================================================
// SimuChat — Authentication Context
// =============================================================================
// Provides auth state (user, token) and methods (login, register, logout)
// to all child components via React Context. Persists JWT in localStorage.
// =============================================================================

import { createContext, useState, useContext, useEffect } from 'react';
import api from '../services/api';

const AuthContext = createContext(null);

/**
 * Custom hook for accessing auth context.
 * Usage: const { user, token, login, register, logout } = useAuth();
 */
export const useAuth = () => {
    const context = useContext(AuthContext);
    if (!context) {
        throw new Error('useAuth must be used within an AuthProvider');
    }
    return context;
};

/**
 * AuthProvider wraps the app and manages authentication state.
 * On mount, it checks localStorage for an existing token.
 */
export const AuthProvider = ({ children }) => {
    const [user, setUser] = useState(null);
    const [token, setToken] = useState(null);
    const [loading, setLoading] = useState(true);

    // Restore auth state from localStorage on mount
    useEffect(() => {
        const savedToken = localStorage.getItem('token');
        const savedUsername = localStorage.getItem('username');
        if (savedToken && savedUsername) {
            setToken(savedToken);
            setUser({ username: savedUsername });
        }
        setLoading(false);
    }, []);

    /**
     * Registers a new user account.
     * @param {string} username
     * @param {string} password
     */
    const register = async (username, password) => {
        const response = await api.post('/register', { username, password });
        const { token: newToken, username: returnedUsername } = response.data;
        localStorage.setItem('token', newToken);
        localStorage.setItem('username', returnedUsername);
        setToken(newToken);
        setUser({ username: returnedUsername });
    };

    /**
     * Logs in an existing user.
     * @param {string} username
     * @param {string} password
     */
    const login = async (username, password) => {
        const response = await api.post('/login', { username, password });
        const { token: newToken, username: returnedUsername } = response.data;
        localStorage.setItem('token', newToken);
        localStorage.setItem('username', returnedUsername);
        setToken(newToken);
        setUser({ username: returnedUsername });
    };

    /**
     * Logs out the current user and clears stored state.
     */
    const logout = () => {
        localStorage.removeItem('token');
        localStorage.removeItem('username');
        setToken(null);
        setUser(null);
    };

    const value = {
        user,
        token,
        loading,
        login,
        register,
        logout,
        isAuthenticated: !!token,
    };

    return (
        <AuthContext.Provider value={value}>
            {children}
        </AuthContext.Provider>
    );
};
