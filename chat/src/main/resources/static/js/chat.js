// Global variables
let stompClient = null;
let currentConversationId = null;
let currentChatRoomId = null;
let currentChatType = null;
let currentUserId = null;
let currentUsername = null;
let typingTimeout = null;

// Initialize on page load
document.addEventListener('DOMContentLoaded', function() {
    console.log('Page loaded, initializing...');

    currentUserId = document.getElementById('currentUserId').value;
    currentUsername = document.getElementById('currentUsername').value;

    console.log('Current User ID:', currentUserId);
    console.log('Current Username:', currentUsername);

    initializeEventListeners();
    connectWebSocket();
    loadConversations();
    loadChatRooms();
});

// Initialize event listeners
function initializeEventListeners() {
    console.log('Initializing event listeners...');

    // Tab switching
    const tabButtons = document.querySelectorAll('.tab-btn');
    console.log('Found tab buttons:', tabButtons.length);
    tabButtons.forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            const tab = this.getAttribute('data-tab');
            console.log('Tab clicked:', tab);
            switchTab(tab);
        });
    });

    // New chat buttons
    const btnNewDirect = document.getElementById('btn-new-direct');
    const btnNewGroup = document.getElementById('btn-new-group');

    console.log('New direct button:', btnNewDirect);
    console.log('New group button:', btnNewGroup);

    if (btnNewDirect) {
        btnNewDirect.addEventListener('click', function(e) {
            e.preventDefault();
            console.log('New direct button clicked');
            showNewDirectModal();
        });
    }

    if (btnNewGroup) {
        btnNewGroup.addEventListener('click', function(e) {
            e.preventDefault();
            console.log('New group button clicked');
            showNewGroupModal();
        });
    }

    // Send message
    const sendButton = document.getElementById('send-button');
    const messageInput = document.getElementById('message-input');

    if (sendButton) {
        sendButton.addEventListener('click', function(e) {
            e.preventDefault();
            sendMessage();
        });
    }

    if (messageInput) {
        messageInput.addEventListener('keypress', function(e) {
            if (e.key === 'Enter') {
                e.preventDefault();
                sendMessage();
            }
        });

        // Typing indicator
        messageInput.addEventListener('input', handleTyping);
    }

    // Modal close buttons
    document.querySelectorAll('.close').forEach(btn => {
        btn.addEventListener('click', function(e) {
            e.preventDefault();
            const modal = this.closest('.modal');
            if (modal) {
                modal.classList.add('hidden');
            }
        });
    });

    // Create group button
    const createGroupBtn = document.getElementById('create-group-btn');
    if (createGroupBtn) {
        createGroupBtn.addEventListener('click', function(e) {
            e.preventDefault();
            createGroup();
        });
    }

    // Close chat button
    const btnCloseChat = document.getElementById('btn-close-chat');
    if (btnCloseChat) {
        btnCloseChat.addEventListener('click', function(e) {
            e.preventDefault();
            closeChat();
        });
    }

    // Search users
    const searchUsers = document.getElementById('search-users');
    if (searchUsers) {
        searchUsers.addEventListener('input', function() {
            searchUsersFunc(this.value);
        });
    }
}

// Switch between Direct and Group tabs
function switchTab(tab) {
    console.log('Switching to tab:', tab);

    document.querySelectorAll('.tab-btn').forEach(btn => btn.classList.remove('active'));
    const activeTab = document.querySelector(`[data-tab="${tab}"]`);
    if (activeTab) {
        activeTab.classList.add('active');
    }

    const directList = document.getElementById('direct-list');
    const groupsList = document.getElementById('groups-list');

    if (tab === 'direct') {
        directList.classList.remove('hidden');
        groupsList.classList.add('hidden');
    } else {
        directList.classList.add('hidden');
        groupsList.classList.remove('hidden');
    }
}

// WebSocket Connection
function connectWebSocket() {
    console.log('Connecting to WebSocket...');
    const socket = new SockJS('/ws');
    stompClient = Stomp.over(socket);

    stompClient.connect({}, function(frame) {
        console.log('Connected: ' + frame);

        stompClient.send('/app/chat.addUser', {}, JSON.stringify({
            userId: currentUserId,
            username: currentUsername
        }));

        stompClient.subscribe('/topic/public', function(message) {
            handlePublicMessage(JSON.parse(message.body));
        });

    }, function(error) {
        console.error('WebSocket connection error:', error);
        setTimeout(connectWebSocket, 5000);
    });
}

