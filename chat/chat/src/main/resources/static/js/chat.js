// Global variables
let stompClient = null;
let currentConversationId = null;
let currentChatRoomId = null;
let currentChatType = null; // 'conversation' or 'chatroom'
let currentUserId = null;
let currentUsername = null;
let typingTimeout = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    currentUserId = document.getElementById('currentUserId').value;
    currentUsername = document.getElementById('currentUsername').value;

    initializeEventListeners();
    connectWebSocket();
    loadConversations();
    loadChatRooms();
    loadUsers();
});

// Initialize event listeners
function initializeEventListeners() {
    // Tab switching
    document.querySelectorAll('.tab-btn').forEach(btn => {
        btn.addEventListener('click', function() {
            const tab = this.getAttribute('data-tab');
            switchTab(tab);
        });
    });

    // New chat buttons
    document.getElementById('btn-new-direct').addEventListener('click', showNewDirectModal);
    document.getElementById('btn-new-group').addEventListener('click', showNewGroupModal);

    // Send message
    document.getElementById('send-button').addEventListener('click', sendMessage);
    document.getElementById('message-input').addEventListener('keypress', function(e) {
        if (e.key === 'Enter') {
            sendMessage();
        }
    });

    // Typing indicator
    document.getElementById('message-input').addEventListener('input', handleTyping);

    // Modal close buttons
    document.querySelectorAll('.close').forEach(btn => {
        btn.addEventListener('click', function() {
            this.closest('.modal').classList.add('hidden');
        });
    });

    // Create group button
    document.getElementById('create-group-btn').addEventListener('click', createGroup);

    // Close chat button
    document.getElementById('btn-close-chat').addEventListener('click', closeChat);

    // Search users
    document.getElementById('search-users').addEventListener('input', function() {
        searchUsers(this.value);
    });
}

// Switch between Direct and Group tabs
function switchTab(tab) {
    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    document.querySelector(`[data-tab="${tab}"]`).classList.add('active');

    document.getElementById('direct-list').classList.toggle('hidden', tab !== 'direct');
    document.getElementById('groups-list').classList.toggle('hidden', tab !== 'groups');
}

// WebSocket Connection
function connectWebSocket() {
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);

        // Notify server that user is online
        stompClient.send('/app/chat.addUser', {}, JSON.stringify({
            userId: currentUserId,
            username: currentUsername
        }));

        // Subscribe to public topic for user status updates
        stompClient.subscribe('/topic/public', function(message) {
            handlePublicMessage(JSON.parse(message.body));
        });

    }, function(error) {
        console.error('WebSocket connection error:', error);
        setTimeout(connectWebSocket, 5000); // Retry after 5 seconds
    });
}

// Handle public messages (user status updates)
function handlePublicMessage(message) {
    if (message.type === 'USER_ONLINE') {
        updateUserStatus(message.userId, 'online');
    } else if (message.type === 'USER_OFFLINE') {
        updateUserStatus(message.userId, 'offline');
    }
}

// Update user status in UI
function updateUserStatus(userId, status) {
    const userItems = document.querySelectorAll(`[data-user-id="${userId}"]`);
    userItems.forEach(item => {
        const statusEl = item.querySelector('.user-status');
        if (statusEl) {
            statusEl.className = `user-status ${status}`;
        }
    });
}

// Load conversations
function loadConversations() {
    fetch('/api/conversations')
        .then(response => response.json())
        .then(conversations => {
            // Since conversations are minimal, we'll build them from messages
            // For now, show empty state
            updateConversationsList([]);
        })
        .catch(error => console.error('Error loading conversations:', error));
}

// Load chat rooms
function loadChatRooms() {
    fetch('/api/chatrooms')
        .then(response => response.json())
        .then(chatRooms => {
            updateChatRoomsList(chatRooms);
        })
        .catch(error => console.error('Error loading chat rooms:', error));
}

