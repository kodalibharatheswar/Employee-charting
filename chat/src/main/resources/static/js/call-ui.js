/**
 * Call UI Manager
 * 
 * Handles all UI interactions for audio/video calls:
 * - Call buttons and controls
 * - Incoming call modal
 * - Active call interface
 * - Video/audio stream rendering
 * - Participant management
 * 
 * Dependencies: 
 * - webrtc.js (WebRTCManager)
 * - SockJS, STOMP (already loaded)
 * 
 * @author CRM Chat Module
 * @version 1.0.0
 */

class CallUIManager {
    constructor() {
        // UI Elements (will be initialized after DOM loads)
        this.elements = {
            // Call buttons
            audioCallBtn: null,
            videoCallBtn: null,
            screenShareBtn: null,
            
            // Modals
            incomingCallModal: null,
            activeCallModal: null,
            
            // Video containers
            localVideoContainer: null,
            remoteVideoContainer: null,
            localVideo: null,
            
            // Call controls
            muteBtn: null,
            cameraBtn: null,
            screenShareToggleBtn: null,
            endCallBtn: null,
            
            // Call info
            callStatusText: null,
            callerNameText: null,
            callDurationText: null
        };

        // State
        this.webrtcManager = null;
        this.remoteStreams = new Map(); // Map<userId, {stream, videoElement}>
        this.callStartTime = null;
        this.durationInterval = null;
        this.currentCallData = null;

        // UI State
        this.isMuted = false;
        this.isCameraOff = false;
        this.isScreenSharing = false;
    }

    /**
     * Initialize Call UI Manager
     * @param {WebRTCManager} webrtcManager - WebRTC Manager instance
     */
    initialize(webrtcManager) {
        this.webrtcManager = webrtcManager;

        // Set up WebRTC event handlers
        this.setupWebRTCEventHandlers();

        // Initialize UI elements
        this.initializeUIElements();

        // Set up event listeners
        this.setupEventListeners();

        console.log('Call UI Manager initialized');
    }

    /**
     * Initialize UI elements
     */
    initializeUIElements() {
        // Call buttons in chat header
        this.elements.audioCallBtn = document.getElementById('btn-audio-call');
        this.elements.videoCallBtn = document.getElementById('btn-video-call');
        this.elements.screenShareBtn = document.getElementById('btn-screen-share');

        // Modals
        this.elements.incomingCallModal = document.getElementById('incoming-call-modal');
        this.elements.activeCallModal = document.getElementById('active-call-modal');

        // Incoming call elements
        this.elements.callerNameText = document.getElementById('caller-name');
        this.elements.callTypeText = document.getElementById('call-type');
        this.elements.acceptCallBtn = document.getElementById('btn-accept-call');
        this.elements.rejectCallBtn = document.getElementById('btn-reject-call');

        // Active call elements
        this.elements.localVideoContainer = document.getElementById('local-video-container');
        this.elements.remoteVideoContainer = document.getElementById('remote-video-container');
        this.elements.callStatusText = document.getElementById('call-status');
        this.elements.callDurationText = document.getElementById('call-duration');

        // Call controls
        this.elements.muteBtn = document.getElementById('btn-mute');
        this.elements.cameraBtn = document.getElementById('btn-camera');
        this.elements.screenShareToggleBtn = document.getElementById('btn-toggle-screen-share');
        this.elements.endCallBtn = document.getElementById('btn-end-call');

        // Participant list (for group calls)
        this.elements.participantsList = document.getElementById('participants-list');
    }

    /**
     * Set up WebRTC event handlers
     */
    setupWebRTCEventHandlers() {
        // Handle remote stream
        this.webrtcManager.onRemoteStream = (userId, stream) => {
            this.handleRemoteStream(userId, stream);
        };

        // Handle remote stream removed
        this.webrtcManager.onRemoteStreamRemoved = (userId) => {
            this.removeRemoteStream(userId);
        };

        // Handle call ended
        this.webrtcManager.onCallEnded = () => {
            this.handleCallEnded();
        };

        // Handle call error
        this.webrtcManager.onCallError = (message) => {
            this.showError(message);
        };

        // Handle connection state changes
        this.webrtcManager.onConnectionStateChange = (userId, state) => {
            this.updateConnectionStatus(userId, state);
        };
    }

