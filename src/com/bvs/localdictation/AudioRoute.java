package com.bvs.localdictation;

import android.content.Context;
import android.media.AudioDeviceCallback;
import android.media.AudioDeviceInfo;
import android.media.AudioManager;
import android.os.Handler;
import android.os.Looper;
import android.util.Log;

/**
 * Chooses where dictation audio comes from: the phone's built-in microphone or a Bluetooth headset.
 *
 * A headset connected for music (A2DP) has no microphone path. Its microphone works only over SCO
 * (or LE Audio), which the system opens only when an app selects the headset as the communication
 * device. So for the headset source we call setCommunicationDevice, wait until the link is up, and
 * release it when dictation stops (otherwise music stays in low "call" quality).
 */
final class AudioRoute {
    private static final String TAG = "LocalDictation";
    private static final long LINK_TIMEOUT_MS = 1500;

    /** Headsets connected or disconnected while a route is held. Called on the main thread. */
    interface Listener { void onHeadsetsChanged(); }

    private final AudioManager am;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Listener listener;

    /** The headset we selected as the communication device, or null. */
    private AudioDeviceInfo selected;
    private Runnable pending;
    private AudioManager.OnCommunicationDeviceChangedListener linkListener;
    private boolean watching;

    private final AudioDeviceCallback deviceCallback = new AudioDeviceCallback() {
        @Override public void onAudioDevicesAdded(AudioDeviceInfo[] added) { changed(added); }
        @Override public void onAudioDevicesRemoved(AudioDeviceInfo[] removed) { changed(removed); }
        private void changed(AudioDeviceInfo[] devs) {
            for (AudioDeviceInfo d : devs) {
                if (isHeadset(d)) { listener.onHeadsetsChanged(); return; }
            }
        }
    };

    AudioRoute(Context c, Listener l) {
        am = c.getSystemService(AudioManager.class);
        listener = l;
    }

    private static boolean isHeadset(AudioDeviceInfo d) {
        int t = d.getType();
        return t == AudioDeviceInfo.TYPE_BLUETOOTH_SCO || t == AudioDeviceInfo.TYPE_BLE_HEADSET;
    }

    /** A Bluetooth headset that can carry a microphone (SCO preferred over LE Audio), or null. */
    AudioDeviceInfo findHeadset() {
        AudioDeviceInfo ble = null;
        for (AudioDeviceInfo d : am.getAvailableCommunicationDevices()) {
            if (d.getType() == AudioDeviceInfo.TYPE_BLUETOOTH_SCO) return d;
            if (d.getType() == AudioDeviceInfo.TYPE_BLE_HEADSET && ble == null) ble = d;
        }
        return ble;
    }

    /**
     * True if the held route no longer matches the devices: the headset is wanted and one appeared
     * while recording from the phone, or the headset in use went away. Device callbacks also fire
     * for already connected devices, so callers check this before rerouting.
     */
    boolean needsReroute(boolean wantHeadset) {
        if (!wantHeadset) return false;
        AudioDeviceInfo h = findHeadset();
        if (selected == null) return h != null;
        return h == null || h.getId() != selected.getId();
    }

    /** Headset currently in use, or null (phone microphone). */
    AudioDeviceInfo headset() { return selected; }

    /** Display name of the headset in use ("" if none). */
    String headsetName() {
        if (selected == null) return "";
        CharSequence n = selected.getProductName();
        return n == null ? "" : n.toString();
    }

    /**
     * Sets up the route and runs {@code ready} once audio can be recorded (at once for the phone,
     * after the headset link is up or a timeout for the headset). While a route is held, headset
     * connections and disconnections are reported to the listener.
     */
    void apply(boolean wantHeadset, Runnable ready) {
        cancelPending();
        watch(true);
        AudioDeviceInfo h = wantHeadset ? findHeadset() : null;
        if (h == null) {
            clearCommunicationDevice();
            ready.run();
            return;
        }
        if (selected != null && selected.getId() == h.getId() && sameAsCurrent(h)) {
            ready.run();
            return;
        }
        boolean ok;
        try {
            ok = am.setCommunicationDevice(h);
        } catch (RuntimeException e) {
            Log.w(TAG, "setCommunicationDevice failed", e);
            ok = false;
        }
        Log.d(TAG, "route: headset type=" + h.getType() + " ok=" + ok);
        if (!ok) {
            selected = null;
            ready.run();
            return;
        }
        selected = h;
        if (sameAsCurrent(h)) {
            ready.run();
            return;
        }
        pending = () -> {
            Runnable r = pending;
            cancelPending();
            if (r != null) {
                Log.d(TAG, "route: link " + (sameAsCurrent(h) ? "up" : "timeout"));
                ready.run();
            }
        };
        final Runnable fire = pending;
        linkListener = d -> { if (d != null && d.getId() == h.getId()) fire.run(); };
        am.addOnCommunicationDeviceChangedListener(handler::post, linkListener);
        handler.postDelayed(fire, LINK_TIMEOUT_MS);
    }

    private boolean sameAsCurrent(AudioDeviceInfo h) {
        AudioDeviceInfo cur = am.getCommunicationDevice();
        return cur != null && cur.getId() == h.getId();
    }

    /**
     * The input device to record from: the headset's microphone for the headset route, the
     * built-in microphone otherwise. Null if not found (the system then picks).
     */
    AudioDeviceInfo inputDevice() {
        AudioDeviceInfo[] ins = am.getDevices(AudioManager.GET_DEVICES_INPUTS);
        if (selected != null) {
            for (AudioDeviceInfo d : ins) {
                if (d.getType() == selected.getType() && d.getAddress().equals(selected.getAddress())) return d;
            }
            for (AudioDeviceInfo d : ins) if (d.getType() == selected.getType()) return d;
            return null;
        }
        for (AudioDeviceInfo d : ins) if (d.getType() == AudioDeviceInfo.TYPE_BUILTIN_MIC) return d;
        return null;
    }

    /** Releases the headset link and stops watching devices. Safe to call any time. */
    void release() {
        cancelPending();
        watch(false);
        clearCommunicationDevice();
    }

    private void clearCommunicationDevice() {
        if (selected == null) return;
        selected = null;
        try {
            am.clearCommunicationDevice();
        } catch (RuntimeException e) {
            Log.w(TAG, "clearCommunicationDevice failed", e);
        }
        Log.d(TAG, "route: released");
    }

    private void cancelPending() {
        if (pending != null) {
            handler.removeCallbacks(pending);
            pending = null;
        }
        if (linkListener != null) {
            am.removeOnCommunicationDeviceChangedListener(linkListener);
            linkListener = null;
        }
    }

    private void watch(boolean on) {
        if (on == watching) return;
        watching = on;
        if (on) am.registerAudioDeviceCallback(deviceCallback, handler);
        else am.unregisterAudioDeviceCallback(deviceCallback);
    }
}
