"""Import a large recitation library: all 114 surahs, the Hisn al-Muslim
azkar and duas, the six Kalimas and well-known Darood, into the global
library (the same `recitations` table the app's Recite tab reads).

Run on the server (inside the API container, which has the database and
internet):

    docker compose exec api python -m scripts_import_library --dry-run   # report only
    docker compose exec api python -m scripts_import_library             # import

Sources
    * Surahs:  api.alquran.cloud/v1/surah  (names, meanings, ayah counts)
    * Azkar:   hisnmuslim.com/api/en/       (Arabic, transliteration, English,
                                             repeat count, per Hisn al-Muslim)
    * Kalimas and Darood: written below (no public API carries them).
      Have someone knowledgeable check this text before release.

No duplicates, ever. For each item, in this order:
    1. Same source_key ("quran:36", "hisn:75", "curated:kalima-2") already in
       the table → that row. Only its EMPTY fields are filled in; nothing you
       or an admin wrote is overwritten.
    2. The hand-written seed already has it (same surah number, or a known
       name like "Kalma Tayyaba") → that row is adopted (gets the source_key),
       empty fields filled in, names kept.
    3. The same Arabic text is already in the library (compared without
       diacritics, punctuation or spacing; Hisn al-Muslim repeats some azkar
       in several chapters) → skipped.
    4. Otherwise → inserted.
A unique index on source_key (migration 0004) backs this up in the database,
and a Postgres advisory lock stops two runs at the same time. Everything is
one transaction: if anything fails, nothing is written.
"""
from __future__ import annotations

import argparse
import asyncio
import json
import re
import unicodedata
from dataclasses import dataclass, field

import httpx
from loguru import logger
from sqlalchemy import select, text

from app.core.logging import configure_logging
from app.database.migrate import upgrade_head
from app.database.postgres import close_postgres, db, session_scope
from app.models.category import Category, Recitation

QURAN_SURAHS_URL = "https://api.alquran.cloud/v1/surah"
HISN_INDEX_URL = "https://www.hisnmuslim.com/api/en/husn_en.json"
HISN_CHAPTER_URL = "https://www.hisnmuslim.com/api/en/{id}.json"

# Categories (by slug). Existing ones are reused; "azkar" is added.
CATEGORIES = {
    "surah": {"name": "Surah", "color": "#004532", "icon": "book", "sort_order": 0},
    "darood": {"name": "Darood", "color": "#735c00", "icon": "heart", "sort_order": 1},
    "kalma-verse": {"name": "Kalma/Verse", "color": "#1d5d49", "icon": "shield", "sort_order": 2},
    "azkar": {"name": "Azkar", "color": "#1d5d49", "icon": "beads", "sort_order": 5},
}

# Fields an import may fill in when they're empty on an existing row.
FILLABLE = ("arabic_name", "urdu_name", "transliteration", "translation",
            "description", "reference", "category", "category_id")


# ── Curated: the six Kalimas and well-known Darood ──────────────────────────
# `aliases`: english_name(s) the hand-written seed may already use for it.

