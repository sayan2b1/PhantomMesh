package com.phantom.mesh.calling.webrtc

/**
 * Serializable data models for WebRTC signaling over Firebase Firestore and Bluetooth Mesh.
 */
data class RtcSessionDescription(
    val type: String, // "OFFER" or "ANSWER"
    val sdp: String
) {
    fun toMap(): Map<String, Any> = mapOf(
        "type" to type,
        "sdp" to sdp
    )

    companion object {
        fun fromMap(map: Map<*, *>?): RtcSessionDescription? {
            if (map == null) return null
            val type = map["type"] as? String ?: return null
            val sdp = map["sdp"] as? String ?: return null
            return RtcSessionDescription(type, sdp)
        }
    }
}

data class RtcIceCandidate(
    val sdp: String,
    val sdpMid: String? = null,
    val sdpMLineIndex: Int = 0
) {
    fun toMap(): Map<String, Any> = mapOf(
        "sdp" to sdp,
        "sdpMid" to (sdpMid ?: ""),
        "sdpMLineIndex" to sdpMLineIndex
    )

    companion object {
        fun fromMap(map: Map<*, *>?): RtcIceCandidate? {
            if (map == null) return null
            val sdp = map["sdp"] as? String ?: return null
            val sdpMid = map["sdpMid"] as? String
            val sdpMLineIndex = (map["sdpMLineIndex"] as? Number)?.toInt() ?: 0
            return RtcIceCandidate(sdp, sdpMid, sdpMLineIndex)
        }
    }
}
