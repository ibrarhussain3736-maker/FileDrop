# FileDrop

Send and receive files between phones on the same Wi-Fi or hotspot.
Compatible with `filedrop.py` (same protocol).

## Use
1. Both phones on the same Wi-Fi (or one phone's hotspot).
2. Receiver: Receive tab, Start receiving, read the address shown.
3. Sender: Send tab, choose a file, type the receiver IP (add :port if it is not 5001), Send.
Received files go to Downloads/FileDrop (change in Settings).
You can also share a file from any app to FileDrop.

## Build the APK from Termux
    termux-setup-storage
    cp ~/storage/downloads/FileDrop.zip ~ && cd ~
    pkg install -y unzip git
    unzip FileDrop.zip && cd FileDrop
    bash push.sh https://github.com/YOUR_USER/YOUR_REPO.git

Create an empty repo on GitHub first. When git asks for a password, paste a
personal access token (GitHub, Settings, Developer settings, Tokens, "repo" scope).
Then open the repo's Actions tab, open the latest "Build APK" run and download
the FileDrop-apk artifact (unzip it to get app-debug.apk).
