@echo off
setlocal
title SecureNote Backend
cd /d "%~dp0"

if not exist "backend\.venv\Scripts\python.exe" (
    echo Python environment not found.
    echo Create backend\.venv and install backend\requirements.txt.
    pause
    exit /b 1
)

echo Starting SecureNote backend...
echo API documentation: http://127.0.0.1:8000/docs
echo Press Ctrl+C to stop.
echo.

"backend\.venv\Scripts\python.exe" -m uvicorn main:app --app-dir backend --host 127.0.0.1 --port 8000 --reload --reload-dir backend

echo.
echo Backend stopped.
pause
