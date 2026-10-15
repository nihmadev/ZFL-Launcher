# Project-specific R8 rules. AndroidX, Compose and Kotlin ship their own consumer rules;
# the app itself uses no reflection or name-based loading, so nothing extra is kept.

# Keep AGP's aggressive class repackaging and access widening explicit.
-repackageclasses
-allowaccessmodification

# Release only: remove Kotlin Intrinsics null checks, including parameter checks.
# Invalid nulls from Java/platform code may fail later instead of at the boundary.
-processkotlinnullchecks remove
