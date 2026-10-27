package com.phantom.mesh.calling.webrtc

import android.content.Context
import android.media.AudioDeviceInfo
import android.media.AudioManager
import android.os.Build
import android.util.Log
import org.webrtc.AudioSource
import org.webrtc.AudioTrack
import org.webrtc.Camera2Enumerator
import org.webrtc.CameraVideoCapturer
import org.webrtc.DefaultVideoDecoderFactory
import org.webrtc.DefaultVideoEncoderFactory
import org.webrtc.EglBase
import org.webrtc.IceCandidate
import org.webrtc.MediaConstraints
import org.webrtc.MediaStreamTrack
import org.webrtc.PeerConnection
import org.webrtc.PeerConnectionFactory
import org.webrtc.RendererCommon
import org.webrtc.RtpTransceiver
import org.webrtc.SdpObserver
import org.webrtc.SessionDescription
import org.webrtc.SurfaceTextureHelper
import org.webrtc.SurfaceViewRenderer
import org.webrtc.VideoSource
import org.webrtc.VideoTrack
import org.webrtc.audio.JavaAudioDeviceModule
import java.util.concurrent.CopyOnWriteArrayList
import java.util.concurrent.atomic.AtomicBoolean

/**
 * Enterprise-grade WebRTC Peer-to-Peer Media Engine for PhantomMesh.
 *
 * Implements Google's official native WebRTC pipeline:
 * - 48kHz Opus full-band audio with hardware Acoustic Echo Cancellation (AEC) and Noise Suppression.
 * - Hardware accelerated VP8/H.264 video capturing via Camera2 with GPU zero-copy rendering.
 * - Google's public STUN infrastructure for high-speed direct UDP NAT traversal.
 * - Dynamic queued ICE candidate processing preventing premature handshake drops.
 */