// Update conversations list in UI
function updateConversationsList(conversations) {
    const container = document.getElementById('conversation-list');
    container.innerHTML = '';

    if (conversations.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #95a5a6;">No conversations yet</div>';
        return;
    }

    conversations.forEach(conv => {
        const item = createConversationItem(conv);
        container.appendChild(item);
    });
}

// Update chat rooms list in UI
function updateChatRoomsList(chatRooms) {
    const container = document.getElementById('chatroom-list');
    container.innerHTML = '';

    if (chatRooms.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #95a5a6;">No groups yet</div>';
        return;
    }

    chatRooms.forEach(room => {
        const item = createChatRoomItem(room);
        container.appendChild(item);
    });
}

// Create conversation item element
function createConversationItem(conversation) {
    const div = document.createElement('div');
    div.className = 'chat-item';
    div.setAttribute('data-conversation-id', conversation.id);
    div.innerHTML = `
        <div class="chat-item-header">
            <span class="chat-item-name">${conversation.recipientName}</span>
            <span class="chat-item-time">${formatTime(conversation.lastMessageAt)}</span>
        </div>
        <div class="chat-item-preview">${conversation.lastMessage || 'Start a conversation'}</div>
    `;
    div.addEventListener('click', () => openConversation(conversation.id, conversation.recipientName));
    return div;
}

// Create chat room item element
function createChatRoomItem(chatRoom) {
    const div = document.createElement('div');
    div.className = 'chat-item';
    div.setAttribute('data-chatroom-id', chatRoom.id);
    div.innerHTML = `
        <div class="chat-item-header">
            <span class="chat-item-name">${chatRoom.name}</span>
            <span class="chat-item-time">${formatTime(chatRoom.lastMessageAt)}</span>
        </div>
        <div class="chat-item-preview">${chatRoom.memberCount} members</div>
    `;
    div.addEventListener('click', () => openChatRoom(chatRoom.id, chatRoom.name));
    return div;
}

// Open conversation
function openConversation(conversationId, recipientName) {
    currentConversationId = conversationId;
    currentChatRoomId = null;
    currentChatType = 'conversation';

    // Update UI
    document.getElementById('welcome-screen').classList.add('hidden');
    document.getElementById('chat-window').classList.remove('hidden');
    document.getElementById('chat-title').textContent = recipientName;
    document.getElementById('chat-subtitle').textContent = 'Online';

    // Highlight active chat
    document.querySelectorAll('.chat-item').forEach(item => item.classList.remove('active'));
    document.querySelector(`[data-conversation-id="${conversationId}"]`)?.classList.add('active');

    // Unsubscribe from previous subscriptions
    if (stompClient && stompClient.connected) {
        stompClient.unsubscribe('/topic/conversation.*');

        // Subscribe to this conversation
        stompClient.subscribe(`/topic/conversation.${conversationId}`, function(message) {
            const msg = JSON.parse(message.body);
            displayMessage(msg);
        });

        // Subscribe to typing indicator
        stompClient.subscribe(`/topic/conversation.${conversationId}.typing`, function(message) {
            const data = JSON.parse(message.body);
            if (data.userId != currentUserId) {
                showTypingIndicator(data.fullName, data.isTyping);
            }
        });
    }

    // Load messages
    loadConversationMessages(conversationId);
}

