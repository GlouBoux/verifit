@echo off
REM Runs the same build Android Studio's Run button does (assembleDebug), with
REM full --info detail, and captures BOTH stdout and stderr (2>&1) - the final
REM "FAILURE: ... What went wrong" summary Gradle prints usually goes to
REM stderr, which a plain "> build_log.txt" redirect misses.
REM
REM Double-click this file (or run it from a terminal) from anywhere; it always
REM writes build_log.txt next to it, at the repo root, regardless of where it
REM was launched from.
REM
REM Deliberately targets assembleDebug rather than the plain "build" task:
REM "build" also compiles the androidTest APK, which currently fails on a
REM pre-existing, unrelated bug (old espresso-core manifest vs. targetSdk 31 -
REM see docs/fitnotes-fork-plan.md) that has nothing to do with the app itself
REM and would drown the real signal in a false failure every time.

cd /d "%~dp0\.."
call gradlew.bat assembleDebug --info > build_log.txt 2>&1
echo Done. See build_log.txt
pause
