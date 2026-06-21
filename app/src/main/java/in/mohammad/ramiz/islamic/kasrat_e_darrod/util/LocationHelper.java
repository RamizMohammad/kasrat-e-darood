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
}
