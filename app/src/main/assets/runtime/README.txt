Production assets go here. Supported layout:
- runtime/common/*.sh for shared launch helpers
- runtime/arm64-v8a/proot, busybox, websockify, pulseaudio, x11vnc
- runtime/armeabi-v7a/proot, busybox, websockify, pulseaudio, x11vnc
- runtime/x86_64/proot, busybox, websockify, pulseaudio, x11vnc
- runtime/<abi>/droidspaces for rooted high-performance mode
All native files must be built from trusted source, signed, and marked executable after extraction.
