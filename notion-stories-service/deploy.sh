#!/usr/bin/env sh
set -eu

cd "$(dirname "$0")"

git pull --ff-only
docker compose build
docker compose up -d
docker compose ps