// Open chat room
function openChatRoom(chatRoomId, chatRoomName) {
    currentChatRoomId = chatRoomId;
    currentConversationId = null;
    currentChatType = 'chatroom';

    // Update UI
    document.getElementById('welcome-screen').classList.add('hidden');
    document.getElementById('chat-window').classList.remove('hidden');
    document.getElementById('chat-title').textContent = chatRoomName;

    // Load member count
    fetch(`/api/chatrooms/${chatRoomId}/members`)
        .then(response => response.json())
        .then(members => {
            document.getElementById('chat-subtitle').textContent = `${members.length} members`;
        });

    // Highlight active chat
    document.querySelectorAll('.chat-item').forEach(item => item.classList.remove('active'));
    document.querySelector(`[data-chatroom-id="${chatRoomId}"]`)?.classList.add('active');

    // Unsubscribe from previous subscriptions
    if (stompClient && stompClient.connected) {
        stompClient.unsubscribe('/topic/chatroom.*');

        // Subscribe to this chat room
        stompClient.subscribe(`/topic/chatroom.${chatRoomId}`, function(message) {
            const msg = JSON.parse(message.body);
            displayMessage(msg);
        });

        // Subscribe to typing indicator
        stompClient.subscribe(`/topic/chatroom.${chatRoomId}.typing`, function(message) {
            const data = JSON.parse(message.body);
            if (data.userId != currentUserId) {
                showTypingIndicator(data.fullName, data.isTyping);
            }
        });
    }

    // Load messages
    loadChatRoomMessages(chatRoomId);
}

// Load conversation messages
function loadConversationMessages(conversationId) {
    fetch(`/api/conversations/${conversationId}/messages`)
        .then(response => response.json())
        .then(messages => {
            displayMessages(messages);
        })
        .catch(error => console.error('Error loading messages:', error));
}

// Load chat room messages
function loadChatRoomMessages(chatRoomId) {
    fetch(`/api/chatrooms/${chatRoomId}/messages`)
        .then(response => response.json())
        .then(messages => {
            displayMessages(messages);
        })
        .catch(error => console.error('Error loading messages:', error));
}

// Display messages
function displayMessages(messages) {
    const container = document.getElementById('messages-container');
    container.innerHTML = '';

    messages.forEach(message => {
        displayMessage(message);
    });

    scrollToBottom();
}

// Display single message
function displayMessage(message) {
    const container = document.getElementById('messages-container');
    const messageDiv = document.createElement('div');
    const isSent = message.senderId == currentUserId;

    messageDiv.className = `message ${isSent ? 'sent' : 'received'}`;
    messageDiv.innerHTML = `
        <div class="message-content">
            ${!isSent ? `<div class="message-sender">${message.senderName}</div>` : ''}
            <div class="message-text">${escapeHtml(message.content)}</div>
            <div class="message-time">${formatTime(message.createdAt)}</div>
        </div>
    `;

    container.appendChild(messageDiv);
    scrollToBottom();
}

// Send message
function sendMessage() {
    const input = document.getElementById('message-input');
    const content = input.value.trim();

    if (!content) return;
    if (!stompClient || !stompClient.connected) {
        alert('Connection lost. Please refresh the page.');
        return;
    }

    if (currentChatType === 'conversation' && currentConversationId) {
        stompClient.send('/app/chat.sendDirectMessage', {}, JSON.stringify({
            conversationId: currentConversationId,
            content: content
        }));
    } else if (currentChatType === 'chatroom' && currentChatRoomId) {
        stompClient.send('/app/chat.sendGroupMessage', {}, JSON.stringify({
            chatRoomId: currentChatRoomId,
            content: content
        }));
    }

    input.value = '';
}

// Handle typing indicator
function handleTyping() {
    if (!stompClient || !stompClient.connected) return;

    const chatId = currentChatType === 'conversation' ? currentConversationId : currentChatRoomId;
    if (!chatId) return;

    // Send typing start
    stompClient.send('/app/chat.typing', {}, JSON.stringify({
        chatType: currentChatType,
        chatId: chatId,
        isTyping: true
    }));

    // Clear previous timeout
    clearTimeout(typingTimeout);

    // Send typing stop after 1 second of inactivity
    typingTimeout = setTimeout(() => {
        stompClient.send('/app/chat.typing', {}, JSON.stringify({
            chatType: currentChatType,
            chatId: chatId,
            isTyping: false
        }));
    }, 1000);
}

