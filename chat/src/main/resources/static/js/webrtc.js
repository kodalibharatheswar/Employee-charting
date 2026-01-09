/**
 * WebRTC Connection Manager
 * 
 * Handles all WebRTC peer connections for audio/video calls and screen sharing
 * Supports both direct (1-on-1) and group calls
 * 
 * Dependencies: SockJS, STOMP (already loaded in chat.html)
 * 
 * @author CRM Chat Module
 * @version 1.0.0
 */

class WebRTCManager {
    constructor() {
        // WebRTC Configuration
        this.config = {
            iceServers: [
                // STUN Servers (from application.properties)
                { urls: 'stun:stun.l.google.com:19302' },
                { urls: 'stun:stun1.l.google.com:19302' },
                { urls: 'stun:stun2.l.google.com:19302' },
                { urls: 'stun:stun3.l.google.com:19302' },
                { urls: 'stun:stun4.l.google.com:19302' },
                
                // TURN Servers (from application.properties)
                {
                    urls: 'turn:openrelay.metered.ca:80',
                    username: 'openrelayproject',
                    credential: 'openrelayproject'
                },
                {
                    urls: 'turns:openrelay.metered.ca:443',
                    username: 'openrelayproject',
                    credential: 'openrelayproject'
                }
            ],
            iceTransportPolicy: 'all', // 'all' or 'relay' (relay forces TURN usage)
            iceCandidatePoolSize: 10
        };

        // Media Constraints
        this.constraints = {
            audio: {
                echoCancellation: true,
                noiseSuppression: true,
                autoGainControl: true
            },
            video: {
                width: { ideal: 1280, max: 1280 },
                height: { ideal: 720, max: 720 },
                frameRate: { ideal: 30, max: 30 }
            },
            screenShare: {
                video: {
                    width: { ideal: 1920, max: 1920 },
                    height: { ideal: 1080, max: 1080 },
                    frameRate: { ideal: 15, max: 15 }
                },
                audio: false // Set to true if you want to share system audio
            }
        };

        // State Management
        this.peerConnections = new Map(); // Map<userId, RTCPeerConnection>
        this.localStream = null;
        this.screenStream = null;
        this.currentCall = null;
        this.isInitiator = false;
        this.callType = null; // 'AUDIO', 'VIDEO', 'SCREEN_SHARE'
        this.callMode = null; // 'DIRECT', 'GROUP'

        // Event Handlers (to be set by UI code)
        this.onRemoteStream = null;
        this.onRemoteStreamRemoved = null;
        this.onCallEnded = null;
        this.onCallError = null;
        this.onConnectionStateChange = null;
        this.onIceConnectionStateChange = null;
    }

    /**
     * Initialize WebRTC Manager
     * Must be called after STOMP connection is established
     */
    initialize(stompClient) {
        this.stompClient = stompClient;
        console.log('WebRTC Manager initialized');
        
        // Subscribe to WebRTC signaling channels
        this.subscribeToSignaling();
    }

    /**
     * Subscribe to WebRTC signaling messages
     */
    subscribeToSignaling() {
        // User-specific call signals (for direct calls)
        this.stompClient.subscribe('/user/queue/call.signal', (message) => {
            this.handleSignalingMessage(JSON.parse(message.body));
        });

        // User-specific call notifications
        this.stompClient.subscribe('/user/queue/call', (message) => {
            this.handleCallNotification(JSON.parse(message.body));
        });

        console.log('Subscribed to WebRTC signaling channels');
    }

    /**
     * Subscribe to group call channels
     */
    subscribeToGroupCall(chatRoomId) {
        // Group call signals
        this.stompClient.subscribe(`/topic/chatroom.${chatRoomId}.signal`, (message) => {
            this.handleSignalingMessage(JSON.parse(message.body));
        });

        // Group call notifications
        this.stompClient.subscribe(`/topic/chatroom.${chatRoomId}.call`, (message) => {
            this.handleCallNotification(JSON.parse(message.body));
        });

        console.log(`Subscribed to group call channels for room ${chatRoomId}`);
    }

