package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote;

import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.List;

import okhttp3.Call;
import okhttp3.Connection;
import okhttp3.EventListener;
import okhttp3.Handshake;
import okhttp3.Protocol;
import okhttp3.Response;

/**
 * Debug builds only: one logcat line per request saying where the time went,
 *
 *   KasratNet  GET /api/v1/groups/… 200 · total 1610 ms · dns 2 · connect 0 (reused) · tls 0 ·
 *              wait 1590 (server + network) · body 4 · h2 · 1.2.3.4
 *
 * "reused" means an existing connection was used (no DNS/connect/TLS).
 * Filter logcat by the tag KasratNet.
 */
final class NetTiming extends EventListener {

    static final EventListener.Factory FACTORY = call -> new NetTiming();
    private static final String TAG = "KasratNet";

    private long start, dnsStart, dns, connectStart, connect, tlsStart, tls,
            requestEnd, firstByte, bodyEnd;
    private boolean newConnection;
    @Nullable private String protocol, address;

    private static long now() { return System.nanoTime(); }
    private static long ms(long from, long to) { return from == 0 || to == 0 ? 0 : (to - from) / 1_000_000; }

    @Override public void callStart(@NonNull Call call) { start = now(); }
    @Override public void dnsStart(@NonNull Call call, @NonNull String domain) { dnsStart = now(); }
    @Override public void dnsEnd(@NonNull Call call, @NonNull String domain, @NonNull List<java.net.InetAddress> list) {
        dns += ms(dnsStart, now());
    }
    @Override public void connectStart(@NonNull Call call, @NonNull InetSocketAddress addr, @NonNull Proxy proxy) {
        connectStart = now();
        newConnection = true;
    }
    @Override public void secureConnectStart(@NonNull Call call) { tlsStart = now(); }
    @Override public void secureConnectEnd(@NonNull Call call, @Nullable Handshake h) { tls += ms(tlsStart, now()); }
    @Override public void connectEnd(@NonNull Call call, @NonNull InetSocketAddress addr, @NonNull Proxy proxy,
                                     @Nullable Protocol p) {
        connect += ms(connectStart, now());
    }
    @Override public void connectFailed(@NonNull Call call, @NonNull InetSocketAddress addr, @NonNull Proxy proxy,
                                        @Nullable Protocol p, @NonNull IOException e) {
        Log.w(TAG, "connect to " + addr + " failed after " + ms(connectStart, now()) + " ms: " + e);
    }
    @Override public void connectionAcquired(@NonNull Call call, @NonNull Connection c) {
        protocol = c.protocol().toString();
        address = c.route().socketAddress().getAddress().getHostAddress();
    }
    @Override public void requestHeadersEnd(@NonNull Call call, @NonNull okhttp3.Request r) { requestEnd = now(); }
    @Override public void requestBodyEnd(@NonNull Call call, long bytes) { requestEnd = now(); }
    @Override public void responseHeadersStart(@NonNull Call call) { firstByte = now(); }
    @Override public void responseBodyEnd(@NonNull Call call, long bytes) { bodyEnd = now(); }

    @Override public void callEnd(@NonNull Call call) { log(call, null); }
    @Override public void callFailed(@NonNull Call call, @NonNull IOException e) { log(call, e); }

    private void log(@NonNull Call call, @Nullable IOException e) {
        long end = now();
        String path = call.request().url().encodedPath();
        String line = call.request().method() + " " + path
                + " · total " + ms(start, end) + " ms"
                + " · dns " + dns
                + " · connect " + connect + (newConnection ? "" : " (reused)")
                + " · tls " + tls
                + " · wait " + ms(requestEnd, firstByte) + " (server + network)"
                + " · body " + ms(firstByte, bodyEnd)
                + (protocol != null ? " · " + protocol : "")
                + (address != null ? " · " + address : "")
                + (e != null ? " · FAILED " + e : "");
        if (e != null) Log.w(TAG, line); else Log.i(TAG, line);
    }
}
