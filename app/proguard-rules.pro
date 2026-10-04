# miuix, Jetpack Compose and the AndroidX libraries ship their own consumer rules.
#
# R8 is what strips the unused Material icons from material-icons-extended; without it the
# release APK would carry several thousand unused ImageVector definitions.

# --- Crash reports have to be readable ---
#
# Without these, every frame in a crash report reads `r8-map-id-<hash>:29`: no file name, and the
# number is a synthetic offset rather than a line. Keeping the line-number table and renaming the
# source file attribute to a plain `SourceFile` gives `AboutScreen.kt:412` style frames, which is
# the difference between a report someone can act on and one they have to guess at.
-keepattributes SourceFile,LineNumberTable
-renamesourcefileattribute SourceFile
