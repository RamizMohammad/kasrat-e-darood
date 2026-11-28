# Stitch "Quiet Luxury" (teal) → Android port

Source: `stitch_kasrat_e_darood_app_design/` (28 screens). Each phase ends with a
build + device test by Ramiz; errors come back before the next phase starts.
Replaced files are copied to `_to_delete/pN_replaced/` before each phase.

| Phase | Scope | Stitch screens | Status |
|---|---|---|---|
| 1 | Design-system foundation: colours (light + night), Fraunces, type scale, radii/spacing, motion, buttons/inputs/chips/switch/segment/progress, Phosphor Light icons | design_system_board | done |
| 2 | Entry: splash, onboarding ×3, sign in, language (register / forgot styled to match) | onboarding_1–3, sign_in | done |
| 3 | App shell + Home: floating pill bottom nav (Home · Recite · Circles · Ranks · Me), Home | home | done |
| 4 | Recite flow: library, recitation detail, tasbih (day + night), review & log, success | recite, darood_e_ibrahimi, tasbih_counter(_night), review_log(_success) | done |
| 5 | Ranks + Hall of Fame | ranks, hall_of_fame | done |
| 6 | Circles: list, empty, new, join by code, join preview, circle board/activity, circle settings | circles(_empty), new_circle, join_circle_*, circle_home_*, circle_settings | **built (6a list, empty, new, join · 6b circle home, settings)** |
| 7 | Me, history, weekly report + 9:16 story, notifications, settings | me, history, weekly_report(_9_16), notifications, settings | **built (7a Me, History · 7b weekly report + story · 7c notifications, settings)** |
| 8 | Screens with no Stitch design (register, forgot password, add recitation, avatar studio, delete account, developer info), dark-mode pass, legacy-resource cleanup | — | |

## Phase 1 notes
- `amber` (old primary name) now = deep teal `#0F5E55`; night accent = gold `#C9A24B`.
- Kit tokens by their own names: `@color/k_*`, `@dimen/k_*`, `TextAppearance.K.*`.
- Icons: existing `ic_*` redrawn from Phosphor Light (same names); new ones as `ic_ph_*`.
- Fraunces is the variable font (`font/fraunces_var*.ttf`); each style sets `opsz`.

## Phase 2 notes
- Flow unchanged: language → sign in → onboarding → avatar studio.
- Sign-in banner is a vector stand-in (`drawable/k_signin_banner.xml`); Stitch's photo is on
  lh3.googleusercontent.com, which the dev machine can't reach. Drop a real image in as
  `drawable-nodpi/signin_banner.webp` and point `@id/banner` at it to replace.
- Onboarding slide 3 member photos → initials discs (`K.AvatarStack`).
- "Forgot password?" kept under the password field (not in Stitch) so reset stays reachable.
- Register / forgot password / language have no Stitch screen: kit styling only.
- New kit pieces: `K.Card`, `Widget.K.Button.Hero|Paper|Quiet`, `Widget.K.TextInputLayout.White`,
  `K.FieldLabel`, `Kasrat.Tag.Paper`, `ic_k_quran`, `splash_k_emblem`, `text_strong` (deep-ink/80).

## Phase 3 notes
- Bottom bar floats over the tab (`view_bottom_bar.xml`, 24dp sides, 20dp above the nav bar).
  `MainActivity.OWN_HEADER` lists tabs already ported: they hide the shared app header and
  pad their own scroll via `NavSpaceAware.onNavSpace()`. Flip each tab to `true` in its phase.
- Tab label "Groups" → "Circles" (user-facing string `tab_circles` only; code/API unchanged).
- Home removed vs the old screen (not in Stitch): Jumu'ah countdown card, quick-log chips,
  Quick tap button, Global circle card, prayer place name, header bell (bell still on other tabs).
- Daily intention = phone-only goal (`AppPrefs.dailyIntention`, default 1,000), tap the ring.
- "vs yesterday" computed from the last 100 submissions; hidden if they don't reach yesterday.
- Circle cards show the circle avatar + member count (API has no member photos).
- New views: `PrayerTimelineView`, `KRingView`; drawables `k_star_pattern`, `bg_k_hero_glow`.