    /**
     * Set up event listeners for UI elements
     */
    setupEventListeners() {
        // Call initiation buttons
        if (this.elements.audioCallBtn) {
            this.elements.audioCallBtn.addEventListener('click', () => this.initiateCall('AUDIO'));
        }

        if (this.elements.videoCallBtn) {
            this.elements.videoCallBtn.addEventListener('click', () => this.initiateCall('VIDEO'));
        }

        if (this.elements.screenShareBtn) {
            this.elements.screenShareBtn.addEventListener('click', () => this.initiateCall('SCREEN_SHARE'));
        }

        // Incoming call buttons
        if (this.elements.acceptCallBtn) {
            this.elements.acceptCallBtn.addEventListener('click', () => this.acceptCall());
        }

        if (this.elements.rejectCallBtn) {
            this.elements.rejectCallBtn.addEventListener('click', () => this.rejectCall());
        }

        // Call control buttons
        if (this.elements.muteBtn) {
            this.elements.muteBtn.addEventListener('click', () => this.toggleMute());
        }

        if (this.elements.cameraBtn) {
            this.elements.cameraBtn.addEventListener('click', () => this.toggleCamera());
        }

        if (this.elements.screenShareToggleBtn) {
            this.elements.screenShareToggleBtn.addEventListener('click', () => this.toggleScreenShare());
        }

        if (this.elements.endCallBtn) {
            this.elements.endCallBtn.addEventListener('click', () => this.endCall());
        }
    }

    /**
     * Initiate a call
     * @param {string} callType - 'AUDIO', 'VIDEO', or 'SCREEN_SHARE'
     */
    async initiateCall(callType) {
        try {
            // Determine call mode based on current chat context
            const callMode = currentType === 'group' ? 'GROUP' : 'DIRECT';

            console.log(`Initiating ${callType} call (${callMode} mode)`);

            // Prepare call parameters
            const callParams = {
                callType: callType,
                callMode: callMode
            };

            if (callMode === 'DIRECT') {
                // Direct call
                if (!currentId || !currentConversationId) {
                    this.showError('Please select a user to call');
                    return;
                }
                
                callParams.conversationId = currentConversationId;
                callParams.recipientId = currentId;
            } else {
                // Group call
                if (!currentId) {
                    this.showError('Please select a group to call');
                    return;
                }
                
                callParams.chatRoomId = currentId;
                
                // Subscribe to group call channels
                this.webrtcManager.subscribeToGroupCall(currentId);
            }

            // Show loading state
            this.showCallStatus('Initiating call...');

            // Initiate call through WebRTC Manager
            const success = await this.webrtcManager.initiateCall(callParams);

            if (success) {
                // Show active call UI
                this.showActiveCallModal(callType);
                
                // Display local stream
                this.displayLocalStream();
                
                // Start call duration timer
                this.startCallDuration();
                
                this.showCallStatus('Calling...');
            }

        } catch (error) {
            console.error('Error initiating call:', error);
            this.showError('Failed to initiate call: ' + error.message);
        }
    }

    /**
     * Show incoming call modal
     * @param {Object} callData - Call notification data from server
     */
    showIncomingCall(callData) {
        console.log('Showing incoming call modal:', callData);

        this.currentCallData = callData;

        // Set caller information
        if (this.elements.callerNameText) {
            this.elements.callerNameText.textContent = callData.callerName;
        }

        // Set call type
        if (this.elements.callTypeText) {
            const callTypeLabel = this.getCallTypeLabel(callData.callType);
            this.elements.callTypeText.textContent = callTypeLabel;
        }

        // Show modal
        if (this.elements.incomingCallModal) {
            this.elements.incomingCallModal.classList.add('active');
        }

        // Play ringtone (optional)
        this.playRingtone();
    }

    /**
     * Accept incoming call
     */
    async acceptCall() {
        try {
            console.log('Accepting call');

            // Hide incoming call modal
            this.hideIncomingCallModal();

            // Stop ringtone
            this.stopRingtone();

            // Show loading state
            this.showCallStatus('Connecting...');

            // Accept call through WebRTC Manager
            const success = await this.webrtcManager.acceptCall();

            if (success) {
                // Show active call UI
                this.showActiveCallModal(this.currentCallData.callType);
                
                // Display local stream
                this.displayLocalStream();
                
                // Start call duration timer
                this.startCallDuration();
                
                this.showCallStatus('Connected');
            }

        } catch (error) {
            console.error('Error accepting call:', error);
            this.showError('Failed to accept call: ' + error.message);
        }
    }