// Show typing indicator
function showTypingIndicator(userName, isTyping) {
    const indicator = document.getElementById('typing-indicator');
    if (isTyping) {
        indicator.querySelector('span').textContent = userName;
        indicator.classList.remove('hidden');
    } else {
        indicator.classList.add('hidden');
    }
}

// Close chat
function closeChat() {
    currentConversationId = null;
    currentChatRoomId = null;
    currentChatType = null;

    document.getElementById('chat-window').classList.add('hidden');
    document.getElementById('welcome-screen').classList.remove('hidden');
    document.querySelectorAll('.chat-item').forEach(item => item.classList.remove('active'));
}

// Show new direct message modal
function showNewDirectModal() {
    document.getElementById('new-direct-modal').classList.remove('hidden');
    loadUsers();
}

// Show new group modal
function showNewGroupModal() {
    document.getElementById('new-group-modal').classList.remove('hidden');
}

// Load users for new conversation
function loadUsers() {
    fetch('/api/users')
        .then(response => response.json())
        .then(users => {
            displayUsersList(users);
        })
        .catch(error => console.error('Error loading users:', error));
}

// Display users list
function displayUsersList(users) {
    const container = document.getElementById('users-list');
    container.innerHTML = '';

    users.forEach(user => {
        const div = document.createElement('div');
        div.className = 'user-item';
        div.setAttribute('data-user-id', user.id);
        div.innerHTML = `
            <div class="user-item-name">${user.fullName}</div>
            <div class="user-item-details">${user.department || ''} ${user.designation ? '• ' + user.designation : ''}</div>
        `;
        div.addEventListener('click', () => startConversation(user.id, user.fullName));
        container.appendChild(div);
    });
}

// Search users
function searchUsers(query) {
    if (!query.trim()) {
        loadUsers();
        return;
    }

    fetch(`/api/users/search?query=${encodeURIComponent(query)}`)
        .then(response => response.json())
        .then(users => {
            displayUsersList(users);
        })
        .catch(error => console.error('Error searching users:', error));
}

// Start new conversation
function startConversation(recipientId, recipientName) {
    fetch('/api/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `recipientId=${recipientId}`
    })
    .then(response => response.json())
    .then(data => {
        document.getElementById('new-direct-modal').classList.add('hidden');
        openConversation(data.conversationId, recipientName);
        loadConversations(); // Refresh conversation list
    })
    .catch(error => console.error('Error creating conversation:', error));
}

// Create group
function createGroup() {
    const name = document.getElementById('group-name').value.trim();
    const description = document.getElementById('group-description').value.trim();

    if (!name) {
        alert('Please enter a group name');
        return;
    }

    fetch('/api/chatrooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            name: name,
            description: description,
            type: 'GROUP'
        })
    })
    .then(response => response.json())
    .then(chatRoom => {
        document.getElementById('new-group-modal').classList.add('hidden');
        document.getElementById('group-name').value = '';
        document.getElementById('group-description').value = '';
        loadChatRooms(); // Refresh chat rooms list
        openChatRoom(chatRoom.id, chatRoom.name);
    })
    .catch(error => console.error('Error creating group:', error));
}

// Utility functions
function formatTime(timestamp) {
    if (!timestamp) return '';
    const date = new Date(timestamp);
    const now = new Date();
    const diff = now - date;

    if (diff < 60000) return 'Just now';
    if (diff < 3600000) return Math.floor(diff / 60000) + 'm ago';
    if (diff < 86400000) return date.toLocaleTimeString('en-US', { hour: 'numeric', minute: '2-digit' });
    return date.toLocaleDateString('en-US', { month: 'short', day: 'numeric' });
}

function escapeHtml(text) {
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function scrollToBottom() {
    const container = document.getElementById('messages-container');
    container.scrollTop = container.scrollHeight;
}

// Handle page unload (set user offline)
window.addEventListener('beforeunload', function() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
});