CURATED: list[dict] = [
    {
        "key": "curated:kalima-1", "category": "kalma-verse", "sort_order": 200,
        "aliases": ["kalma tayyaba (first kalima)", "kalma tayyaba", "first kalima"],
        "english_name": "First Kalima · Tayyab (Purity)",
        "urdu_name": "پہلا کلمہ طیب",
        "arabic_name": "لَا إِلٰهَ إِلَّا اللّٰهُ مُحَمَّدٌ رَّسُوْلُ اللّٰهِ",
        "transliteration": "Lā ilāha illallāhu Muḥammadur rasūlullāh",
        "translation": "There is no god but Allah; Muhammad is the Messenger of Allah.",
        "default_increment": 33,
    },
    {
        "key": "curated:kalima-2", "category": "kalma-verse", "sort_order": 201,
        "aliases": ["kalma shahadat", "second kalima"],
        "english_name": "Second Kalima · Shahadat (Testimony)",
        "urdu_name": "دوسرا کلمہ شہادت",
        "arabic_name": "أَشْهَدُ أَنْ لَّا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيْكَ لَهُ وَأَشْهَدُ أَنَّ مُحَمَّدًا عَبْدُهُ وَرَسُوْلُهُ",
        "transliteration": "Ash-hadu al lā ilāha illallāhu waḥdahu lā sharīka lahu wa ash-hadu anna Muḥammadan ʿabduhu wa rasūluh",
        "translation": "I bear witness that there is no god but Allah, alone, without partner, and I bear witness that Muhammad is His servant and Messenger.",
        "default_increment": 11,
    },
    {
        "key": "curated:kalima-3", "category": "kalma-verse", "sort_order": 202,
        "aliases": ["kalma tamjeed", "third kalima"],
        "english_name": "Third Kalima · Tamjeed (Glorification)",
        "urdu_name": "تیسرا کلمہ تمجید",
        "arabic_name": "سُبْحَانَ اللّٰهِ وَالْحَمْدُ لِلّٰهِ وَلَا إِلٰهَ إِلَّا اللّٰهُ وَاللّٰهُ أَكْبَرُ وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ الْعَلِيِّ الْعَظِيْمِ",
        "transliteration": "Subḥānallāhi wal-ḥamdu lillāhi wa lā ilāha illallāhu wallāhu akbar, wa lā ḥawla wa lā quwwata illā billāhil-ʿaliyyil-ʿaẓīm",
        "translation": "Glory be to Allah, all praise is for Allah, there is no god but Allah, and Allah is the Greatest. There is no might nor power except with Allah, the Most High, the Most Great.",
        "default_increment": 11,
    },
    {
        "key": "curated:kalima-4", "category": "kalma-verse", "sort_order": 203,
        "aliases": ["kalma tauheed", "kalma tawheed", "fourth kalima"],
        "english_name": "Fourth Kalima · Tauheed (Oneness)",
        "urdu_name": "چوتھا کلمہ توحید",
        "arabic_name": "لَا إِلٰهَ إِلَّا اللّٰهُ وَحْدَهُ لَا شَرِيْكَ لَهُ لَهُ الْمُلْكُ وَلَهُ الْحَمْدُ يُحْيِيْ وَيُمِيْتُ وَهُوَ حَيٌّ لَّا يَمُوْتُ أَبَدًا أَبَدًا ذُو الْجَلَالِ وَالْإِكْرَامِ بِيَدِهِ الْخَيْرُ وَهُوَ عَلٰى كُلِّ شَيْءٍ قَدِيْرٌ",
        "transliteration": "Lā ilāha illallāhu waḥdahu lā sharīka lahu, lahul-mulku wa lahul-ḥamdu, yuḥyī wa yumītu, wa huwa ḥayyul-lā yamūtu abadan abadā, dhul-jalāli wal-ikrām, biyadihil-khayr, wa huwa ʿalā kulli shay'in qadīr",
        "translation": "There is no god but Allah, alone, without partner. His is the dominion and His is the praise. He gives life and causes death, and He is Ever-Living and will never die. Possessor of majesty and honour; in His hand is all good, and He has power over all things.",
        "default_increment": 11,
    },
    {
        "key": "curated:kalima-5", "category": "kalma-verse", "sort_order": 204,
        "aliases": ["kalma astaghfar", "kalma istighfar", "fifth kalima"],
        "english_name": "Fifth Kalima · Astaghfar (Seeking Forgiveness)",
        "urdu_name": "پانچواں کلمہ استغفار",
        "arabic_name": "أَسْتَغْفِرُ اللّٰهَ رَبِّيْ مِنْ كُلِّ ذَنْبٍ أَذْنَبْتُهُ عَمَدًا أَوْ خَطَأً سِرًّا أَوْ عَلَانِيَةً وَأَتُوْبُ إِلَيْهِ مِنَ الذَّنْبِ الَّذِيْ أَعْلَمُ وَمِنَ الذَّنْبِ الَّذِيْ لَا أَعْلَمُ إِنَّكَ أَنْتَ عَلَّامُ الْغُيُوْبِ وَسَتَّارُ الْعُيُوْبِ وَغَفَّارُ الذُّنُوْبِ وَلَا حَوْلَ وَلَا قُوَّةَ إِلَّا بِاللّٰهِ الْعَلِيِّ الْعَظِيْمِ",
        "transliteration": "Astaghfirullāha rabbī min kulli dhambin adhnabtuhu ʿamadan aw khaṭa'an sirran aw ʿalāniyatan wa atūbu ilayhi minadh-dhambil-ladhī aʿlamu wa minadh-dhambil-ladhī lā aʿlamu, innaka anta ʿallāmul-ghuyūbi wa sattārul-ʿuyūbi wa ghaffārudh-dhunūbi, wa lā ḥawla wa lā quwwata illā billāhil-ʿaliyyil-ʿaẓīm",
        "translation": "I seek forgiveness from Allah, my Lord, for every sin I committed knowingly or unknowingly, secretly or openly, and I turn to Him from the sin I know and the sin I do not know. You are the Knower of the unseen, the Concealer of faults and the Forgiver of sins. There is no might nor power except with Allah, the Most High, the Most Great.",
        "default_increment": 11,
    },
    {
        "key": "curated:kalima-6", "category": "kalma-verse", "sort_order": 205,
        "aliases": ["kalma radd-e-kufr", "kalma radde kufr", "sixth kalima"],
        "english_name": "Sixth Kalima · Radd-e-Kufr (Rejecting Disbelief)",
        "urdu_name": "چھٹا کلمہ ردِ کفر",
        "arabic_name": "اَللّٰهُمَّ إِنِّيْ أَعُوْذُ بِكَ مِنْ أَنْ أُشْرِكَ بِكَ شَيْئًا وَّأَنَا أَعْلَمُ بِهٖ وَأَسْتَغْفِرُكَ لِمَا لَا أَعْلَمُ بِهٖ تُبْتُ عَنْهُ وَتَبَرَّأْتُ مِنَ الْكُفْرِ وَالشِّرْكِ وَالْكِذْبِ وَالْغِيْبَةِ وَالْبِدْعَةِ وَالنَّمِيْمَةِ وَالْفَوَاحِشِ وَالْبُهْتَانِ وَالْمَعَاصِيْ كُلِّهَا وَأَسْلَمْتُ وَأَقُوْلُ لَا إِلٰهَ إِلَّا اللّٰهُ مُحَمَّدٌ رَّسُوْلُ اللّٰهِ",
        "transliteration": "Allāhumma innī aʿūdhu bika min an ushrika bika shay'an wa ana aʿlamu bihi, wa astaghfiruka limā lā aʿlamu bihi, tubtu ʿanhu wa tabarra'tu minal-kufri wash-shirki wal-kidhbi wal-ghībati wal-bidʿati wan-namīmati wal-fawāḥishi wal-buhtāni wal-maʿāṣī kullihā, wa aslamtu wa aqūlu lā ilāha illallāhu Muḥammadur rasūlullāh",
        "translation": "O Allah, I seek refuge in You from knowingly associating anything with You, and I seek Your forgiveness for what I do not know. I repent of it and disown disbelief, polytheism, lying, backbiting, innovation, tale-bearing, indecency, slander and all sins. I submit, and I say: there is no god but Allah; Muhammad is the Messenger of Allah.",
        "default_increment": 11,
    },
    {
        "key": "curated:darood-ibrahimi", "category": "darood", "sort_order": 100,
        "aliases": ["darood-e-ibrahimi", "darood e ibrahimi", "durood ibrahim", "salawat ibrahimiyya"],
        "english_name": "Darood-e-Ibrahimi",
        "urdu_name": "درودِ ابراہیمی",
        "arabic_name": "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ وَّعَلٰى اٰلِ مُحَمَّدٍ كَمَا صَلَّيْتَ عَلٰى إِبْرَاهِيْمَ وَعَلٰى اٰلِ إِبْرَاهِيْمَ إِنَّكَ حَمِيْدٌ مَّجِيْدٌ، اَللّٰهُمَّ بَارِكْ عَلٰى مُحَمَّدٍ وَّعَلٰى اٰلِ مُحَمَّدٍ كَمَا بَارَكْتَ عَلٰى إِبْرَاهِيْمَ وَعَلٰى اٰلِ إِبْرَاهِيْمَ إِنَّكَ حَمِيْدٌ مَّجِيْدٌ",
        "transliteration": "Allāhumma ṣalli ʿalā Muḥammadin wa ʿalā āli Muḥammadin kamā ṣallayta ʿalā Ibrāhīma wa ʿalā āli Ibrāhīma innaka ḥamīdum majīd. Allāhumma bārik ʿalā Muḥammadin wa ʿalā āli Muḥammadin kamā bārakta ʿalā Ibrāhīma wa ʿalā āli Ibrāhīma innaka ḥamīdum majīd",
        "translation": "O Allah, send prayers upon Muhammad and the family of Muhammad, as You sent prayers upon Ibrahim and the family of Ibrahim; You are Praiseworthy, Glorious. O Allah, bless Muhammad and the family of Muhammad, as You blessed Ibrahim and the family of Ibrahim; You are Praiseworthy, Glorious.",
        "reference": "Sahih al-Bukhari 3370",
        "default_increment": 11,
    },
    {
        "key": "curated:darood-short", "category": "darood", "sort_order": 101,
        "aliases": [],
        "english_name": "Allahumma Salli ʿala Muhammad",
        "urdu_name": "اللّٰہم صل علیٰ محمد",
        "arabic_name": "اَللّٰهُمَّ صَلِّ عَلٰى مُحَمَّدٍ وَّعَلٰى اٰلِ مُحَمَّدٍ",
        "transliteration": "Allāhumma ṣalli ʿalā Muḥammadin wa ʿalā āli Muḥammad",
        "translation": "O Allah, send prayers upon Muhammad and the family of Muhammad.",
        "default_increment": 100,
    },
    {
        "key": "curated:darood-salawat", "category": "darood", "sort_order": 102,
        "aliases": [],
        "english_name": "Sallallahu ʿalayhi wa Sallam",
        "urdu_name": "صلی اللہ علیہ وسلم",
        "arabic_name": "صَلَّى اللّٰهُ عَلَيْهِ وَسَلَّمَ",
        "transliteration": "Ṣallallāhu ʿalayhi wa sallam",
        "translation": "May Allah send prayers and peace upon him.",
        "default_increment": 100,
    },
    {
        "key": "curated:darood-sayyidina", "category": "darood", "sort_order": 103,
        "aliases": [],
        "english_name": "Allahumma Salli wa Sallim ʿala Sayyidina Muhammad",
        "urdu_name": "اللّٰہم صل وسلم علیٰ سیدنا محمد",
        "arabic_name": "اَللّٰهُمَّ صَلِّ وَسَلِّمْ وَبَارِكْ عَلٰى سَيِّدِنَا مُحَمَّدٍ وَّعَلٰى اٰلِهٖ وَصَحْبِهٖ",
        "transliteration": "Allāhumma ṣalli wa sallim wa bārik ʿalā sayyidinā Muḥammadin wa ʿalā ālihī wa ṣaḥbih",
        "translation": "O Allah, send prayers, peace and blessings upon our master Muhammad, his family and his companions.",
        "default_increment": 100,
    },
]