    /**
     * Reject incoming call
     */
    rejectCall() {
        console.log('Rejecting call');

        // Reject through WebRTC Manager
        this.webrtcManager.rejectCall();

        // Hide incoming call modal
        this.hideIncomingCallModal();

        // Stop ringtone
        this.stopRingtone();
    }

    /**
     * Show active call modal
     * @param {string} callType - 'AUDIO', 'VIDEO', or 'SCREEN_SHARE'
     */
    showActiveCallModal(callType) {
        if (!this.elements.activeCallModal) return;

        this.elements.activeCallModal.classList.add('active');

        // Show/hide video containers based on call type
        if (callType === 'AUDIO') {
            // Audio only - hide video containers
            if (this.elements.localVideoContainer) {
                this.elements.localVideoContainer.style.display = 'none';
            }
            if (this.elements.remoteVideoContainer) {
                this.elements.remoteVideoContainer.classList.add('audio-only');
            }
        } else {
            // Video or Screen Share - show video containers
            if (this.elements.localVideoContainer) {
                this.elements.localVideoContainer.style.display = 'block';
            }
            if (this.elements.remoteVideoContainer) {
                this.elements.remoteVideoContainer.classList.remove('audio-only');
            }
        }

        // Hide camera button for audio calls
        if (this.elements.cameraBtn && callType === 'AUDIO') {
            this.elements.cameraBtn.style.display = 'none';
        }
    }

    /**
     * Display local stream in UI
     */
    displayLocalStream() {
        const localStream = this.webrtcManager.getLocalStream();
        
        if (!localStream) {
            console.warn('No local stream available');
            return;
        }

        // Create or update local video element
        let localVideo = document.getElementById('local-video');
        
        if (!localVideo) {
            localVideo = document.createElement('video');
            localVideo.id = 'local-video';
            localVideo.autoplay = true;
            localVideo.muted = true; // Mute local audio to prevent feedback
            localVideo.playsInline = true;
            
            if (this.elements.localVideoContainer) {
                this.elements.localVideoContainer.appendChild(localVideo);
            }
        }

        // Attach stream
        localVideo.srcObject = localStream;

        console.log('Local stream displayed');
    }

    /**
     * Handle remote stream from peer
     * @param {number} userId - Remote user ID
     * @param {MediaStream} stream - Remote media stream
     */
    handleRemoteStream(userId, stream) {
        console.log('Handling remote stream for user:', userId);

        // Create video element for remote stream
        const videoElement = document.createElement('video');
        videoElement.id = `remote-video-${userId}`;
        videoElement.autoplay = true;
        videoElement.playsInline = true;
        videoElement.srcObject = stream;

        // Add video element to remote container
        if (this.elements.remoteVideoContainer) {
            this.elements.remoteVideoContainer.appendChild(videoElement);
        }

        // Store reference
        this.remoteStreams.set(userId, {
            stream: stream,
            videoElement: videoElement
        });

        // Update call status
        this.showCallStatus('Connected');

        console.log('Remote stream displayed for user:', userId);
    }

    /**
     * Remove remote stream
     * @param {number} userId - Remote user ID
     */
    removeRemoteStream(userId) {
        console.log('Removing remote stream for user:', userId);

        const remoteData = this.remoteStreams.get(userId);
        
        if (remoteData && remoteData.videoElement) {
            // Remove video element from DOM
            remoteData.videoElement.remove();
        }

        // Remove from map
        this.remoteStreams.delete(userId);

        console.log('Remote stream removed for user:', userId);
    }

    /**
     * Toggle microphone (mute/unmute)
     */
    toggleMute() {
        const enabled = this.webrtcManager.toggleMicrophone();
        this.isMuted = !enabled;

        // Update button UI
        if (this.elements.muteBtn) {
            this.elements.muteBtn.classList.toggle('muted', this.isMuted);
            this.elements.muteBtn.innerHTML = this.isMuted 
                ? '<span class="icon">🔇</span><span>Unmute</span>' 
                : '<span class="icon">🎤</span><span>Mute</span>';
        }

        console.log('Microphone toggled:', enabled ? 'unmuted' : 'muted');
    }