    /**
     * Initiate a call (audio, video, or screen share)
     * 
     * @param {Object} params - Call parameters
     * @param {string} params.callType - 'AUDIO', 'VIDEO', or 'SCREEN_SHARE'
     * @param {string} params.callMode - 'DIRECT' or 'GROUP'
     * @param {number} params.conversationId - For direct calls
     * @param {number} params.chatRoomId - For group calls
     * @param {number} params.recipientId - For direct calls
     */
    async initiateCall({ callType, callMode, conversationId, chatRoomId, recipientId }) {
        try {
            console.log(`Initiating ${callType} call (${callMode} mode)`);
            
            this.callType = callType;
            this.callMode = callMode;
            this.isInitiator = true;

            // Get local media stream
            await this.getLocalStream(callType);

            // Send call initiation request to server
            const callData = {
                callType: callType,
                callMode: callMode,
                conversationId: conversationId,
                chatRoomId: chatRoomId,
                recipientId: recipientId
            };

            this.stompClient.send('/app/call.initiate', {}, JSON.stringify(callData));

            console.log('Call initiation request sent');
            return true;

        } catch (error) {
            console.error('Failed to initiate call:', error);
            this.handleError('Failed to start call: ' + error.message);
            return false;
        }
    }

    /**
     * Get local media stream based on call type
     */
    async getLocalStream(callType) {
        try {
            if (callType === 'SCREEN_SHARE') {
                // Get screen sharing stream
                this.screenStream = await navigator.mediaDevices.getDisplayMedia(
                    this.constraints.screenShare
                );
                
                // Also get audio for voice during screen share
                this.localStream = await navigator.mediaDevices.getUserMedia({ 
                    audio: this.constraints.audio, 
                    video: false 
                });

                // Detect when user stops screen sharing via browser UI
                this.screenStream.getVideoTracks()[0].onended = () => {
                    console.log('Screen sharing stopped by user');
                    this.stopScreenShare();
                };

            } else if (callType === 'VIDEO') {
                // Get video + audio stream
                this.localStream = await navigator.mediaDevices.getUserMedia({
                    audio: this.constraints.audio,
                    video: this.constraints.video
                });

            } else if (callType === 'AUDIO') {
                // Get audio-only stream
                this.localStream = await navigator.mediaDevices.getUserMedia({
                    audio: this.constraints.audio,
                    video: false
                });
            }

            console.log('Local stream acquired:', callType);
            return this.localStream;

        } catch (error) {
            console.error('Error getting user media:', error);
            
            // User-friendly error messages
            if (error.name === 'NotAllowedError') {
                throw new Error('Permission denied. Please allow camera/microphone access.');
            } else if (error.name === 'NotFoundError') {
                throw new Error('Camera or microphone not found.');
            } else if (error.name === 'NotReadableError') {
                throw new Error('Camera or microphone is already in use.');
            } else {
                throw new Error('Failed to access camera/microphone: ' + error.message);
            }
        }
    }

    /**
     * Create peer connection for a specific user
     */
    async createPeerConnection(userId) {
        try {
            console.log(`Creating peer connection for user ${userId}`);

            const peerConnection = new RTCPeerConnection(this.config);
            this.peerConnections.set(userId, peerConnection);

            // Add local stream tracks to peer connection
            if (this.localStream) {
                this.localStream.getTracks().forEach(track => {
                    peerConnection.addTrack(track, this.localStream);
                });
            }

            // Add screen share tracks if available
            if (this.screenStream) {
                this.screenStream.getTracks().forEach(track => {
                    peerConnection.addTrack(track, this.screenStream);
                });
            }

            // Handle incoming remote tracks
            peerConnection.ontrack = (event) => {
                console.log('Received remote track from user', userId);
                if (this.onRemoteStream) {
                    this.onRemoteStream(userId, event.streams[0]);
                }
            };

            // Handle ICE candidates
            peerConnection.onicecandidate = (event) => {
                if (event.candidate) {
                    console.log('New ICE candidate for user', userId);
                    this.sendIceCandidate(event.candidate, userId);
                }
            };

            // Monitor connection state
            peerConnection.onconnectionstatechange = () => {
                console.log(`Connection state for user ${userId}:`, peerConnection.connectionState);
                
                if (this.onConnectionStateChange) {
                    this.onConnectionStateChange(userId, peerConnection.connectionState);
                }

                // Handle disconnection
                if (peerConnection.connectionState === 'disconnected' || 
                    peerConnection.connectionState === 'failed' || 
                    peerConnection.connectionState === 'closed') {
                    this.removePeerConnection(userId);
                }
            };

            // Monitor ICE connection state
            peerConnection.oniceconnectionstatechange = () => {
                console.log(`ICE connection state for user ${userId}:`, peerConnection.iceConnectionState);
                
                if (this.onIceConnectionStateChange) {
                    this.onIceConnectionStateChange(userId, peerConnection.iceConnectionState);
                }
            };

            // Handle negotiation needed (for adding/removing tracks)
            peerConnection.onnegotiationneeded = async () => {
                try {
                    if (this.isInitiator) {
                        console.log('Negotiation needed, creating offer...');
                        await this.createAndSendOffer(userId);
                    }
                } catch (error) {
                    console.error('Negotiation failed:', error);
                }
            };

            return peerConnection;

        } catch (error) {
            console.error('Error creating peer connection:', error);
            throw error;
        }
    }