# ── Arabic comparison ───────────────────────────────────────────────────────

_DIACRITICS = re.compile("[ؐ-ًؚ-ٰٟۖ-ۭـ]")
_NOT_LETTER = re.compile(r"[^ء-يٱ-ۓ]")
_ALEF = str.maketrans({"أ": "ا", "إ": "ا", "آ": "ا", "ٱ": "ا", "ى": "ي", "ة": "ه",
                       "ی": "ي", "ک": "ك", "ۃ": "ه", "ہ": "ه", "ؤ": "و", "ئ": "ي"})


def arabic_key(s: str | None) -> str:
    """Same text → same key, whatever the diacritics, punctuation or spacing."""
    if not s:
        return ""
    s = unicodedata.normalize("NFKC", s)
    s = _DIACRITICS.sub("", s).translate(_ALEF)
    return _NOT_LETTER.sub("", s)


def clean(s: str | None, limit: int | None = None) -> str | None:
    if s is None:
        return None
    s = " ".join(str(s).split()).strip()
    # Hisn al-Muslim wraps many azkar in "( … )".
    if s.startswith("(") and s.endswith(")") and s.count("(") == 1:
        s = s[1:-1].strip()
    if not s:
        return None
    return s[:limit] if limit else s


# ── Fetching ────────────────────────────────────────────────────────────────

