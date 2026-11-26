#!/bin/bash

# エラー吐いたら即終了
set -e

# スリープ防止
/usr/bin/caffeinate -dimsu -w $$ &

cd /Users/risin/Documents/science_tokyo # ローカルリポジトリのパス

git add .
git diff --cached --quiet || git commit -m "自動コミット: $(date '+%Y-%m-%d %H:%M:%S')"
git push origin main

