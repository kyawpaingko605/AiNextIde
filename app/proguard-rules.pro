# Add project specific ProGuard rules here.
-dontobfuscate
-dontoptimize

# Core App Rules
-keep class com.nextide.** { *; }

# Sora Editor & TextMate Rules (အကုန်လုံး မဖျက်ရန် အပိတ်ကာထားပါသည်)
-keep class io.github.Rosemoe.sora.** { *; }
-keep class com.ashera.textmate.** { *; }
-keep class org.eclipse.tm4e.** { *; }

# Java Records & Attributes Support
-keepattributes Signature, InnerClasses, EnclosingMethod, Annotation, MethodParameters
