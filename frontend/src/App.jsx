// =============================================================================
// SimuChat — App Router
// =============================================================================
// Defines the application routing structure with React Router.
// Public routes: /login, /register
// Protected routes: / (character select), /chat/:characterName (chat view)
// =============================================================================

import { Routes, Route, Navigate } from 'react-router-dom';
import ProtectedRoute from './components/ProtectedRoute';
import LoginPage from './pages/LoginPage';
import RegisterPage from './pages/RegisterPage';
import CharacterSelect from './pages/CharacterSelect';
import ChatView from './pages/ChatView';
import { useAuth } from './context/AuthContext';

function App() {
  const { isAuthenticated } = useAuth();

  return (
    <Routes>
      {/* Public Routes */}
      <Route
        path="/login"
        element={isAuthenticated ? <Navigate to="/" replace /> : <LoginPage />}
      />
      <Route
        path="/register"
        element={isAuthenticated ? <Navigate to="/" replace /> : <RegisterPage />}
      />

      {/* Protected Routes */}
      <Route
        path="/"
        element={
          <ProtectedRoute>
            <CharacterSelect />
          </ProtectedRoute>
        }
      />
      <Route
        path="/chat/:characterName"
        element={
          <ProtectedRoute>
            <ChatView />
          </ProtectedRoute>
        }
      />

      {/* Catch-all — redirect to home */}
      <Route path="*" element={<Navigate to="/" replace />} />
    </Routes>
  );
}

export default App;
