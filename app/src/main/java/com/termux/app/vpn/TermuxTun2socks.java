package com.termux.app.vpn;

import androidx.annotation.Keep;

/**
 * JNI bindings for hev-socks5-tunnel. Native method names must match hev-jni.c
 * ({@code TProxyStartService} etc.) and {@code PKGNAME}/{@code CLSNAME} ndk flags.
 */
@Keep
final class TermuxTun2socks {

    static {
        System.loadLibrary("hev-socks5-tunnel");
    }

    private TermuxTun2socks() {}

    static native boolean TProxyStartService(String configPath, int fd);

    static native boolean TProxyStopService();

    static native boolean TProxyIsRunning();

    static native long[] TProxyGetStats();
}
