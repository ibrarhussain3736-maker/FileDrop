#!/usr/bin/env bash
# Usage (inside the FileDrop folder): bash push.sh https://github.com/USER/REPO.git
set -e
if [ -z "$1" ]; then echo "Usage: bash push.sh <github-repo-url>"; exit 1; fi
command -v git >/dev/null || pkg install -y git
git config user.name  >/dev/null || git config user.name  "FileDrop"
git config user.email >/dev/null || git config user.email "filedrop@example.com"
[ -d .git ] || git init -q
git add -A
git commit -qm "FileDrop app" || true
git branch -M main
git remote remove origin 2>/dev/null || true
git remote add origin "$1"
git push -u origin main
echo "Pushed. Open the Actions tab on GitHub; the APK appears under Artifacts when the build finishes."
