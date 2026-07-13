# Morphe's injected shared bridge is only called after patching; preserve it
# through R8 alongside the project-specific extension implementation.
-keep class app.morphe.** { *; }
-keep class com.tivimate.traktpatch.extension.** { *; }
