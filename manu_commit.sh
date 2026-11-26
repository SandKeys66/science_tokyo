#!/bin/bash
cd /Users/risin/Documents/science_tokyo # リポジトリの実際のパスに書き換えてください

git add .
git commit -m "手動コミット: $(date '+%Y-%m-%d %H:%M:%S')"
git push origin main # 必要に応じてプッシュも自動化