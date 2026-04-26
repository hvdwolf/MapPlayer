###############################################
## 0. Keep line numbers (crash readability)
###############################################
-keepattributes SourceFile,LineNumberTable

###############################################
## 1. Core Android components
###############################################
-keep class xyz.hvdw.mapplayer.ui.** { *; }
-keep class xyz.hvdw.mapplayer.service.** { *; }
-keep class xyz.hvdw.mapplayer.receiver.** { *; }

###############################################
## 2. Gson model classes (reflection)
###############################################
-keep class xyz.hvdw.mapplayer.data.LibraryScanner$LibraryDb { *; }
-keep class xyz.hvdw.mapplayer.data.LibraryScanner$LibraryFolder { *; }
-keep class xyz.hvdw.mapplayer.data.LibraryScanner$LibraryTrack { *; }

###############################################
## 3. Your models (FolderItem, Track, etc.)
###############################################
-keep class xyz.hvdw.mapplayer.model.** { *; }

###############################################
## 4. ExoPlayer
###############################################
-keep class com.google.android.exoplayer2.** { *; }
-dontwarn com.google.android.exoplayer2.**

###############################################
## 5. MediaSession / media support
###############################################
-keep class androidx.media.** { *; }
-dontwarn androidx.media.**

###############################################
## 6. Kotlin / coroutines
###############################################
-keep class kotlinx.coroutines.** { *; }
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

###############################################
## 7. AndroidX lifecycle / activity / fragment
###############################################
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**
-keep class androidx.activity.** { *; }
-dontwarn androidx.activity.**
-keep class androidx.fragment.app.** { *; }
-dontwarn androidx.fragment.app.**

###############################################
## 8. RecyclerView adapters / view holders
###############################################
# Keep all ViewHolders
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    <init>(...);
}

# Keep all Adapters (they use generics + reflection internally)
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter { *; }

###############################################
## 9. RecyclerView LayoutManagers (prevent cast crashes)
###############################################
-keep class androidx.recyclerview.widget.LinearLayoutManager { *; }
-keep class androidx.recyclerview.widget.GridLayoutManager { *; }
-keep class androidx.recyclerview.widget.RecyclerView$LayoutManager { *; }
-keep class androidx.recyclerview.widget.RecyclerView$LayoutParams { *; }

###############################################
## 10. GestureDetector
###############################################
-keep class android.view.GestureDetector { *; }
-keep class android.view.GestureDetector$SimpleOnGestureListener { *; }

###############################################
## 11. Material Components
###############################################
-keep class com.google.android.material.** { *; }
-dontwarn com.google.android.material.**

###############################################
## 12. MediaMetadataRetriever
###############################################
-keep class android.media.MediaMetadataRetriever { *; }

###############################################
## 13. Keep @Keep-annotated members
###############################################
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