    /**
     * Create and send SDP offer to peer
     */
    async createAndSendOffer(userId) {
        try {
            const peerConnection = this.peerConnections.get(userId);
            if (!peerConnection) return;

            console.log(`Creating offer for user ${userId}`);

            const offer = await peerConnection.createOffer({
                offerToReceiveAudio: true,
                offerToReceiveVideo: this.callType === 'VIDEO' || this.callType === 'SCREEN_SHARE'
            });

            await peerConnection.setLocalDescription(offer);

            // Send offer through signaling server
            const offerData = {
                callId: this.currentCall.callId,
                offer: {
                    sdp: offer.sdp,
                    type: offer.type
                },
                recipientId: userId
            };

            this.stompClient.send('/app/call.offer', {}, JSON.stringify(offerData));
            console.log('Offer sent to user', userId);

        } catch (error) {
            console.error('Error creating offer:', error);
            throw error;
        }
    }

    /**
     * Handle incoming signaling messages
     */
    async handleSignalingMessage(data) {
        try {
            console.log('Received signaling message:', data.type);

            switch (data.type) {
                case 'OFFER':
                    await this.handleOffer(data);
                    break;

                case 'ANSWER':
                    await this.handleAnswer(data);
                    break;

                case 'ICE_CANDIDATE':
                    await this.handleIceCandidate(data);
                    break;

                default:
                    console.warn('Unknown signaling message type:', data.type);
            }

        } catch (error) {
            console.error('Error handling signaling message:', error);
        }
    }

    /**
     * Handle incoming SDP offer
     */
    async handleOffer(data) {
        try {
            const userId = data.senderId;
            console.log(`Received offer from user ${userId}`);

            // Create peer connection if it doesn't exist
            if (!this.peerConnections.has(userId)) {
                await this.createPeerConnection(userId);
            }

            const peerConnection = this.peerConnections.get(userId);

            // Set remote description
            await peerConnection.setRemoteDescription(
                new RTCSessionDescription(data.offer)
            );

            // Create and send answer
            const answer = await peerConnection.createAnswer();
            await peerConnection.setLocalDescription(answer);

            // Send answer through signaling server
            const answerData = {
                callId: data.callId,
                answer: {
                    sdp: answer.sdp,
                    type: answer.type
                },
                recipientId: userId
            };

            this.stompClient.send('/app/call.answer', {}, JSON.stringify(answerData));
            console.log('Answer sent to user', userId);

        } catch (error) {
            console.error('Error handling offer:', error);
        }
    }

    /**
     * Handle incoming SDP answer
     */
    async handleAnswer(data) {
        try {
            const userId = data.senderId;
            console.log(`Received answer from user ${userId}`);

            const peerConnection = this.peerConnections.get(userId);
            if (!peerConnection) {
                console.warn('No peer connection found for user', userId);
                return;
            }

            // Set remote description
            await peerConnection.setRemoteDescription(
                new RTCSessionDescription(data.answer)
            );

            console.log('Remote description set for user', userId);

        } catch (error) {
            console.error('Error handling answer:', error);
        }
    }

    /**
     * Send ICE candidate to peer
     */
    sendIceCandidate(candidate, recipientId) {
        const candidateData = {
            callId: this.currentCall.callId,
            candidate: {
                candidate: candidate.candidate,
                sdpMid: candidate.sdpMid,
                sdpMLineIndex: candidate.sdpMLineIndex
            },
            recipientId: recipientId
        };

        this.stompClient.send('/app/call.ice-candidate', {}, JSON.stringify(candidateData));
    }

    /**
     * Handle incoming ICE candidate
     */
    async handleIceCandidate(data) {
        try {
            const userId = data.senderId;
            const peerConnection = this.peerConnections.get(userId);

            if (!peerConnection) {
                console.warn('No peer connection found for ICE candidate from user', userId);
                return;
            }

            const candidate = new RTCIceCandidate(data.candidate);
            await peerConnection.addIceCandidate(candidate);

            console.log('ICE candidate added for user', userId);

        } catch (error) {
            console.error('Error adding ICE candidate:', error);
        }
    }

