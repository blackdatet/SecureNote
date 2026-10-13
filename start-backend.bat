@echo off
setlocal
title SecureNote - Backend Launcher
cd /d "%~dp0"

if not exist "backend\.venv\Scripts\python.exe" (
    color 0C
    echo.
    echo [ERROR] Python environment not found.
    echo Create backend\.venv and install backend\requirements.txt.
    echo.
    pause
    exit /b 1
)

start "SecureNote - Local Backend" /D "%~dp0" "%~dp0backend\.venv\Scripts\python.exe" -u -c "import runpy, sys; print('\n  ============================================\n   SecureNote | Local Backend\n   FastAPI + SQLite\n  ============================================\n\n   API:   http://127.0.0.1:8000\n   Docs:  http://127.0.0.1:8000/docs\n\n   Stop:  Ctrl+C (the window will close)\n   Keep this window open while using the app.\n\n  ============================================\n'); sys.argv = ['uvicorn', 'main:app', '--app-dir', 'backend', '--host', '127.0.0.1', '--port', '8000', '--reload', '--reload-dir', 'backend']; runpy.run_module('uvicorn', run_name='__main__')"
exit /b