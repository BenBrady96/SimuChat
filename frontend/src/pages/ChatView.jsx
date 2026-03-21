// =============================================================================
// SimuChat — Chat View (Two-Pane Layout)
// =============================================================================
// The main chat interface featuring:
// - Left sidebar: thread list for the selected character, "New Chat" button
// - Main area: message history with auto-scroll, fixed-bottom input bar
// - Typing indicator during AI response generation
//
// This layout mirrors the Gemini/ChatGPT conversational UI pattern.
// =============================================================================

import { useState, useEffect, useRef } from 'react';
import { useParams, useNavigate } from 'react-router-dom';
import { Button, Form, Spinner, Alert } from 'react-bootstrap';
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

const ChatView = () => {
    const { characterName } = useParams();
    const decodedName = decodeURIComponent(characterName);
    const navigate = useNavigate();
    const { user, logout } = useAuth();

    // State
    const [threads, setThreads] = useState([]);
    const [activeThreadId, setActiveThreadId] = useState(null);
    const [messages, setMessages] = useState([]);
    const [input, setInput] = useState('');
    const [sending, setSending] = useState(false);
    const [loadingThreads, setLoadingThreads] = useState(true);
    const [loadingMessages, setLoadingMessages] = useState(false);
    const [error, setError] = useState('');
    const [sidebarOpen, setSidebarOpen] = useState(true);

    // Refs
    const messagesEndRef = useRef(null);
    const inputRef = useRef(null);
    const isSendingRef = useRef(false); // Guards against fetchMessages racing with send

    // Scroll to bottom when messages change
    useEffect(() => {
        messagesEndRef.current?.scrollIntoView({ behavior: 'smooth' });
    }, [messages]);

    // Re-focus the input after sending completes (input re-enables on render)
    useEffect(() => {
        if (!sending) {
            inputRef.current?.focus();
        }
    }, [sending]);

    // Fetch threads on mount
    useEffect(() => {
        fetchThreads();
    }, [decodedName]);

    // Fetch messages when active thread changes
    // Skip if we're in the middle of sending (to avoid duplicates)
    useEffect(() => {
        if (activeThreadId && !isSendingRef.current) {
            fetchMessages(activeThreadId);
        } else if (!activeThreadId) {
            setMessages([]);
        }
    }, [activeThreadId]);

    // ---------------------------------------------------------------------------
    // API Calls
    // ---------------------------------------------------------------------------

    const fetchThreads = async () => {
        try {
            const response = await api.get(`/threads?character=${encodeURIComponent(decodedName)}`);
            setThreads(response.data);
            // Auto-select the most recent thread if available
            if (response.data.length > 0 && !activeThreadId) {
                setActiveThreadId(response.data[0].id);
            }
        } catch (err) {
            setError('Failed to load chat threads.');
        } finally {
            setLoadingThreads(false);
        }
    };

    const fetchMessages = async (threadId) => {
        setLoadingMessages(true);
        try {
            const response = await api.get(`/threads/${threadId}/messages`);
            setMessages(response.data);
        } catch (err) {
            setError('Failed to load messages.');
        } finally {
            setLoadingMessages(false);
        }
    };

    const handleNewChat = async () => {
        try {
            const response = await api.post('/threads', { characterName: decodedName });
            const newThread = response.data;
            setThreads((prev) => [newThread, ...prev]);
            setActiveThreadId(newThread.id);
            setMessages([]);
            inputRef.current?.focus();
        } catch (err) {
            setError('Failed to create a new chat.');
        }
    };

    const handleDeleteThread = async (threadId, e) => {
        e.stopPropagation();
        try {
            await api.delete(`/threads/${threadId}`);
            setThreads((prev) => prev.filter((t) => t.id !== threadId));
            if (activeThreadId === threadId) {
                setActiveThreadId(null);
                setMessages([]);
            }
        } catch (err) {
            setError('Failed to delete thread.');
        }
    };

    const handleSendMessage = async (e) => {
        e.preventDefault();
        if (!input.trim() || sending) return;

        // If no active thread, create one first
        let threadId = activeThreadId;
        if (!threadId) {
            try {
                const response = await api.post('/threads', { characterName: decodedName });
                const newThread = response.data;
                setThreads((prev) => [newThread, ...prev]);
                threadId = newThread.id;
                setActiveThreadId(threadId);
            } catch (err) {
                setError('Failed to create a new chat.');
                return;
            }
        }

        const messageContent = input.trim();
        setInput('');
        setSending(true);
        isSendingRef.current = true; // Block fetchMessages useEffect

        // Optimistically add the user message
        const tempUserMsg = {
            id: Date.now(),
            threadId,
            role: 'user',
            content: messageContent,
            timestamp: new Date().toISOString(),
        };
        setMessages((prev) => [...prev, tempUserMsg]);

        try {
            const response = await api.post(`/threads/${threadId}/message`, {
                content: messageContent,
            });

            // Replace the optimistic message and add AI response
            setMessages((prev) => {
                const filtered = prev.filter((m) => m.id !== tempUserMsg.id);
                return [...filtered, response.data.userMessage, response.data.aiMessage];
            });

            // Update thread title in the sidebar
            await fetchThreads();
        } catch (err) {
            setError('Failed to send message. Please try again.');
            // Remove the optimistic message on error
            setMessages((prev) => prev.filter((m) => m.id !== tempUserMsg.id));
            setInput(messageContent); // Restore their input
        } finally {
            setSending(false);
            isSendingRef.current = false; // Re-enable fetchMessages useEffect
            inputRef.current?.focus();
        }
    };

    // ---------------------------------------------------------------------------
    // Character Info
    // ---------------------------------------------------------------------------

    const getCharacterEmoji = () => {
        const emojiMap = {
            'Cloud Strife': '⚔️',
            'Tifa Lockhart': '🥊',
            'Sephiroth': '🗡️',
            'Vivi Ornitier': '🎩',
            'Yuna': '🌸',
            'Lightning': '⚡',
        };
        return emojiMap[decodedName] || '💬';
    };

    /** Gets the character portrait image or falls back to emoji */
    const getCharacterImage = () => characterImages[decodedName] || null;

    /** Renders the character avatar — image if available, emoji fallback */
    const renderAvatar = (size = 'small') => {
        const img = getCharacterImage();
        if (img) {
            return (
                <img
                    src={img}
                    alt={decodedName}
                    className={`avatar-img avatar-${size}`}
                />
            );
        }
        return getCharacterEmoji();
    };

    /**
     * Renders basic markdown (bold and italic) as HTML.
     * Escapes HTML entities first to prevent XSS attacks.
     */
    const renderMarkdown = (text) => {
        if (!text) return '';
        // 1. Escape HTML to prevent XSS
        let html = text
            .replace(/&/g, '&amp;')
            .replace(/</g, '&lt;')
            .replace(/>/g, '&gt;');
        // 2. Convert **bold** (must come before *italic*)
        html = html.replace(/\*\*(.+?)\*\*/g, '<strong>$1</strong>');
        // 3. Convert *italic*
        html = html.replace(/\*(.+?)\*/g, '<em>$1</em>');
        // 4. Convert newlines to <br>
        html = html.replace(/\n/g, '<br />');
        return html;
    };

    // ---------------------------------------------------------------------------
    // Render
    // ---------------------------------------------------------------------------

    return (
        <div className="chat-layout">
            {/* ---- Top Navigation Bar ---- */}
            <nav className="chat-navbar">
                <div className="nav-left">
                    <Button
                        variant="link"
                        className="sidebar-toggle"
                        onClick={() => setSidebarOpen(!sidebarOpen)}
                        title="Toggle sidebar"
                    >
                        ☰
                    </Button>
                    <Button
                        variant="link"
                        className="back-button"
                        onClick={() => navigate('/')}
                        title="Back to characters"
                    >
                        ← Characters
                    </Button>
                </div>
                <div className="nav-center">
                    <span className="character-badge">
                        {getCharacterEmoji()} {decodedName}
                    </span>
                </div>
                <div className="nav-right">
                    <span className="nav-username">👤 {user?.username}</span>
                    <Button variant="outline-light" size="sm" onClick={logout} className="logout-btn">
                        Log Out
                    </Button>
                </div>
            </nav>

            <div className="chat-body">
                {/* ---- Left Sidebar: Thread List ---- */}
                <aside className={`thread-sidebar ${sidebarOpen ? 'open' : 'closed'}`}>
                    <div className="sidebar-header">
                        <h3 className="sidebar-title">Chat History</h3>
                        <Button variant="primary" size="sm" className="new-chat-btn" onClick={handleNewChat}>
                            + New Chat
                        </Button>
                    </div>

                    <div className="thread-list">
                        {loadingThreads ? (
                            <div className="text-center p-3">
                                <Spinner animation="border" size="sm" />
                            </div>
                        ) : threads.length === 0 ? (
                            <div className="no-threads">
                                <p>No conversations yet.</p>
                                <p className="text-muted small">Click &quot;New Chat&quot; to start!</p>
                            </div>
                        ) : (
                            threads.map((thread) => (
                                <div
                                    key={thread.id}
                                    className={`thread-item ${activeThreadId === thread.id ? 'active' : ''}`}
                                    onClick={() => setActiveThreadId(thread.id)}
                                    role="button"
                                    tabIndex={0}
                                    onKeyDown={(e) => e.key === 'Enter' && setActiveThreadId(thread.id)}
                                >
                                    <div className="thread-title">{thread.title}</div>
                                    <div className="thread-meta">
                                        {new Date(thread.createdAt).toLocaleDateString('en-GB', {
                                            day: 'numeric',
                                            month: 'short',
                                        })}
                                        <button
                                            className="thread-delete"
                                            onClick={(e) => handleDeleteThread(thread.id, e)}
                                            title="Delete thread"
                                        >
                                            🗑️
                                        </button>
                                    </div>
                                </div>
                            ))
                        )}
                    </div>
                </aside>

                {/* ---- Main Chat Area ---- */}
                <main className="chat-main">
                    {error && (
                        <Alert
                            variant="danger"
                            className="chat-error"
                            dismissible
                            onClose={() => setError('')}
                        >
                            {error}
                        </Alert>
                    )}

                    {/* Messages */}
                    <div className="messages-container">
                        {!activeThreadId && !sending ? (
                            <div className="empty-chat">
                                <div className="empty-emoji">{renderAvatar('large')}</div>
                                <h3>Start a conversation with {decodedName}</h3>
                                <p className="text-muted">
                                    Type a message below or click &quot;New Chat&quot; to begin.
                                </p>
                            </div>
                        ) : loadingMessages ? (
                            <div className="text-center p-5">
                                <Spinner animation="border" />
                            </div>
                        ) : (
                            <>
                                {messages.map((msg) => (
                                    <div
                                        key={msg.id}
                                        className={`message-bubble ${msg.role === 'user' ? 'user-message' : 'ai-message'}`}
                                    >
                                        {msg.role === 'model' && (
                                            <div className="message-avatar">{renderAvatar()}</div>
                                        )}
                                        <div className="message-content">
                                            <div
                                                className="message-text"
                                                dangerouslySetInnerHTML={{ __html: renderMarkdown(msg.content) }}
                                            />
                                            <div className="message-time">
                                                {new Date(msg.timestamp).toLocaleTimeString('en-GB', {
                                                    hour: '2-digit',
                                                    minute: '2-digit',
                                                })}
                                            </div>
                                        </div>
                                    </div>
                                ))}

                                {/* Typing Indicator */}
                                {sending && (
                                    <div className="message-bubble ai-message typing-indicator">
                                        <div className="message-avatar">{renderAvatar()}</div>
                                        <div className="message-content">
                                            <div className="typing-dots">
                                                <span></span>
                                                <span></span>
                                                <span></span>
                                            </div>
                                        </div>
                                    </div>
                                )}

                                <div ref={messagesEndRef} />
                            </>
                        )}
                    </div>

                    {/* Fixed-Bottom Input Bar */}
                    <Form className="chat-input-bar" onSubmit={handleSendMessage}>
                        <Form.Control
                            ref={inputRef}
                            type="text"
                            placeholder={`Message ${decodedName}...`}
                            value={input}
                            onChange={(e) => setInput(e.target.value)}
                            disabled={sending}
                            className="chat-input"
                            autoFocus
                        />
                        <Button
                            type="submit"
                            variant="primary"
                            className="send-button"
                            disabled={!input.trim() || sending}
                        >
                            {sending ? <Spinner animation="border" size="sm" /> : '➤'}
                        </Button>
                    </Form>
                </main>
            </div>
        </div>
    );
};

export default ChatView;