    /**
     * Handle call notifications
     */
    handleCallNotification(data) {
        console.log('Call notification:', data.type);

        switch (data.type) {
            case 'INCOMING_CALL':
            case 'INCOMING_GROUP_CALL':
                this.handleIncomingCall(data);
                break;

            case 'USER_JOINED':
                this.handleUserJoined(data);
                break;

            case 'USER_LEFT':
                this.handleUserLeft(data);
                break;

            case 'CALL_REJECTED':
                this.handleCallRejected(data);
                break;

            case 'MIC_TOGGLED':
            case 'CAMERA_TOGGLED':
            case 'SCREEN_SHARE_TOGGLED':
                this.handleMediaToggled(data);
                break;

            default:
                console.warn('Unknown call notification type:', data.type);
        }
    }

    /**
     * Handle incoming call
     */
    async handleIncomingCall(data) {
        console.log('Incoming call from', data.callerName);

        this.currentCall = data;
        this.callType = data.callType;
        this.callMode = data.callMode;
        this.isInitiator = false;

        // Notify UI layer to show incoming call modal
        if (window.showIncomingCallModal) {
            window.showIncomingCallModal(data);
        }
    }

    /**
     * Accept incoming call
     */
    async acceptCall() {
        try {
            console.log('Accepting call');

            // Get local media stream
            await this.getLocalStream(this.callType);

            // Join the call
            this.stompClient.send('/app/call.join', {}, JSON.stringify({
                callId: this.currentCall.callId
            }));

            // For direct calls, create peer connection with caller
            if (this.callMode === 'DIRECT') {
                await this.createPeerConnection(this.currentCall.callerId);
            }

            console.log('Call accepted');
            return true;

        } catch (error) {
            console.error('Error accepting call:', error);
            this.handleError('Failed to accept call: ' + error.message);
            return false;
        }
    }

    /**
     * Reject incoming call
     */
    rejectCall() {
        console.log('Rejecting call');

        this.stompClient.send('/app/call.reject', {}, JSON.stringify({
            callId: this.currentCall.callId,
            callerId: this.currentCall.callerId
        }));

        this.cleanup();
    }

    /**
     * Handle user joined call
     */
    async handleUserJoined(data) {
        console.log('User joined call:', data.userName);

        // Create peer connection with new user
        if (this.isInitiator) {
            await this.createPeerConnection(data.userId);
            // Create and send offer to new user
            await this.createAndSendOffer(data.userId);
        }
    }

    /**
     * Handle user left call
     */
    handleUserLeft(data) {
        console.log('User left call:', data.userName);
        this.removePeerConnection(data.userId);

        if (this.onRemoteStreamRemoved) {
            this.onRemoteStreamRemoved(data.userId);
        }
    }

    /**
     * Handle call rejected
     */
    handleCallRejected(data) {
        console.log('Call rejected by:', data.userName);
        
        if (this.onCallError) {
            this.onCallError(`${data.userName} declined the call`);
        }

        this.endCall();
    }

    /**
     * Handle media toggle notifications
     */
    handleMediaToggled(data) {
        console.log(`Media toggled: ${data.type} for user ${data.userId}`);
        
        // Notify UI to update participant status
        if (window.updateParticipantMedia) {
            window.updateParticipantMedia(data);
        }
    }

    /**
     * Toggle microphone (mute/unmute)
     */
    toggleMicrophone() {
        if (!this.localStream) return;

        const audioTrack = this.localStream.getAudioTracks()[0];
        if (audioTrack) {
            audioTrack.enabled = !audioTrack.enabled;
            
            // Notify server and other participants
            this.stompClient.send('/app/call.toggleMicrophone', {}, JSON.stringify({
                callId: this.currentCall.callId,
                enabled: audioTrack.enabled
            }));

            console.log('Microphone toggled:', audioTrack.enabled);
            return audioTrack.enabled;
        }

        return false;
    }

    /**
     * Toggle camera (on/off)
     */
    toggleCamera() {
        if (!this.localStream) return;

        const videoTrack = this.localStream.getVideoTracks()[0];
        if (videoTrack) {
            videoTrack.enabled = !videoTrack.enabled;
            
            // Notify server and other participants
            this.stompClient.send('/app/call.toggleCamera', {}, JSON.stringify({
                callId: this.currentCall.callId,
                enabled: videoTrack.enabled
            }));

            console.log('Camera toggled:', videoTrack.enabled);
            return videoTrack.enabled;
        }

        return false;
    }