async def get_json(client: httpx.AsyncClient, url: str) -> dict | list:
    """GET with a few retries (the sources are free services)."""
    url = url.replace("http://", "https://", 1)
    last: Exception | None = None
    for attempt in range(4):
        try:
            r = await client.get(url)
            r.raise_for_status()
            # strict=False: some Hisn al-Muslim files have raw tabs/newlines
            # inside strings, which strict JSON rejects. clean() tidies them.
            return json.loads(r.content.decode("utf-8-sig", errors="replace"), strict=False)
        except (httpx.HTTPError, ValueError) as exc:
            last = exc
            await asyncio.sleep(1.5 * (attempt + 1))
    raise RuntimeError(f"Could not fetch {url}: {last}")


async def fetch_surahs(client: httpx.AsyncClient) -> list[dict]:
    body = await get_json(client, QURAN_SURAHS_URL)
    out = []
    for s in body.get("data", []):
        n = int(s["number"])
        arabic = clean(s.get("name"))
        latin = clean(s.get("englishName"))
        revelation = clean(s.get("revelationType")) or ""
        out.append({
            "key": f"quran:{n}", "category": "surah", "surah": n, "sort_order": 1000 + n,
            "aliases": [],
            "english_name": f"Surah {latin}"[:200],
            "urdu_name": _DIACRITICS.sub("", arabic or "")[:200] or None,
            "arabic_name": arabic,
            "transliteration": latin,
            "translation": clean(s.get("englishNameTranslation")),
            "reference": f"Quran {n}",
            "description": f"{s.get('numberOfAyahs')} ayahs · {revelation}".strip(" ·"),
            "default_increment": 1,
            "icon": "book",
        })
    if len(out) != 114:
        raise RuntimeError(f"Expected 114 surahs, got {len(out)}")
    return out


