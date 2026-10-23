@echo off
rem Opens Shasha (the PC tool for Android TVs). Windows 10/11, nothing to install.
start "" /min powershell -NoProfile -ExecutionPolicy Bypass -WindowStyle Hidden -File "%~dp0Shasha.ps1"
