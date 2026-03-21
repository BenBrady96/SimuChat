// =============================================================================
// SimuChat — Character Selection Screen
// =============================================================================
// Displays a responsive grid of AI character cards. Each card shows the
// character's portrait, name, and description. Clicking a card navigates
// to the chat view for that character.
//
// FINAL FANTASY is a registered trademark of Square Enix Holdings Co., Ltd.
// This is a non-commercial fan project. Character images are AI-generated.
// =============================================================================

import { useState, useEffect } from 'react';
import { useNavigate } from 'react-router-dom';
import { Container, Row, Col, Card, Spinner, Alert, Button } from 'react-bootstrap';
import { useAuth } from '../context/AuthContext';
import api from '../services/api';

// Character portrait images (AI-generated original art)
import cloudImg from '../assets/characters/cloud.png';
import tifaImg from '../assets/characters/tifa.png';
import sephirothImg from '../assets/characters/sephiroth.png';
import viviImg from '../assets/characters/vivi.png';
import yunaImg from '../assets/characters/yuna.png';
import lightningImg from '../assets/characters/lightning.png';

// Map character names to their portrait images
const characterImages = {
    'Cloud Strife': cloudImg,
    'Tifa Lockhart': tifaImg,
    'Sephiroth': sephirothImg,
    'Vivi Ornitier': viviImg,
    'Yuna': yunaImg,
    'Lightning': lightningImg,
};

const CharacterSelect = () => {
    const [characters, setCharacters] = useState([]);
    const [loading, setLoading] = useState(true);
    const [error, setError] = useState('');
    const { user, logout } = useAuth();
    const navigate = useNavigate();

    useEffect(() => {
        fetchCharacters();
    }, []);

    const fetchCharacters = async () => {
        try {
            const response = await api.get('/characters');
            setCharacters(response.data);
        } catch (err) {
            setError('Failed to load characters. Please try again.');
        } finally {
            setLoading(false);
        }
    };

    const handleCharacterClick = (characterName) => {
        navigate(`/chat/${encodeURIComponent(characterName)}`);
    };

    if (loading) {
        return (
            <div className="d-flex justify-content-center align-items-center vh-100">
                <Spinner animation="border" variant="primary" />
            </div>
        );
    }

    return (
        <div className="character-page">
            {/* Top Navigation Bar */}
            <nav className="top-navbar">
                <div className="nav-brand">
                    <span className="brand-icon">💬</span> SimuChat
                </div>
                <div className="nav-right">
                    <span className="nav-username">👤 {user?.username}</span>
                    <Button variant="outline-light" size="sm" onClick={logout} className="logout-btn">
                        Log Out
                    </Button>
                </div>
            </nav>

            {/* Character Grid */}
            <Container className="character-container">
                <div className="text-center mb-5">
                    <h1 className="page-title">Choose Your Character</h1>
                    <p className="page-subtitle">
                        Select a legendary Final Fantasy character to chat with. Each has their own unique personality.
                    </p>
                </div>

                {error && <Alert variant="danger">{error}</Alert>}

                <Row className="g-4 justify-content-center">
                    {characters.map((character) => (
                        <Col key={character.name} xs={12} sm={6} md={4} lg={4}>
                            <Card
                                className="character-card"
                                onClick={() => handleCharacterClick(character.name)}
                                role="button"
                                tabIndex={0}
                                onKeyDown={(e) => e.key === 'Enter' && handleCharacterClick(character.name)}
                            >
                                <Card.Body className="text-center p-4">
                                    <div className="character-portrait">
                                        {characterImages[character.name] ? (
                                            <img
                                                src={characterImages[character.name]}
                                                alt={character.name}
                                                className="character-portrait-img"
                                            />
                                        ) : (
                                            <div className="character-emoji">{character.emoji}</div>
                                        )}
                                    </div>
                                    <Card.Title className="character-name">{character.name}</Card.Title>
                                    <Card.Text className="character-description">
                                        {character.description}
                                    </Card.Text>
                                    <div className="character-cta">Start Chatting →</div>
                                </Card.Body>
                            </Card>
                        </Col>
                    ))}
                </Row>

                {/* Square Enix IP Disclaimer */}
                <div className="disclaimer-footer">
                    <p>
                        FINAL FANTASY is a registered trademark of Square Enix Holdings Co., Ltd.
                        This is a non-commercial fan project for educational purposes only.
                        Character portraits are AI-generated original artwork.
                    </p>
                </div>
            </Container>
        </div>
    );
};

export default CharacterSelect;