    /**
     * Toggle camera (on/off)
     */
    toggleCamera() {
        const enabled = this.webrtcManager.toggleCamera();
        this.isCameraOff = !enabled;

        // Update button UI
        if (this.elements.cameraBtn) {
            this.elements.cameraBtn.classList.toggle('camera-off', this.isCameraOff);
            this.elements.cameraBtn.innerHTML = this.isCameraOff 
                ? '<span class="icon">📷</span><span>Camera On</span>' 
                : '<span class="icon">📹</span><span>Camera Off</span>';
        }

        // Hide/show local video
        const localVideo = document.getElementById('local-video');
        if (localVideo) {
            localVideo.style.visibility = enabled ? 'visible' : 'hidden';
        }

        console.log('Camera toggled:', enabled ? 'on' : 'off');
    }

    /**
     * Toggle screen sharing
     */
    async toggleScreenShare() {
        try {
            if (this.isScreenSharing) {
                // Stop screen sharing
                await this.webrtcManager.stopScreenShare();
                this.isScreenSharing = false;
                
                // Update button
                if (this.elements.screenShareToggleBtn) {
                    this.elements.screenShareToggleBtn.textContent = 'Share Screen';
                }
                
                // Switch back to camera view
                this.displayLocalStream();
                
            } else {
                // Start screen sharing
                const success = await this.webrtcManager.startScreenShare();
                
                if (success) {
                    this.isScreenSharing = true;
                    
                    // Update button
                    if (this.elements.screenShareToggleBtn) {
                        this.elements.screenShareToggleBtn.textContent = 'Stop Sharing';
                    }
                    
                    // Display screen share stream
                    this.displayScreenShare();
                }
            }

        } catch (error) {
            console.error('Error toggling screen share:', error);
            this.showError('Failed to toggle screen share');
        }
    }

    /**
     * Display screen share stream
     */
    displayScreenShare() {
        const screenStream = this.webrtcManager.getScreenStream();
        
        if (!screenStream) return;

        const localVideo = document.getElementById('local-video');
        if (localVideo) {
            localVideo.srcObject = screenStream;
        }

        console.log('Screen share displayed');
    }

    /**
     * End call
     */
    endCall() {
        console.log('Ending call');

        // End call through WebRTC Manager
        this.webrtcManager.endCall();

        // Clean up UI
        this.handleCallEnded();
    }

    /**
     * Handle call ended
     */
    handleCallEnded() {
        console.log('Call ended - cleaning up UI');

        // Stop call duration timer
        this.stopCallDuration();

        // Hide active call modal
        this.hideActiveCallModal();

        // Clear remote streams
        this.clearRemoteStreams();

        // Reset state
        this.isMuted = false;
        this.isCameraOff = false;
        this.isScreenSharing = false;
        this.currentCallData = null;

        console.log('UI cleanup complete');
    }

    /**
     * Hide incoming call modal
     */
    hideIncomingCallModal() {
        if (this.elements.incomingCallModal) {
            this.elements.incomingCallModal.classList.remove('active');
        }
    }

    /**
     * Hide active call modal
     */
    hideActiveCallModal() {
        if (this.elements.activeCallModal) {
            this.elements.activeCallModal.classList.remove('active');
        }
    }

    /**
     * Clear all remote streams
     */
    clearRemoteStreams() {
        this.remoteStreams.forEach((remoteData, userId) => {
            if (remoteData.videoElement) {
                remoteData.videoElement.remove();
            }
        });

        this.remoteStreams.clear();

        // Clear remote container
        if (this.elements.remoteVideoContainer) {
            this.elements.remoteVideoContainer.innerHTML = '';
        }

        // Clear local video
        const localVideo = document.getElementById('local-video');
        if (localVideo) {
            localVideo.remove();
        }
    }

    /**
     * Start call duration timer
     */
    startCallDuration() {
        this.callStartTime = Date.now();
        
        this.durationInterval = setInterval(() => {
            const duration = Math.floor((Date.now() - this.callStartTime) / 1000);
            const formatted = this.formatDuration(duration);
            
            if (this.elements.callDurationText) {
                this.elements.callDurationText.textContent = formatted;
            }
        }, 1000);
    }

    /**
     * Stop call duration timer
     */
    stopCallDuration() {
        if (this.durationInterval) {
            clearInterval(this.durationInterval);
            this.durationInterval = null;
        }
        
        this.callStartTime = null;
        
        if (this.elements.callDurationText) {
            this.elements.callDurationText.textContent = '00:00';
        }
    }

