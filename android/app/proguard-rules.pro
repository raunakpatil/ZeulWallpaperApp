# Project-specific R8 rules. Library rules (Hilt, Compose, kotlinx.serialization) ship with the libraries.

# Annotation referenced by play-core ktx, not shipped at runtime (R8 missing_rules.txt).
-dontwarn com.google.android.gms.common.annotation.NoNullnessRewrite
