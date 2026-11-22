# Keep application components declared in AndroidManifest.xml
-keep class com.example.MainActivity { *; }
-keep class com.example.FilePickerBridgeActivity { *; }
-keep class com.example.data.provider.FolderPushDocumentsProvider { *; }
-keep class com.example.data.sync.FolderSyncService { *; }

# Keep Room entities & DAOs
-keep class com.example.data.local.** { *; }
-keepclassmembers class * extends androidx.room.RoomDatabase {
    <init>();
}

# Keep OkHttp / Coroutines safely
-dontwarn okhttp3.**
-dontwarn okio.**
-keepattributes Signature,InnerClasses,EnclosingMethod,SourceFile,LineNumberTable
