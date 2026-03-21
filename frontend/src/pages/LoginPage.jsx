// =============================================================================
// SimuChat — Login Page
// =============================================================================
// Clean, centred card layout with username/password fields.
// Includes a link to the registration page and error handling.
// =============================================================================

import { useState } from 'react';
import { Link, useNavigate } from 'react-router-dom';
import { Container, Card, Form, Button, Alert, Spinner } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';

const LoginPage = () => {
    const [username, setUsername] = useState('');
    const [password, setPassword] = useState('');
    const [error, setError] = useState('');
    const [loading, setLoading] = useState(false);
    const { login } = useAuth();
    const navigate = useNavigate();

    const handleSubmit = async (e) => {
        e.preventDefault();
        setError('');
        setLoading(true);

        try {
            await login(username, password);
            navigate('/');
        } catch (err) {
            const status = err.response?.status;
            const message = err.response?.data?.error;

            if (status === 423) {
                // Account locked
                setError(message || 'Account is locked. Please try again later.');
            } else if (status === 401) {
                // Wrong credentials — backend includes remaining attempts
                setError(message || 'Incorrect username or password.');
            } else {
                setError(message || 'Login failed. Please check your credentials and try again.');
            }
        } finally {
            setLoading(false);
        }
    };

    return (
        <div className="auth-page">
            <Container className="d-flex justify-content-center align-items-center min-vh-100">
                <div className="auth-card-wrapper">
                    {/* Brand Header */}
                    <div className="text-center mb-4">
                        <h1 className="brand-title">
                            <span className="brand-icon">💬</span> SimuChat
                        </h1>
                        <p className="brand-subtitle">Chat with unique AI personalities</p>
                    </div>

                    {/* Login Card */}
                    <Card className="auth-card">
                        <Card.Body className="p-4">
                            <h2 className="text-center mb-4 card-heading">Welcome Back</h2>

                            {error && (
                                <Alert variant="danger" className="auth-alert" onClose={() => setError('')} dismissible>
                                    {error}
                                </Alert>
                            )}

                            <Form onSubmit={handleSubmit}>
                                <Form.Group className="mb-3" controlId="loginUsername">
                                    <Form.Label>Username</Form.Label>
                                    <Form.Control
                                        type="text"
                                        placeholder="Enter your username"
                                        value={username}
                                        onChange={(e) => setUsername(e.target.value)}
                                        required
                                        className="auth-input"
                                        autoFocus
                                    />
                                </Form.Group>

                                <Form.Group className="mb-4" controlId="loginPassword">
                                    <Form.Label>Password</Form.Label>
                                    <Form.Control
                                        type="password"
                                        placeholder="Enter your password"
                                        value={password}
                                        onChange={(e) => setPassword(e.target.value)}
                                        required
                                        className="auth-input"
                                    />
                                </Form.Group>

                                <Button
                                    variant="primary"
                                    type="submit"
                                    className="w-100 auth-button"
                                    disabled={loading}
                                >
                                    {loading ? (
                                        <>
                                            <Spinner animation="border" size="sm" className="me-2" />
                                            Logging in...
                                        </>
                                    ) : (
                                        'Log In'
                                    )}
                                </Button>
                            </Form>

                            <div className="text-center mt-3">
                                <span className="text-muted">Don&apos;t have an account? </span>
                                <Link to="/register" className="auth-link">Register</Link>
                            </div>
                        </Card.Body>
                    </Card>
                </div>
            </Container>
        </div>
    );
};

export default LoginPage;