class WebRtcClient(
    private val context: Context,
    val eglBase: EglBase = EglBase.create()
) {
    companion object {
        private const val TAG = "WebRtcClient"
        private const val VIDEO_WIDTH = 640
        private const val VIDEO_HEIGHT = 480
        private const val VIDEO_FPS = 30

        private val ICE_SERVERS = listOf(
            PeerConnection.IceServer.builder("stun:stun.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun1.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun2.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun3.l.google.com:19302").createIceServer(),
            PeerConnection.IceServer.builder("stun:stun4.l.google.com:19302").createIceServer()
        )
    }

    interface Listener {
        fun onIceCandidateGenerated(candidate: RtcIceCandidate)
        fun onLocalSdpCreated(desc: RtcSessionDescription)
        fun onRemoteVideoTrackReceived(track: VideoTrack)
        fun onConnectionStateChanged(state: PeerConnection.PeerConnectionState)
    }

    var listener: Listener? = null

    private val audioManager = context.getSystemService(Context.AUDIO_SERVICE) as? AudioManager
    private var peerConnectionFactory: PeerConnectionFactory? = null
    private var peerConnection: PeerConnection? = null

    private var audioSource: AudioSource? = null
    var localAudioTrack: AudioTrack? = null
        private set

    private var videoSource: VideoSource? = null
    var localVideoTrack: VideoTrack? = null
        private set

    var remoteVideoTrack: VideoTrack? = null
        private set

    private var surfaceTextureHelper: SurfaceTextureHelper? = null
    private var videoCapturer: CameraVideoCapturer? = null

    private val isClosed = AtomicBoolean(false)
    private val isRemoteDescriptionSet = AtomicBoolean(false)
    private val pendingCandidates = CopyOnWriteArrayList<IceCandidate>()

    private val localRenderers = CopyOnWriteArrayList<SurfaceViewRenderer>()
    private val remoteRenderers = CopyOnWriteArrayList<SurfaceViewRenderer>()

    private var isVideoCall = false
    private var isFrontCamera = true

    fun attachLocalRenderer(renderer: SurfaceViewRenderer) {
        try {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer.setEnableHardwareScaler(true)
            renderer.setMirror(isFrontCamera)
            localRenderers.add(renderer)
            localVideoTrack?.addSink(renderer)
        } catch (e: Exception) {
            Log.w(TAG, "attachLocalRenderer error: ${e.message}")
        }
    }

    fun detachLocalRenderer(renderer: SurfaceViewRenderer) {
        try {
            localRenderers.remove(renderer)
            localVideoTrack?.removeSink(renderer)
            renderer.release()
        } catch (_: Exception) {}
    }

    fun attachRemoteRenderer(renderer: SurfaceViewRenderer) {
        try {
            renderer.init(eglBase.eglBaseContext, null)
            renderer.setScalingType(RendererCommon.ScalingType.SCALE_ASPECT_FILL)
            renderer.setEnableHardwareScaler(true)
            remoteRenderers.add(renderer)
            remoteVideoTrack?.addSink(renderer)
        } catch (e: Exception) {
            Log.w(TAG, "attachRemoteRenderer error: ${e.message}")
        }
    }

    fun detachRemoteRenderer(renderer: SurfaceViewRenderer) {
        try {
            remoteRenderers.remove(renderer)
            remoteVideoTrack?.removeSink(renderer)
            renderer.release()
        } catch (_: Exception) {}
    }

    init {
        initPeerConnectionFactory()
    }

    private fun initPeerConnectionFactory() {
        val options = PeerConnectionFactory.InitializationOptions.builder(context)
            .setEnableInternalTracer(false)
            .createInitializationOptions()
        PeerConnectionFactory.initialize(options)

        val adm = JavaAudioDeviceModule.builder(context)
            .setUseHardwareAcousticEchoCanceler(true)
            .setUseHardwareNoiseSuppressor(true)
            .createAudioDeviceModule()

        val encoderFactory = DefaultVideoEncoderFactory(eglBase.eglBaseContext, true, true)
        val decoderFactory = DefaultVideoDecoderFactory(eglBase.eglBaseContext)

        peerConnectionFactory = PeerConnectionFactory.builder()
            .setAudioDeviceModule(adm)
            .setVideoEncoderFactory(encoderFactory)
            .setVideoDecoderFactory(decoderFactory)
            .setOptions(PeerConnectionFactory.Options().apply {
                disableNetworkMonitor = false
            })
            .createPeerConnectionFactory()
    }

    /**
     * Initializes local tracks and creates the PeerConnection.
     */
    fun start(isCaller: Boolean, isVideo: Boolean) {
        if (isClosed.get()) return
        isVideoCall = isVideo

        configureAudioMode()

        val factory = peerConnectionFactory ?: return

        // 1. Create Local Audio Track (Opus 48kHz, Hardware AEC)
        val audioConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("googEchoCancellation", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googAutoGainControl", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googHighpassFilter", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("googNoiseSuppression", "true"))
        }
        audioSource = factory.createAudioSource(audioConstraints)
        localAudioTrack = factory.createAudioTrack("ARDAMSa0", audioSource).apply {
            setEnabled(true)
        }

        // 2. Create Local Video Track if video call
        if (isVideo) {
            setupCameraCapture()
        }

        // 3. Create PeerConnection
        val rtcConfig = PeerConnection.RTCConfiguration(ICE_SERVERS).apply {
            sdpSemantics = PeerConnection.SdpSemantics.UNIFIED_PLAN
            continualGatheringPolicy = PeerConnection.ContinualGatheringPolicy.GATHER_CONTINUALLY
        }

        peerConnection = factory.createPeerConnection(rtcConfig, object : PeerConnection.Observer {
            override fun onIceCandidate(candidate: IceCandidate?) {
                if (candidate != null) {
                    val rtcCand = RtcIceCandidate(
                        sdp = candidate.sdp,
                        sdpMid = candidate.sdpMid,
                        sdpMLineIndex = candidate.sdpMLineIndex
                    )
                    listener?.onIceCandidateGenerated(rtcCand)
                }
            }

            override fun onTrack(transceiver: RtpTransceiver?) {
                val track = transceiver?.receiver?.track()
                if (track is VideoTrack) {
                    remoteVideoTrack = track
                    for (renderer in remoteRenderers) {
                        try {
                            track.addSink(renderer)
                        } catch (_: Exception) {}
                    }
                    listener?.onRemoteVideoTrackReceived(track)
                }
            }

            override fun onConnectionChange(newState: PeerConnection.PeerConnectionState?) {
                Log.i(TAG, "WebRTC PeerConnectionState changed: $newState")
                if (newState != null) {
                    listener?.onConnectionStateChanged(newState)
                }
            }

            override fun onIceCandidatesRemoved(candidates: Array<out IceCandidate>?) {}
            override fun onSignalingChange(state: PeerConnection.SignalingState?) {}
            override fun onIceConnectionChange(state: PeerConnection.IceConnectionState?) {}
            override fun onIceConnectionReceivingChange(receiving: Boolean) {}
            override fun onIceGatheringChange(state: PeerConnection.IceGatheringState?) {}
            override fun onAddStream(stream: org.webrtc.MediaStream?) {}
            override fun onRemoveStream(stream: org.webrtc.MediaStream?) {}
            override fun onDataChannel(channel: org.webrtc.DataChannel?) {}
            override fun onRenegotiationNeeded() {}
        })

        // 4. Attach tracks to PeerConnection
        localAudioTrack?.let { peerConnection?.addTrack(it) }
        localVideoTrack?.let { peerConnection?.addTrack(it) }

        // 5. If caller, generate initial SDP Offer
        if (isCaller) {
            createOffer()
        }
    }

    private fun setupCameraCapture() {
        try {
            val enumerator = Camera2Enumerator(context)
            val deviceNames = enumerator.deviceNames

            val frontCameraName = deviceNames.firstOrNull { enumerator.isFrontFacing(it) }
            val backCameraName = deviceNames.firstOrNull { enumerator.isBackFacing(it) }
            val chosenCamera = frontCameraName ?: backCameraName ?: deviceNames.firstOrNull() ?: return

            isFrontCamera = enumerator.isFrontFacing(chosenCamera)

            videoCapturer = enumerator.createCapturer(chosenCamera, null)
            surfaceTextureHelper = SurfaceTextureHelper.create("WebRtcCaptureThread", eglBase.eglBaseContext)

            val factory = peerConnectionFactory ?: return
            videoSource = factory.createVideoSource(videoCapturer!!.isScreencast)

            videoCapturer?.initialize(surfaceTextureHelper, context, videoSource?.capturerObserver)
            videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)

            localVideoTrack = factory.createVideoTrack("ARDAMSv0", videoSource).apply {
                setEnabled(true)
                for (renderer in localRenderers) {
                    try {
                        addSink(renderer)
                    } catch (_: Exception) {}
                }
            }
        } catch (e: Exception) {
            Log.e(TAG, "Failed setting up camera capture: ${e.message}", e)
        }
    }

    private fun configureAudioMode() {
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            setSpeakerOn(true)
        } catch (e: Exception) {
            Log.w(TAG, "Failed configuring audio mode: ${e.message}")
        }
    }

    fun setSpeakerOn(speakerOn: Boolean) {
        try {
            audioManager?.mode = AudioManager.MODE_IN_COMMUNICATION
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                if (speakerOn) {
                    val speaker = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_SPEAKER
                    }
                    if (speaker != null) audioManager?.setCommunicationDevice(speaker)
                } else {
                    val earpiece = audioManager?.availableCommunicationDevices?.firstOrNull {
                        it.type == AudioDeviceInfo.TYPE_BUILTIN_EARPIECE
                    }
                    if (earpiece != null) audioManager?.setCommunicationDevice(earpiece) else audioManager?.clearCommunicationDevice()
                }
            }
            @Suppress("DEPRECATION")
            audioManager?.isSpeakerphoneOn = speakerOn

            val maxVol = audioManager?.getStreamMaxVolume(AudioManager.STREAM_VOICE_CALL) ?: 1
            val curVol = audioManager?.getStreamVolume(AudioManager.STREAM_VOICE_CALL) ?: 0
            if (curVol < (maxVol * 0.75).toInt()) {
                audioManager?.setStreamVolume(AudioManager.STREAM_VOICE_CALL, (maxVol * 0.9).toInt(), 0)
            }
        } catch (e: Exception) {
            Log.w(TAG, "setSpeakerOn error: ${e.message}")
        }
    }

    fun setMuted(isMuted: Boolean) {
        localAudioTrack?.setEnabled(!isMuted)
    }

    fun setVideoEnabled(enabled: Boolean) {
        localVideoTrack?.setEnabled(enabled)
        try {
            if (enabled) {
                videoCapturer?.startCapture(VIDEO_WIDTH, VIDEO_HEIGHT, VIDEO_FPS)
            } else {
                videoCapturer?.stopCapture()
            }
        } catch (_: Exception) {}
    }

    fun switchCamera() {
        videoCapturer?.switchCamera(object : CameraVideoCapturer.CameraSwitchHandler {
            override fun onCameraSwitchDone(isFront: Boolean) {
                isFrontCamera = isFront
                for (renderer in localRenderers) {
                    try {
                        renderer.setMirror(isFront)
                    } catch (_: Exception) {}
                }
            }
            override fun onCameraSwitchError(error: String?) {
                Log.w(TAG, "Camera switch error: $error")
            }
        })
    }

    private fun createOffer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }

        peerConnection?.createOffer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        val rtcDesc = RtcSessionDescription(desc.type.canonicalForm().uppercase(), desc.description)
                        listener?.onLocalSdpCreated(rtcDesc)
                    }
                    override fun onCreateFailure(err: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "setLocalDescription failed: $err")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createOffer failed: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpConstraints)
    }

    fun onRemoteSessionDescriptionReceived(desc: RtcSessionDescription) {
        val type = SessionDescription.Type.valueOf(desc.type.uppercase())
        val sessionDesc = SessionDescription(type, desc.sdp)

        peerConnection?.setRemoteDescription(object : SdpObserver {
            override fun onSetSuccess() {
                isRemoteDescriptionSet.set(true)
                // Drain any pending ICE candidates that arrived before remote SDP description was ready
                for (cand in pendingCandidates) {
                    peerConnection?.addIceCandidate(cand)
                }
                pendingCandidates.clear()

                // If remote SDP was an OFFER, create and dispatch ANSWER
                if (type == SessionDescription.Type.OFFER) {
                    createAnswer()
                }
            }

            override fun onCreateSuccess(p0: SessionDescription?) {}
            override fun onCreateFailure(err: String?) {}
            override fun onSetFailure(err: String?) {
                Log.e(TAG, "setRemoteDescription failed: $err")
            }
        }, sessionDesc)
    }

    private fun createAnswer() {
        val sdpConstraints = MediaConstraints().apply {
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveAudio", "true"))
            mandatory.add(MediaConstraints.KeyValuePair("OfferToReceiveVideo", if (isVideoCall) "true" else "false"))
        }

        peerConnection?.createAnswer(object : SdpObserver {
            override fun onCreateSuccess(desc: SessionDescription?) {
                if (desc == null) return
                peerConnection?.setLocalDescription(object : SdpObserver {
                    override fun onCreateSuccess(p0: SessionDescription?) {}
                    override fun onSetSuccess() {
                        val rtcDesc = RtcSessionDescription(desc.type.canonicalForm().uppercase(), desc.description)
                        listener?.onLocalSdpCreated(rtcDesc)
                    }
                    override fun onCreateFailure(err: String?) {}
                    override fun onSetFailure(err: String?) {
                        Log.e(TAG, "setLocalDescription for answer failed: $err")
                    }
                }, desc)
            }

            override fun onSetSuccess() {}
            override fun onCreateFailure(err: String?) {
                Log.e(TAG, "createAnswer failed: $err")
            }
            override fun onSetFailure(err: String?) {}
        }, sdpConstraints)
    }

    fun addIceCandidate(candidate: RtcIceCandidate) {
        val iceCandidate = IceCandidate(candidate.sdpMid, candidate.sdpMLineIndex, candidate.sdp)
        if (isRemoteDescriptionSet.get()) {
            peerConnection?.addIceCandidate(iceCandidate)
        } else {
            pendingCandidates.add(iceCandidate)
        }
    }

    fun close() {
        if (isClosed.getAndSet(true)) return

        for (r in localRenderers) {
            try { r.release() } catch (_: Exception) {}
        }
        localRenderers.clear()

        for (r in remoteRenderers) {
            try { r.release() } catch (_: Exception) {}
        }
        remoteRenderers.clear()

        try {
            videoCapturer?.stopCapture()
            videoCapturer?.dispose()
        } catch (_: Exception) {}
        videoCapturer = null

        try {
            surfaceTextureHelper?.dispose()
        } catch (_: Exception) {}
        surfaceTextureHelper = null

        try {
            localVideoTrack?.dispose()
            videoSource?.dispose()
        } catch (_: Exception) {}

        try {
            localAudioTrack?.dispose()
            audioSource?.dispose()
        } catch (_: Exception) {}

        try {
            peerConnection?.close()
            peerConnection?.dispose()
        } catch (_: Exception) {}
        peerConnection = null

        try {
            audioManager?.mode = AudioManager.MODE_NORMAL
            audioManager?.isSpeakerphoneOn = false
        } catch (_: Exception) {}

        pendingCandidates.clear()
        Log.i(TAG, "WebRtcClient closed and media resources released")
    }
}