async def fetch_hisn(client: httpx.AsyncClient) -> list[dict]:
    index = await get_json(client, HISN_INDEX_URL)
    chapters = next((v for v in index.values() if isinstance(v, list)), []) if isinstance(index, dict) else []
    if not chapters:
        raise RuntimeError("Hisn al-Muslim index is empty")
    sem = asyncio.Semaphore(6)
    skipped_chapters: list[int] = []

    async def one(ch: dict) -> list[dict]:
        cid = int(ch["ID"])
        title = clean(ch.get("TITLE")) or f"Hisn al-Muslim {cid}"
        async with sem:
            try:
                body = await get_json(client, ch.get("TEXT") or HISN_CHAPTER_URL.format(id=cid))
            except RuntimeError as exc:
                # One broken chapter mustn't stop the rest; re-running later picks it up.
                logger.warning("Skipping Hisn al-Muslim chapter {} ({}): {}", cid, title, exc)
                skipped_chapters.append(cid)
                return []
        items = next((v for v in body.values() if isinstance(v, list)), []) if isinstance(body, dict) else []
        rows = []
        for i, it in enumerate(items, start=1):
            arabic = clean(it.get("ARABIC_TEXT"))
            if not arabic:
                continue
            name = title if len(items) == 1 else f"{title} ({i})"
            repeat = it.get("REPEAT")
            try:
                repeat = max(1, int(repeat))
            except (TypeError, ValueError):
                repeat = 1
            rows.append({
                "key": f"hisn:{int(it['ID'])}", "category": "azkar",
                "sort_order": 5000 + cid * 100 + i, "aliases": [],
                "english_name": name[:200],
                "urdu_name": None,
                "arabic_name": arabic,
                "transliteration": clean(it.get("LANGUAGE_ARABIC_TRANSLATED_TEXT")),
                "translation": clean(it.get("TRANSLATED_TEXT")),
                "reference": f"Hisn al-Muslim #{int(it['ID'])}",
                "description": title,
                "default_increment": repeat,
                "icon": "beads",
            })
        return rows

    chunks = await asyncio.gather(*(one(ch) for ch in chapters))
    if skipped_chapters:
        logger.warning("Hisn al-Muslim chapters skipped: {}", sorted(skipped_chapters))
    if len(skipped_chapters) > len(chapters) // 2:
        raise RuntimeError("Most Hisn al-Muslim chapters failed to download; not importing a partial set")
    return [r for chunk in chunks for r in chunk]


# ── Writing ─────────────────────────────────────────────────────────────────

@dataclass
class Report:
    inserted: int = 0
    filled: int = 0
    adopted: int = 0
    unchanged: int = 0
    duplicate_text: int = 0
    examples: list[str] = field(default_factory=list)


async def ensure_categories() -> dict[str, Category]:
    session = db()
    out: dict[str, Category] = {}
    for slug, data in CATEGORIES.items():
        cat = (await session.execute(select(Category).where(Category.slug == slug))).scalar()
        if cat is None:
            cat = Category(slug=slug, **data)
            session.add(cat)
            await session.flush()
            logger.info("Category added: {}", data["name"])
        out[slug] = cat
    return out