    /**
     * Format duration in MM:SS format
     * @param {number} seconds - Duration in seconds
     * @returns {string} Formatted duration
     */
    formatDuration(seconds) {
        const mins = Math.floor(seconds / 60);
        const secs = seconds % 60;
        return `${mins.toString().padStart(2, '0')}:${secs.toString().padStart(2, '0')}`;
    }

    /**
     * Show call status
     * @param {string} status - Status message
     */
    showCallStatus(status) {
        if (this.elements.callStatusText) {
            this.elements.callStatusText.textContent = status;
        }
        console.log('Call status:', status);
    }

    /**
     * Update connection status for a user
     * @param {number} userId - User ID
     * @param {string} state - Connection state
     */
    updateConnectionStatus(userId, state) {
        console.log(`Connection status for user ${userId}:`, state);

        // You can add visual indicators here
        // For example, show "Reconnecting..." if state is 'disconnected'
        if (state === 'connected') {
            this.showCallStatus('Connected');
        } else if (state === 'connecting') {
            this.showCallStatus('Connecting...');
        } else if (state === 'disconnected') {
            this.showCallStatus('Reconnecting...');
        } else if (state === 'failed') {
            this.showError('Connection failed');
        }
    }

    /**
     * Update participant media status (for group calls)
     * @param {Object} data - Media toggle notification
     */
    updateParticipantMedia(data) {
        console.log('Participant media updated:', data);

        // Update participant UI based on media state
        // This is useful for showing mute/camera off indicators in group calls
        
        const participantElement = document.getElementById(`participant-${data.userId}`);
        if (participantElement) {
            if (data.type === 'MIC_TOGGLED') {
                participantElement.classList.toggle('muted', !data.enabled);
            } else if (data.type === 'CAMERA_TOGGLED') {
                participantElement.classList.toggle('camera-off', !data.enabled);
            }
        }
    }

    /**
     * Show error message
     * @param {string} message - Error message
     */
    showError(message) {
        console.error('Call error:', message);
        
        // Show error notification to user
        if (window.showNotification) {
            window.showNotification('error', message);
        } else {
            alert(message);
        }
    }

    /**
     * Get call type label
     * @param {string} callType - 'AUDIO', 'VIDEO', or 'SCREEN_SHARE'
     * @returns {string} Human-readable label
     */
    getCallTypeLabel(callType) {
        switch (callType) {
            case 'AUDIO':
                return 'Audio Call';
            case 'VIDEO':
                return 'Video Call';
            case 'SCREEN_SHARE':
                return 'Screen Share';
            default:
                return 'Call';
        }
    }

    /**
     * Play ringtone for incoming call
     */
    playRingtone() {
        // Implement ringtone playback
        // You can use Web Audio API or <audio> element
        console.log('Playing ringtone...');
        
        // Example:
        // const audio = new Audio('/sounds/ringtone.mp3');
        // audio.loop = true;
        // audio.play();
    }

    /**
     * Stop ringtone
     */
    stopRingtone() {
        console.log('Stopping ringtone...');
        
        // Stop ringtone playback
        // Example:
        // if (this.ringtoneAudio) {
        //     this.ringtoneAudio.pause();
        //     this.ringtoneAudio.currentTime = 0;
        // }
    }

    /**
     * Check if call buttons should be visible
     */
    updateCallButtonsVisibility() {
        const hasActiveChat = currentId !== null;
        
        if (this.elements.audioCallBtn) {
            this.elements.audioCallBtn.style.display = hasActiveChat ? 'block' : 'none';
        }
        if (this.elements.videoCallBtn) {
            this.elements.videoCallBtn.style.display = hasActiveChat ? 'block' : 'none';
        }
        if (this.elements.screenShareBtn) {
            this.elements.screenShareBtn.style.display = hasActiveChat ? 'block' : 'none';
        }
    }
}

// Export for global use
window.CallUIManager = CallUIManager;

// Global function to show incoming call (called by WebRTCManager)
window.showIncomingCallModal = function(callData) {
    if (window.callUIManager) {
        window.callUIManager.showIncomingCall(callData);
    }
};

// Global function to update participant media (called by WebRTCManager)
window.updateParticipantMedia = function(data) {
    if (window.callUIManager) {
        window.callUIManager.updateParticipantMedia(data);
    }
};