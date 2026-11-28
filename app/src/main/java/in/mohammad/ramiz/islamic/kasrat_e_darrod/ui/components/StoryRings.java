package in.mohammad.ramiz.islamic.kasrat_e_darrod.ui.components;

import android.content.Context;

/**
 * Kasrat has no stories. This stands in for Confess's StoryRings so the ported
 * {@link AuraAvatarView} works unchanged: every avatar reports "no story".
 */
public final class StoryRings {
    public static final int NONE = 0;
    public static final int UNSEEN = 1;
    public static final int SEEN = 2;

    public interface Listener { void onStoryRingsChanged(); }

    private StoryRings() { }

    public static void addListener(Listener l) { }
    public static void removeListener(Listener l) { }
    public static int state(String alias) { return NONE; }
    public static boolean open(Context ctx, String alias) { return false; }
}
