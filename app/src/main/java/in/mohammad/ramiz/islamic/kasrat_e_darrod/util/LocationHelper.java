package in.mohammad.ramiz.islamic.kasrat_e_darrod.util;

import android.Manifest;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationManager;

import androidx.core.content.ContextCompat;

import java.util.List;

/**
 * Minimal last-known-location lookup via {@link LocationManager} (no Play
 * Services dependency). Falls back to a default city when permission is denied
 * or no fix is cached.
 */
public final class LocationHelper {
    // Default: Makkah. Used when no location permission/fix is available.
    public static final double DEFAULT_LAT = 21.3890824;
    public static final double DEFAULT_LON = 39.8579118;

    private LocationHelper() {}

    public static boolean hasPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    /** Returns {lat, lon}; device last-known if available, otherwise the default. */
    public static double[] lastKnownOrDefault(Context context) {
        if (!hasPermission(context)) {
            return new double[]{DEFAULT_LAT, DEFAULT_LON};
        }
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm != null) {
                List<String> providers = lm.getProviders(true);
                Location best = null;
                for (String provider : providers) {
                    @SuppressWarnings("MissingPermission")
                    Location l = lm.getLastKnownLocation(provider);
                    if (l == null) continue;
                    if (best == null || l.getAccuracy() < best.getAccuracy()) {
                        best = l;
                    }
                }
                if (best != null) {
                    return new double[]{best.getLatitude(), best.getLongitude()};
                }
            }
        } catch (SecurityException ignored) {
            // fall through to default
        }
        return new double[]{DEFAULT_LAT, DEFAULT_LON};
    }

    /** Called once with [lat, lon], or null when there's no permission or no fix. */
    public interface Found { void onLocation(@androidx.annotation.Nullable double[] latLon); }

    /**
     * The phone's location for prayer times: the last known fix if there is one,
     * otherwise a fresh network/GPS fix (given up after 10 seconds). Never a made-up default.
     */
    public static void current(Context context, Found cb) {
        if (!hasPermission(context)) {
            cb.onLocation(null);
            return;
        }
        double[] last = lastKnown(context);
        if (last != null) {
            cb.onLocation(last);
            return;
        }
        LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
        String provider = null;
        if (lm != null && lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) provider = LocationManager.NETWORK_PROVIDER;
        else if (lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER)) provider = LocationManager.GPS_PROVIDER;
        if (provider == null) {
            cb.onLocation(null);
            return;
        }
        java.util.concurrent.atomic.AtomicBoolean done = new java.util.concurrent.atomic.AtomicBoolean();
        android.os.CancellationSignal cancel = new android.os.CancellationSignal();
        android.os.Handler main = new android.os.Handler(android.os.Looper.getMainLooper());
        Runnable timeout = () -> {
            if (done.compareAndSet(false, true)) {
                cancel.cancel();
                cb.onLocation(null);
            }
        };
        main.postDelayed(timeout, 10_000);
        try {
            //noinspection MissingPermission
            lm.getCurrentLocation(provider, cancel, context.getMainExecutor(), loc -> {
                if (!done.compareAndSet(false, true)) return;
                main.removeCallbacks(timeout);
                cb.onLocation(loc == null ? null : new double[]{loc.getLatitude(), loc.getLongitude()});
            });
        } catch (SecurityException | IllegalArgumentException e) {
            if (done.compareAndSet(false, true)) {
                main.removeCallbacks(timeout);
                cb.onLocation(null);
            }
        }
    }

    /** The most accurate last known fix, or null. */
    @androidx.annotation.Nullable
    public static double[] lastKnown(Context context) {
        if (!hasPermission(context)) return null;
        try {
            LocationManager lm = (LocationManager) context.getSystemService(Context.LOCATION_SERVICE);
            if (lm == null) return null;
            Location best = null;
            for (String provider : lm.getProviders(true)) {
                @SuppressWarnings("MissingPermission")
                Location l = lm.getLastKnownLocation(provider);
                if (l != null && (best == null || l.getAccuracy() < best.getAccuracy())) best = l;
            }
            return best == null ? null : new double[]{best.getLatitude(), best.getLongitude()};
        } catch (SecurityException e) {
            return null;
        }
    }
}