async def import_items(items: list[dict], cats: dict[str, Category], report: Report) -> None:
    session = db()
    rows = (await session.execute(select(Recitation).where(
        Recitation.group_id.is_(None), Recitation.deleted.is_(False)))).scalars().all()

    by_key = {r.source_key: r for r in rows if r.source_key}
    by_name = {r.english_name.strip().lower(): r for r in rows if r.english_name}
    by_surah = {}
    for r in rows:
        m = re.fullmatch(r"\s*Quran\s+(\d{1,3})\s*", r.reference or "")
        if m:
            by_surah.setdefault(int(m.group(1)), r)
    by_text = {arabic_key(r.arabic_name): r for r in rows if len(arabic_key(r.arabic_name)) >= 8}

    for item in items:
        cat = cats[item["category"]]
        data = {k: item.get(k) for k in ("english_name", "urdu_name", "arabic_name", "transliteration",
                                         "translation", "reference", "description", "default_increment",
                                         "sort_order", "icon")}
        data["category"] = cat.name
        data["category_id"] = cat.id
        data["color"] = cat.color

        row = by_key.get(item["key"])
        adopted = False
        if row is None:
            row = by_surah.get(item["surah"]) if "surah" in item else None
            if row is None:
                for alias in [item["english_name"], *item.get("aliases", [])]:
                    row = by_name.get(alias.strip().lower())
                    if row is not None:
                        break
            if row is not None and row.source_key and row.source_key != item["key"]:
                row = None                      # belongs to another imported item
            adopted = row is not None

        tkey = arabic_key(item.get("arabic_name"))
        if row is None and len(tkey) >= 8 and tkey in by_text:
            report.duplicate_text += 1
            continue

        if row is None:
            row = Recitation(group_id=None, visibility="public", source_key=item["key"], **data)
            session.add(row)
            report.inserted += 1
            by_key[item["key"]] = row
            by_name[row.english_name.strip().lower()] = row
            if len(tkey) >= 8:
                by_text[tkey] = row
            if len(report.examples) < 8:
                report.examples.append(f"+ {item['category']}: {row.english_name}")
            continue

        # Existing row: never overwrite, only fill what's empty.
        changed = False
        if adopted:
            row.source_key = item["key"]
            by_key[item["key"]] = row
            report.adopted += 1
            changed = True
        for k in FILLABLE:
            if getattr(row, k) in (None, "") and data.get(k) not in (None, ""):
                setattr(row, k, data[k])
                changed = True
        if changed:
            row.touch()
            if not adopted:
                report.filled += 1
        else:
            report.unchanged += 1
        if len(tkey) >= 8:
            by_text.setdefault(tkey, row)

    await session.flush()


async def run(dry_run: bool) -> Report:
    configure_logging()
    await upgrade_head()                                  # needs migration 0004
    report = Report()

    async with httpx.AsyncClient(timeout=httpx.Timeout(20.0), follow_redirects=True,
                                 headers={"User-Agent": "kasrat-e-darood-library-import/1.0"}) as client:
        logger.info("Fetching surahs …")
        surahs = await fetch_surahs(client)
        logger.info("Fetching Hisn al-Muslim …")
        hisn = await fetch_hisn(client)
    logger.info("Fetched {} surahs, {} azkar, {} curated", len(surahs), len(hisn), len(CURATED))

    try:
        async with session_scope() as session:
            # One import at a time, even if run twice by accident.
            await session.execute(text("SELECT pg_advisory_xact_lock(hashtext('kasrat_import_library'))"))
            cats = await ensure_categories()
            # Curated first: where Hisn al-Muslim has the same text (e.g. Darood-e-Ibrahimi),
            # the curated entry with its proper name is the one kept.
            await import_items(CURATED, cats, report)
            await import_items(surahs, cats, report)
            await import_items(hisn, cats, report)
            if dry_run:
                raise _DryRun()
    except _DryRun:
        logger.warning("Dry run: nothing was written (rolled back)")
    finally:
        await close_postgres()
    return report


class _DryRun(Exception):
    pass


def main() -> None:
    parser = argparse.ArgumentParser(description="Import surahs, azkar, Kalimas and Darood.")
    parser.add_argument("--dry-run", action="store_true", help="fetch and compare, write nothing")
    args = parser.parse_args()
    r = asyncio.run(run(args.dry_run))
    for line in r.examples:
        logger.info(line)
    logger.success(
        "Library import {}: inserted={} adopted_from_seed={} filled_empty_fields={} "
        "already_there={} skipped_same_text={}",
        "(dry run)" if args.dry_run else "done",
        r.inserted, r.adopted, r.filled, r.unchanged, r.duplicate_text)


if __name__ == "__main__":
    main()