    /**
     * Start screen sharing
     */
    async startScreenShare() {
        try {
            console.log('Starting screen share');

            // Get screen stream
            this.screenStream = await navigator.mediaDevices.getDisplayMedia(
                this.constraints.screenShare
            );

            // Replace video track in all peer connections
            const screenTrack = this.screenStream.getVideoTracks()[0];
            
            this.peerConnections.forEach((peerConnection, userId) => {
                const sender = peerConnection.getSenders().find(s => 
                    s.track && s.track.kind === 'video'
                );

                if (sender) {
                    sender.replaceTrack(screenTrack);
                }
            });

            // Notify server and other participants
            this.stompClient.send('/app/call.toggleScreenShare', {}, JSON.stringify({
                callId: this.currentCall.callId,
                enabled: true
            }));

            // Detect when user stops screen sharing via browser UI
            screenTrack.onended = () => {
                this.stopScreenShare();
            };

            console.log('Screen sharing started');
            return true;

        } catch (error) {
            console.error('Error starting screen share:', error);
            this.handleError('Failed to start screen sharing: ' + error.message);
            return false;
        }
    }

    /**
     * Stop screen sharing
     */
    async stopScreenShare() {
        try {
            console.log('Stopping screen share');

            if (!this.screenStream) return;

            // Stop screen stream tracks
            this.screenStream.getTracks().forEach(track => track.stop());

            // Replace with camera track if available
            if (this.localStream) {
                const cameraTrack = this.localStream.getVideoTracks()[0];
                
                if (cameraTrack) {
                    this.peerConnections.forEach((peerConnection, userId) => {
                        const sender = peerConnection.getSenders().find(s => 
                            s.track && s.track.kind === 'video'
                        );

                        if (sender) {
                            sender.replaceTrack(cameraTrack);
                        }
                    });
                }
            }

            this.screenStream = null;

            // Notify server and other participants
            this.stompClient.send('/app/call.toggleScreenShare', {}, JSON.stringify({
                callId: this.currentCall.callId,
                enabled: false
            }));

            console.log('Screen sharing stopped');

        } catch (error) {
            console.error('Error stopping screen share:', error);
        }
    }

    /**
     * End call
     */
    endCall() {
        console.log('Ending call');

        if (this.currentCall) {
            this.stompClient.send('/app/call.leave', {}, JSON.stringify({
                callId: this.currentCall.callId
            }));
        }

        this.cleanup();

        if (this.onCallEnded) {
            this.onCallEnded();
        }
    }

    /**
     * Remove peer connection
     */
    removePeerConnection(userId) {
        const peerConnection = this.peerConnections.get(userId);
        
        if (peerConnection) {
            peerConnection.close();
            this.peerConnections.delete(userId);
            console.log('Peer connection removed for user', userId);
        }
    }

    /**
     * Clean up resources
     */
    cleanup() {
        console.log('Cleaning up WebRTC resources');

        // Close all peer connections
        this.peerConnections.forEach((peerConnection, userId) => {
            peerConnection.close();
        });
        this.peerConnections.clear();

        // Stop local streams
        if (this.localStream) {
            this.localStream.getTracks().forEach(track => track.stop());
            this.localStream = null;
        }

        if (this.screenStream) {
            this.screenStream.getTracks().forEach(track => track.stop());
            this.screenStream = null;
        }

        // Reset state
        this.currentCall = null;
        this.isInitiator = false;
        this.callType = null;
        this.callMode = null;

        console.log('Cleanup complete');
    }

    /**
     * Handle errors
     */
    handleError(message) {
        console.error('WebRTC Error:', message);
        
        if (this.onCallError) {
            this.onCallError(message);
        }
    }

    /**
     * Get local stream (for displaying in UI)
     */
    getLocalStream() {
        return this.localStream;
    }

    /**
     * Get screen stream (for displaying in UI)
     */
    getScreenStream() {
        return this.screenStream;
    }

    /**
     * Check if microphone is enabled
     */
    isMicrophoneEnabled() {
        if (!this.localStream) return false;
        const audioTrack = this.localStream.getAudioTracks()[0];
        return audioTrack ? audioTrack.enabled : false;
    }

    /**
     * Check if camera is enabled
     */
    isCameraEnabled() {
        if (!this.localStream) return false;
        const videoTrack = this.localStream.getVideoTracks()[0];
        return videoTrack ? videoTrack.enabled : false;
    }

    /**
     * Check if screen sharing is active
     */
    isScreenSharing() {
        return this.screenStream !== null;
    }
}

// Export for use in other files
window.WebRTCManager = WebRTCManager;