## Colour pass (between Phase 3 and 4)
- App is always light: `MODE_NIGHT_NO` in `NoorApp` + `AppPrefs.applyDarkMode`; `values-night/`
  colours moved to `_to_delete/pc_replaced/` so a dark-mode phone (and the splash) stays ivory/white/teal.
  The Stitch night Tasbih screen will use the fixed `k_night_*` colours directly (Phase 4).
- Every colour token is now a Stitch hex or an alpha of one (teal ramps = deep teal at 25–75%).
- Medals: #1 gold, #2 deep-teal/60 (`silver`), #3 hairline (`bronze`), as in ranks_teal.
- Circle avatar colours, week-report PDF, pie chart, dialog icon ring, fallback avatar → Stitch palette.
- Launcher icon: white tile + deep-teal Open-Quran emblem (`ic_launcher_k_fg`).
- Left as is: avatar-studio skin/hair/outfit colours and profile "aura" colours (user personalisation).

## Phase 4 notes
- Recite tab now owns its header (`OWN_HEADER[1] = true`); log bar floats 12dp above the bottom bar.
- Favourites: phone-only (`util/Favourites`), heart on each card + detail, "Favourites" tab last.
- Category tabs are built from the server's categories (All · … · Favourites), not hard-coded.
- Review sheet: "Counts toward all N of your circles" and the success "Added to …" use
  `data/CircleNames` (filled by Home's /dashboard). Unknown → falls back to "2 recitations" / hidden.
- Footnote says "You can undo within 5 minutes" (the app's real undo window), not Stitch's 10.
- Success sheet stays until Done (no auto-close), as in Stitch.
- Detail: stats from the last 100 logs (total · this week since Friday · best day); "Source" card
  shows `reference`. Stitch's English/Hindi/Urdu tabs and the "Virtue" text need per-language
  translations + a virtue field in the API, so they're left out.
- Tasbih: number = count within the lap; gold dots = laps done; Day/Night via the sliders button
  (night = Stitch night palette on this screen only, saved per phone).

## Phase 5 notes
- Ranks owns its header (`OWN_HEADER[3] = true`); the pinned "You" row floats 12dp above the bar
  (`BoardPane.setBottomSpace`).
- The People / Groups segment is gone from Ranks (Stitch has only This week · All time). The
  circle-vs-circle ranking (`GroupRanksPane`) is kept in code, unused, in case it moves to Circles.
- Board header, podium and rows are shared with the circle board (GroupActivity): the circle
  version says "Your circle recited … darood this week"; Phase 6 reworks that screen's header.
- Rows: one hairline card built from row segments (`bg_k_row_top|mid|bottom|single`), movement as
  "↑2" (teal) / "↓1" (muted red). Avatars are the app's own (AuraAvatarView); Stitch's photos/initials
  are stand-ins.
- Hall of Fame: newest week = gold-framed hero (winner, #2 · #3); older weeks = compact cards.
  Stitch doesn't show the week's community total, so it's only in the TalkBack description now.
- Admins still get "close the week" as an outlined archive button next to the trophy.

## Phase 6a notes
- Circles owns its header (`OWN_HEADER[2] = true`). The global circle isn't listed (its board is Ranks).
- Circle emblems redrawn Stitch-style (`GroupAvatarView`): 4 line marks × 4 disc colours × 4 faint
  patterns. Stored code format unchanged (`grp1:sN.cN.pN`); old codes wrap into the new ranges.
- New circle is one page (no wizard). New circles use join_rule "code" and total_only false;
  approval / total-only stay editable in Circle settings.
- Join with code — NEEDS BACKEND:
    GET  /api/v1/groups/invite/{code}  → GroupDetail with preview fields (top, viewer_status, full); 404 = unknown
    POST /api/v1/groups/join {invite_code} → JoinResult {status: joined|requested, group}
- Card meta shows "14 members · You 1,240" (MyGroup has no rank-in-circle, so not "You're #3").

## Phase 6b notes
- Circle home (`GroupActivity`, `activity_group.xml`): CoordinatorLayout + AppBarLayout. The hero
  (64dp emblem, name, "14 members · Private", code capsule with copy/share, goal card with
  `KTickBarView`) scrolls away; the top bar then fades in a 32dp emblem + name. Board · Activity ·
  About text tabs stay pinned (`K.CircleTab`, 2dp teal underline). Activity / About fold the hero.
- Board: shared BoardPane; `BoardAdapter.Labels.GROUP` is compact — the "recited" card shows only
  for All time (the goal card already shows the week).
- Activity: day headings (Today / Yesterday / date) and a hairline timeline. Joins and role
  changes are quiet rows, logs get the teal-ring dot, announcements a quote card, goal reached and
  week winners a gold card. Handles `submission.created` / `recitation.logged` / `group.role` if the
  server sends them.
- About (no Stitch screen): description in Fraunces italic, a rules card, last week's top rows.
- Not a member → `JoinPreviewActivity.intentPublic` (joins with POST /groups/{id}/join). The old
  in-page preview and the "Log to group" button are gone.
- Circle settings (`GroupSettingsActivity`): rows open their own editor and save at once (PATCH
  with just that field). Goal & Privacy also carries "Who can join" and "Board" (approval and
  total-only, which New circle doesn't ask).
- Needs backend: GET /groups/{id}/members, POST …/members/{uid}/role|ban|approve,
  POST /groups/{id}/invite-code, POST /groups/{id}/announcements (all in API_CONTRACT).
- `dialog_number` caps at 5 digits for tasbih; goal dialogs lift it to 10.

## Phase 7a notes
- Me (`MeFragment`, `fragment_me.xml`) now draws its own header (`OWN_HEADER` all true) and pads
  for the floating bar. Hero: 88dp avatar in a 2dp white ring on `bg_k_aura_glow`, serif name,
  "Member since" (UserDto.created_at from GET /auth/me, saved once; hidden if the server omits it).
- Streak card: flame + "14 days" + Active streak, then Monday–Sunday dots from `days`
  (today ringed teal-soft). Stats: Lifetime · This week · Best streak · Circles (GET /groups/mine,
  global circle not counted). `KHeatmapView`: 12 weeks × 7, four teal tints relative to your best day.
- Rows: History · Weekly reports · Notifications · Settings. Avatar studio = tap the avatar.
  About and Sign out moved to Settings → Account (`SignOut` helper). Dropped from Me: Today /
  Month tiles, the year graph, per-recitation bars, the version line (still in About).
- History (`SubmissionsActivity`): month picker; pages of 100 load until the month is complete;
  "This month · total · N active days"; day cards from `bg_k_row_*` segments; "After Fajr" etc.
  from Home's saved prayer times (today's times used for every day); Undo capsule (5 min, not
  Stitch's 10-min Edit — editing a log has no endpoint).
- Unused now (Phase 8 cleanup): RecitationGraphView, StatTile, item_me_bar, item_submission,
  item_day_header, item_page_loader (check before removing).

## Phase 7b notes
- Weekly report (`WeekReportActivity`, `activity_week_report.xml`) is now *your* week, not the
  community week list. Weeks = GET /weeks/current + GET /weeks/hall-of-fame (26); opens on the
  last closed week, ‹ › in the eyebrow move between weeks.
- Numbers from your own logs (GET /submissions/me paged until the week is covered): total,
  `KDayBarsView` (7 bars ending on the closing Friday, best day gold with "1.8k"), avg/day,
  best day, most recited. Week label: start+1 day → close day ("15–21 Nov 2026").
- "Top 8% of the ummah": this week from the global board around you (needs `member_count`, else
  "#142 in the ummah"); closed weeks only if you're in that week's hall-of-fame top list.
  Streak and Circle results only for this week (the API has no past per-user/per-circle weeks);
  closed weeks show "Active days 5 of 7" instead of Streak and no circle card.
- Share as image: `view_week_story.xml` (390 × 693dp, `KStarPatternView`, gold frame, Arabic
  salawat, StoryHero number) drawn into a 1080 × 1920 PNG. Download PDF: WeekReportPdf with your
  recitations for the week.
- Unused now: item_week_report, view_week_share_card, communityStats()/weekHistory() calls.

## Phase 7c notes
- Notifications (`NotificationsActivity`): one list, Today / Earlier, full-bleed rows with
  hairlines, 44dp teal-soft / gold-soft discs by type, unread teal dot. Sources: categories
  `notifications` + `considerations` (closed weeks → opens the weekly report). The community
  "activity" feed and the Activity / Groups / System tabs are gone. Read state is on the phone
  (AppPrefs.notificationsSeen): "Mark all read" or leaving the screen marks everything seen.
- Settings (`SettingsActivity`): Account (avatar row → change avatar / delete account) ·
  Recitation (daily intention, default tasbih target, sound, haptics, keep screen on — phone
  only, wired into TasbihActivity and Haptics) · Reminders (daily time = server
  `reminders.enabled/time` via MaterialTimePicker; Friday and prayer-time nudges are phone-only
  switches with no scheduler yet) · Appearance (Theme segmented: Light only, Dark / System
  disabled until the Phase 8 dark-mode decision; Language segmented) · Prayer times and
  Notifications (not in Stitch, kept) · Privacy ("Show me on global ranks" = !hide_on_boards) ·
  About (version, rate, about) · Sign out.
- Unused now: item_notification, item_switch_row (check), item_menu_row (still used elsewhere?).

## Phase 8a notes (cleanup + remaining screens)
- 120+ files nothing referenced any more moved to `_to_delete/p8_unused/` (same relative paths):
  old layouts (quick log, group card, prayer row, week card, share card, me bars, submission,
  notification, switch row, app header…), ~90 old drawables/icons, and unused classes
  (PieChartView, LoaderAdapter, RecitationGraphView, GroupRanksPane, RelativeTime,
  KeyboardInsets, Anims, GifLoader, LeaderboardEntry, GoalProgressBar, GoalTrackView, StatTile)
  plus their styleables. NoorApp and SplashActivity stay (manifest).
- Shell: the shared app header (`view_app_header`, bell, streak pill) is gone; every tab draws
  its own header. MainActivity no longer listens to Totals.
- Shared surfaces restyled at the source: `bg_plate` (white + hairline, 28dp), choice rows
  (hairline / teal hairline when chosen, 20dp), pop-up menu, `Kasrat.SectionLabel` → eyebrow,
  KasratDialog (Hero / Quiet buttons, kit type; danger keeps red with a red glow), ChoiceSheet
  (kit handle, serif title, Paper / Hero buttons).
- Stitch-style header (44dp outlined back + HeadlineLgMobile title) on Delete account, About,
  Add recitation; Avatar studio top bar uses outlined buttons.
- Still open: dark mode (Settings shows Light only), README/repo hygiene from the first scan.

## Phase D notes (dark theme)
- `values-night/colors.xml`: the Stitch night palette for every token that changes — canvas
  #141210, cards #1E1B18, hairlines #2E2A25, text warm ivory, muted #A89F96. Gold (#C9A24B)
  replaces deep teal as the accent, with night-ink text on gold buttons (as the tasbih night
  screen does). Kit names that act as surfaces/ink (k_warm_ivory, k_pure_white, k_deep_ink*,
  k_teal_soft…) follow the theme; k_night_* and k_gold don't. `values-night/bools.xml` flips the
  system-bar icons.
- Views that hard-coded colours now read resources: GroupAvatarView / GroupAvatarPicker
  (k_emblem_disc_0…3, k_emblem_mark), KHeatmapView + legend (k_heat_0…3), KDayBarsView
  (best day = k_milestone, a paler gold in the dark), KTickBarView (k_tick), KStarPatternView,
  RankRows ring, TasbihRingView day dots.
- `drawable-night/`: 17 drawables with literal teal / light fills (glows, pills, discs, search,
  pinned row, goal pill, success disc…) re-tinted teal → gold, light → night surfaces.
- Fixed in both themes: launcher icon background (icon_tile), switch thumb (white), the share
  story (inflated with a light configuration), the PDF.
- Settings → Theme: Light (default) · Dark · System — AppPrefs.theme()/setTheme(), applied in
  NoorApp. Tasbih picks its bar icons from the theme too.

## Phase F notes (typography)
- Serif: Fraunces → Playfair Display (variable wght 400–900 + italic, from google/fonts, OFL in
  docs/font-licenses). `font/playfair.xml`; `font/fraunces.xml` kept as an alias for code
  (R.font.fraunces). Playfair has no `tnum`, so serif styles ask for `lnum` (proportional digits).
- Weight ladder: display / story numbers 700–800 (tight tracking), screen titles 700, headline-sm
  600, serif title 700, serif italic 400 (quotes, translations), serif label 600; Jakarta title
  700, label-lg 700, label-sm 500, eyebrow 700 at 0.14em, body 400. Bold name spans (activity,
  cart bar, circles summary) use 700.
- Originals (type.xml, fraunces.xml, Fraunces TTFs) in `_to_delete/pf_replaced/`.

