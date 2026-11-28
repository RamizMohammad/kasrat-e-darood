package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model;

/** A single group-activity event shown on the home dashboard. */
public class ActivityItem {
    public String initial;
    public String text;
    public String meta;
    public String time;
    public boolean goldAvatar;

    public ActivityItem(String initial, String text, String meta, String time, boolean goldAvatar) {
        this.initial = initial;
        this.text = text;
        this.meta = meta;
        this.time = time;
        this.goldAvatar = goldAvatar;
    }
}
