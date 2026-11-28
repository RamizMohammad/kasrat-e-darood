package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.model;

/** A recitation entry in the library (mirrors the backend RecitationOut DTO). */
public class Recitation {
    public String id;
    public String arabicName;
    public String englishName;
    public String translation;
    public String tag;          // e.g. "Chapter 36", "Daily Prayer"
    public int iconRes;         // local drawable fallback

    public Recitation(String id, String arabicName, String englishName,
                      String translation, String tag, int iconRes) {
        this.id = id;
        this.arabicName = arabicName;
        this.englishName = englishName;
        this.translation = translation;
        this.tag = tag;
        this.iconRes = iconRes;
    }
}
