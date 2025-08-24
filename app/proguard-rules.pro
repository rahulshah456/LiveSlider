#noinspection ShrinkerUnresolvedReference
# Add project specific ProGuard rules here.
# By default, the flags in this file are appended to flags specified
# in C:\Users\dklap\AppData\Local\Android\Sdk/tools/proguard/proguard-android.txt
# You can edit the include path and order by changing the proguardFiles
# directive in build.gradle.
#
# For more details, see
#   http://developer.android.com/guide/developing/tools/proguard.html

# Add any project specific keep options here:

# If your project uses WebView with JS, uncomment the following
# and specify the fully qualified class name to the JavaScript interface
# class:
#-keepclassmembers class fqcn.of.javascript.interface.for.webview {
#   public *;
#}

######----------   Optimization Levels  ---------------######
-optimizationpasses 5
######----------   Optimization Levels  ---------------######


######------------ Keeping Modal & Inner Classes from getting obfuscation -------------#####
-keep public class com.droid2developers.liveslider.database.dao.** { *; }
-keep public class com.droid2developers.liveslider.database.models.** { *; }
-keep public class com.droid2developers.liveslider.database.repository.** {*;}
######------------ Keeping Modal Classes from getting obfuscation -------------#####


-keepattributes *Annotation*
-keepclassmembers class ** {
    @org.greenrobot.eventbus.Subscribe <methods>;
}
-keep enum org.greenrobot.eventbus.ThreadMode { *; }



######----------   Glide  ---------------######
-keep public class * implements com.bumptech.glide.module.GlideModule
-keep class * extends com.bumptech.glide.module.AppGlideModule {
 <init>(...);
}
-keep public enum com.bumptech.glide.load.ImageHeaderParser$** {
  **[] $VALUES;
  public *;
}
-keep class com.bumptech.glide.load.data.ParcelFileDescriptorRewinder$InternalRewinder {
  *** rewind();
}
######----------   Glide  ---------------######

# Keep the wallpaper provider that is used by the system wallpaper service
-keep class com.droid2developers.liveslider.provider.SepWallpaperProvider { *; }
