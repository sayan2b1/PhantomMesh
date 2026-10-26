# Keep Room schema and database
-keep class androidx.room.** { *; }
-keep class * extends androidx.room.RoomDatabase
-dontwarn androidx.room.paging.**

# Keep Nearby Connections models and callbacks
-keep class com.google.android.gms.nearby.** { *; }

# Keep MeshPacket and JSON models
-keepclassmembers class com.phantom.mesh.mesh.MeshPacket { <fields>; }
-keepclassmembers class com.phantom.mesh.data.repository.DropZoneMetadataPayload { <fields>; }
-keepclassmembers class com.phantom.mesh.data.repository.AuthCloudRequest { <fields>; }
-keepclassmembers class com.phantom.mesh.data.repository.AuthCloudResponse { <fields>; }

# Gson serialization
-keepattributes Signature
-keepattributes *Annotation*
-dontwarn sun.misc.**
-keep class com.google.gson.** { *; }
