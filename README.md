# Pocket Backup

A simple backup tool for Pocket — because they’re shutting down and the official data export process is broken (at least for me).

This project lets you back up your Pocket saves to a .jsonl file using the official API.

**Hurry up — Pocket shuts down on 2025-07-08!**

![Logs screenshot](https://raw.githubusercontent.com/jpcenteno/pocket-backup.clj/refs/heads/main/docs/screenshot.png)


## Usage

1. Get a _consumer key_ by [creating a Pocket application](https://getpocket.com/developer/apps/new).
2. Use OAuth to get an _access token_.
3. Save your keys in a file named `secrets.env` (See `secrets.env.example`).
4. Open `./src/pocket_backup/core.clj` on your favorite Clojure IDE.
5. Eval `(def secrets ...)` to parse `secrets.env`.
6. Eval `(backup "backup.jsonl" secrets 0)`. Check the logs — you may need to fix runtime errors manually.

You should now have a .jsonl file with all your saved items. Do whatever you want with it!
