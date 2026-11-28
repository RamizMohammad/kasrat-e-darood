package in.mohammad.ramiz.islamic.kasrat_e_darrod.data;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/**
 * The names of the circles you're in, as /dashboard last listed them. Every log counts
 * toward all of them, so the review sheet can say "Counts toward all 3 of your circles"
 * and the success screen "Added to Khan Household, Jummah Sisters & Family".
 */
public final class CircleNames {

    @Nullable private static List<String> names;

    private CircleNames() { }

    public static void set(@Nullable List<String> list) {
        names = list == null ? null : Collections.unmodifiableList(new ArrayList<>(list));
    }

    /** null until /dashboard has answered once this session. */
    @Nullable
    public static List<String> get() { return names; }
}
