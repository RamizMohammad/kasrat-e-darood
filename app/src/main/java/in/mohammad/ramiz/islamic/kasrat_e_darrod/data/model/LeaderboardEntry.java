package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model;

/** A row in the community standings leaderboard. */
public class LeaderboardEntry {
    public int rank;
    public String name;
    public String streak;
    public int total;
    public boolean goldAvatar;
    public String photoUrl;

    public LeaderboardEntry(int rank, String name, String streak, int total,
                            boolean goldAvatar, String photoUrl) {
        this.rank = rank;
        this.name = name;
        this.streak = streak;
        this.total = total;
        this.goldAvatar = goldAvatar;
        this.photoUrl = photoUrl;
    }
}
