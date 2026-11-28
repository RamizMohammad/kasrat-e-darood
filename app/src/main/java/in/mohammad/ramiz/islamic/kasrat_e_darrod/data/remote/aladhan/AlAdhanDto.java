package in.mohammad.ramiz.islamic.kasrat_e_darrod.data.remote.aladhan;

import com.google.gson.annotations.SerializedName;

/** DTOs for the AlAdhan prayer-times API ( https://api.aladhan.com/v1 ). */
public final class AlAdhanDto {
    private AlAdhanDto() {}

    public static class Response {
        public int code;
        public String status;
        public Data data;
    }

    public static class Data {
        public Timings timings;
        public DateInfo date;
        public Meta meta;
    }

    public static class Timings {
        @SerializedName("Fajr") public String fajr;
        @SerializedName("Sunrise") public String sunrise;
        @SerializedName("Dhuhr") public String dhuhr;
        @SerializedName("Asr") public String asr;
        @SerializedName("Maghrib") public String maghrib;
        @SerializedName("Isha") public String isha;
    }

    public static class DateInfo {
        public String readable;          // e.g. "01 Jan 2025"
        public Hijri hijri;
        public Gregorian gregorian;
    }

    public static class Hijri {
        public String date;              // "01-07-1446"
        public String day;               // "1"
        public String year;              // "1446"
        public Month month;
        public Weekday weekday;
    }

    public static class Gregorian {
        public String date;
        public String day;
        public String year;
        public Month month;
        public Weekday weekday;
    }

    public static class Month {
        public int number;
        public String en;
        public String ar;
    }

    public static class Weekday {
        public String en;
        public String ar;
    }

    public static class Meta {
        public double latitude;
        public double longitude;
        public String timezone;
    }
}
