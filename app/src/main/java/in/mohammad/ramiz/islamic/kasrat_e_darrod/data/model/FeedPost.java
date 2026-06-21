package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model;

/** A post in the community feed (Groups tab). */
public class FeedPost {
    public String initial;
    public String name;
    public String time;
    public String title;
    public int reactionFire;
    public int reactionHeart;

    public FeedPost(String initial, String name, String time, String title,
                    int reactionFire, int reactionHeart) {
        this.initial = initial;
        this.name = name;
        this.time = time;
        this.title = title;
        this.reactionFire = reactionFire;
        this.reactionHeart = reactionHeart;
    }
}