// Handle public messages
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
    console.log('Loading conversations...');
    const container = document.getElementById('conversation-list');
    if (container) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #95a5a6;">No conversations yet</div>';
    }
}

// Load chat rooms
function loadChatRooms() {
    console.log('Loading chat rooms...');
    fetch('/api/chatrooms')
        .then(response => {
            if (!response.ok) {
                throw new Error('Failed to load chat rooms');
            }
            return response.json();
        })
        .then(chatRooms => {
            console.log('Chat rooms loaded:', chatRooms);
            updateChatRoomsList(chatRooms);
        })
        .catch(error => {
            console.error('Error loading chat rooms:', error);
            const container = document.getElementById('chatroom-list');
            if (container) {
                container.innerHTML = '<div style="padding: 20px; text-align: center; color: #e74c3c;">Failed to load groups</div>';
            }
        });
}

// Update chat rooms list in UI
function updateChatRoomsList(chatRooms) {
    const container = document.getElementById('chatroom-list');
    if (!container) return;

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

// Create chat room item element
function createChatRoomItem(chatRoom) {
    const div = document.createElement('div');
    div.className = 'chat-item';
    div.setAttribute('data-chatroom-id', chatRoom.id);
    div.innerHTML = `
        <div class="chat-item-header">
            <span class="chat-item-name">${escapeHtml(chatRoom.name)}</span>
            <span class="chat-item-time">${formatTime(chatRoom.lastMessageAt)}</span>
        </div>
        <div class="chat-item-preview">${chatRoom.memberCount} members</div>
    `;
    div.addEventListener('click', () => openChatRoom(chatRoom.id, chatRoom.name));
    return div;
}

// Open chat room
function openChatRoom(chatRoomId, chatRoomName) {
    console.log('Opening chat room:', chatRoomId, chatRoomName);

    // Validate chatRoomId
    if (!chatRoomId || chatRoomId === 'undefined') {
        console.error('Invalid chatRoomId:', chatRoomId);
        alert('Error: Invalid chat room ID');
        return;
    }

    currentChatRoomId = chatRoomId;
    currentConversationId = null;
    currentChatType = 'chatroom';

    document.getElementById('welcome-screen').classList.add('hidden');
    document.getElementById('chat-window').classList.remove('hidden');
    document.getElementById('chat-title').textContent = chatRoomName;

    fetch(`/api/chatrooms/${chatRoomId}/members`)
        .then(response => {
            if (!response.ok) throw new Error('Failed to load members');
            return response.json();
        })
        .then(members => {
            document.getElementById('chat-subtitle').textContent = `${members.length} members`;
        })
        .catch(error => {
            console.error('Error loading members:', error);
            document.getElementById('chat-subtitle').textContent = 'Group chat';
        });

    document.querySelectorAll('.chat-item').forEach(item => item.classList.remove('active'));
    const activeItem = document.querySelector(`[data-chatroom-id="${chatRoomId}"]`);
    if (activeItem) activeItem.classList.add('active');

    if (stompClient && stompClient.connected) {
        stompClient.subscribe(`/topic/chatroom.${chatRoomId}`, function(message) {
            const msg = JSON.parse(message.body);
            displayMessage(msg);
        });

        stompClient.subscribe(`/topic/chatroom.${chatRoomId}.typing`, function(message) {
            const data = JSON.parse(message.body);
            if (data.userId != currentUserId) {
                showTypingIndicator(data.fullName, data.isTyping);
            }
        });
    }

    loadChatRoomMessages(chatRoomId);
}

// Open conversation
function openConversation(conversationId, recipientName) {
    console.log('Opening conversation:', conversationId, recipientName);

    // Validate conversationId
    if (!conversationId || conversationId === 'undefined') {
        console.error('Invalid conversationId:', conversationId);
        alert('Error: Invalid conversation ID');
        return;
    }

    currentConversationId = conversationId;
    currentChatRoomId = null;
    currentChatType = 'conversation';

    document.getElementById('welcome-screen').classList.add('hidden');
    document.getElementById('chat-window').classList.remove('hidden');
    document.getElementById('chat-title').textContent = recipientName;
    document.getElementById('chat-subtitle').textContent = 'Online';

    document.querySelectorAll('.chat-item').forEach(item => item.classList.remove('active'));
    const activeItem = document.querySelector(`[data-conversation-id="${conversationId}"]`);
    if (activeItem) activeItem.classList.add('active');

    if (stompClient && stompClient.connected) {
        stompClient.subscribe(`/topic/conversation.${conversationId}`, function(message) {
            const msg = JSON.parse(message.body);
            displayMessage(msg);
        });

        stompClient.subscribe(`/topic/conversation.${conversationId}.typing`, function(message) {
            const data = JSON.parse(message.body);
            if (data.userId != currentUserId) {
                showTypingIndicator(data.fullName, data.isTyping);
            }
        });
    }

    loadConversationMessages(conversationId);
}

// Load conversation messages
function loadConversationMessages(conversationId) {
    if (!conversationId || conversationId === 'undefined') {
        console.error('Cannot load messages: invalid conversationId');
        return;
    }

    fetch(`/api/conversations/${conversationId}/messages`)
        .then(response => {
            if (!response.ok) throw new Error('Failed to load messages');
            return response.json();
        })
        .then(messages => {
            displayMessages(messages);
        })
        .catch(error => {
            console.error('Error loading messages:', error);
            const container = document.getElementById('messages-container');
            container.innerHTML = '<div style="padding: 20px; text-align: center; color: #e74c3c;">Failed to load messages</div>';
        });
}

// Load chat room messages
function loadChatRoomMessages(chatRoomId) {
    if (!chatRoomId || chatRoomId === 'undefined') {
        console.error('Cannot load messages: invalid chatRoomId');
        return;
    }

    fetch(`/api/chatrooms/${chatRoomId}/messages`)
        .then(response => {
            if (!response.ok) throw new Error('Failed to load messages');
            return response.json();
        })
        .then(messages => {
            displayMessages(messages);
        })
        .catch(error => {
            console.error('Error loading messages:', error);
            const container = document.getElementById('messages-container');
            container.innerHTML = '<div style="padding: 20px; text-align: center; color: #e74c3c;">Failed to load messages</div>';
        });
}

// Display messages
function displayMessages(messages) {
    const container = document.getElementById('messages-container');
    container.innerHTML = '';

    if (messages.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #95a5a6;">No messages yet. Start the conversation!</div>';
        return;
    }

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
            ${!isSent ? `<div class="message-sender">${escapeHtml(message.senderName)}</div>` : ''}
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

    if (!content) {
        console.log('Message is empty, not sending');
        return;
    }

    if (!stompClient || !stompClient.connected) {
        alert('Connection lost. Please refresh the page.');
        return;
    }

    // Validate IDs before sending
    if (currentChatType === 'conversation') {
        if (!currentConversationId || currentConversationId === 'undefined') {
            console.error('Cannot send message: Invalid conversationId');
            alert('Error: No conversation selected');
            return;
        }

        console.log('Sending direct message to conversation:', currentConversationId);
        stompClient.send('/app/chat.sendDirectMessage', {}, JSON.stringify({
            conversationId: currentConversationId,
            content: content
        }));
    } else if (currentChatType === 'chatroom') {
        if (!currentChatRoomId || currentChatRoomId === 'undefined') {
            console.error('Cannot send message: Invalid chatRoomId');
            alert('Error: No chat room selected');
            return;
        }

        console.log('Sending group message to chatroom:', currentChatRoomId);
        stompClient.send('/app/chat.sendGroupMessage', {}, JSON.stringify({
            chatRoomId: currentChatRoomId,
            content: content
        }));
    } else {
        console.error('Cannot send message: No chat selected');
        alert('Please select a conversation or group first');
        return;
    }

    input.value = '';
}

// Handle typing indicator
function handleTyping() {
    if (!stompClient || !stompClient.connected) return;

    const chatId = currentChatType === 'conversation' ? currentConversationId : currentChatRoomId;
    if (!chatId || chatId === 'undefined') return;

    stompClient.send('/app/chat.typing', {}, JSON.stringify({
        chatType: currentChatType,
        chatId: chatId,
        isTyping: true
    }));

    clearTimeout(typingTimeout);

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
    console.log('Showing new direct modal');
    const modal = document.getElementById('new-direct-modal');
    if (modal) {
        modal.classList.remove('hidden');
        loadUsers();
    }
}

// Show new group modal
function showNewGroupModal() {
    console.log('Showing new group modal');
    const modal = document.getElementById('new-group-modal');
    if (modal) {
        modal.classList.remove('hidden');
    }
}

// Load users for new conversation
function loadUsers() {
    console.log('Loading users...');
    fetch('/api/users')
        .then(response => {
            if (!response.ok) throw new Error('Failed to load users');
            return response.json();
        })
        .then(users => {
            console.log('Users loaded:', users);
            displayUsersList(users);
        })
        .catch(error => {
            console.error('Error loading users:', error);
            const container = document.getElementById('users-list');
            if (container) {
                container.innerHTML = '<div style="padding: 20px; text-align: center; color: #e74c3c;">Failed to load users</div>';
            }
        });
}

// Display users list
function displayUsersList(users) {
    const container = document.getElementById('users-list');
    if (!container) return;

    container.innerHTML = '';

    if (users.length === 0) {
        container.innerHTML = '<div style="padding: 20px; text-align: center; color: #95a5a6;">No other users available</div>';
        return;
    }

    users.forEach(user => {
        const div = document.createElement('div');
        div.className = 'user-item';
        div.setAttribute('data-user-id', user.id);
        div.innerHTML = `
            <div class="user-item-name">${escapeHtml(user.fullName)}</div>
            <div class="user-item-details">${escapeHtml(user.department || '')} ${user.designation ? '• ' + escapeHtml(user.designation) : ''}</div>
        `;
        div.addEventListener('click', () => startConversation(user.id, user.fullName));
        container.appendChild(div);
    });
}

// Search users
function searchUsersFunc(query) {
    if (!query.trim()) {
        loadUsers();
        return;
    }

    fetch(`/api/users/search?query=${encodeURIComponent(query)}`)
        .then(response => {
            if (!response.ok) throw new Error('Search failed');
            return response.json();
        })
        .then(users => {
            displayUsersList(users);
        })
        .catch(error => {
            console.error('Error searching users:', error);
        });
}

// Start new conversation
function startConversation(recipientId, recipientName) {
    console.log('Starting conversation with:', recipientId, recipientName);

    if (!recipientId || recipientId === 'undefined') {
        console.error('Invalid recipientId');
        alert('Error: Invalid user selected');
        return;
    }

    fetch('/api/conversations', {
        method: 'POST',
        headers: { 'Content-Type': 'application/x-www-form-urlencoded' },
        body: `recipientId=${recipientId}`
    })
    .then(response => {
        if (!response.ok) throw new Error('Failed to create conversation');
        return response.json();
    })
    .then(data => {
        console.log('Conversation created:', data);
        document.getElementById('new-direct-modal').classList.add('hidden');

        if (data.conversationId && data.conversationId !== 'undefined') {
            openConversation(data.conversationId, recipientName);
            loadConversations();
        } else {
            console.error('Invalid conversationId returned:', data);
            alert('Error: Failed to create conversation');
        }
    })
    .catch(error => {
        console.error('Error creating conversation:', error);
        alert('Failed to create conversation. Please try again.');
    });
}

// Create group
function createGroup() {
    const name = document.getElementById('group-name').value.trim();
    const description = document.getElementById('group-description').value.trim();

    if (!name) {
        alert('Please enter a group name');
        return;
    }

    console.log('Creating group:', name);
    fetch('/api/chatrooms', {
        method: 'POST',
        headers: { 'Content-Type': 'application/json' },
        body: JSON.stringify({
            name: name,
            description: description,
            type: 'GROUP'
        })
    })
    .then(response => {
        if (!response.ok) throw new Error('Failed to create group');
        return response.json();
    })
    .then(chatRoom => {
        console.log('Group created:', chatRoom);
        document.getElementById('new-group-modal').classList.add('hidden');
        document.getElementById('group-name').value = '';
        document.getElementById('group-description').value = '';
        loadChatRooms();

        if (chatRoom.id && chatRoom.id !== 'undefined') {
            openChatRoom(chatRoom.id, chatRoom.name);
        } else {
            console.error('Invalid chatRoomId returned:', chatRoom);
        }
    })
    .catch(error => {
        console.error('Error creating group:', error);
        alert('Failed to create group. Please try again.');
    });
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
    if (!text) return '';
    const div = document.createElement('div');
    div.textContent = text;
    return div.innerHTML;
}

function scrollToBottom() {
    const container = document.getElementById('messages-container');
    if (container) {
        container.scrollTop = container.scrollHeight;
    }
}

// Handle page unload
window.addEventListener('beforeunload', function() {
    if (stompClient && stompClient.connected) {
        stompClient.disconnect();
    }
});
