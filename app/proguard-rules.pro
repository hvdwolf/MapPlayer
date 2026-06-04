###############################################
## 0. Keep line numbers (crash readability)
###############################################
-keepattributes SourceFile,LineNumberTable

###############################################
## 1. Core Android components (Activities, Services, Receivers)
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
## 3. Your model classes
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
-dontwarn kotlinx.coroutines.**
-dontwarn org.jetbrains.annotations.**

###############################################
## 7. RecyclerView adapters / view holders
###############################################
-keepclassmembers class * extends androidx.recyclerview.widget.RecyclerView$ViewHolder {
    <init>(...);
}
-keep class * extends androidx.recyclerview.widget.RecyclerView$Adapter { *; }

###############################################
## 8. Keep @Keep-annotated members
###############################################
-keepclassmembers class * {
    @androidx.annotation.Keep *;
}
