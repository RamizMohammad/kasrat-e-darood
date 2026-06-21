# Noor — MongoDB Data Model

MongoDB Atlas. Every document carries the base fields:

| field        | type      | notes                                  |
|--------------|-----------|----------------------------------------|
| `_id`        | ObjectId  | primary key                            |
| `created_at` | datetime  | UTC                                    |
| `updated_at` | datetime  | UTC, bumped on every write             |
| `created_by` | ObjectId? | user id                                |
| `updated_by` | ObjectId? | user id                                |
| `deleted`    | bool      | **soft delete** — records are never hard-deleted |
| `version`    | int       | optimistic locking                     |

## Collections

### users
`firebase_uid` (unique), `display_name`, `email`, `phone`, `photo_url`,
`role` (super_admin|member…global), `lifetime_total`, `last_active_at`,
`preferences{}`. Indexes: unique `firebase_uid`, unique sparse `email`, text on
`display_name`.

### groups
`name`, `description`, `banner_url`, `logo_url`, `privacy` (public|private),
`invite_code` (unique), `owner_id`, `timezone`, `hijri_method`, `member_count`,
`settings{}`. Indexes: unique `invite_code`, text `name,description`.

### memberships
`group_id`, `user_id`, `role` (owner|admin|moderator|member|viewer),
`status` (active|pending|banned|muted), `joined_at`.
Indexes: **unique compound** `(group_id, user_id)`; `(group_id, role)`.

### recitations
`group_id` (null = global library), `arabic_name`, `english_name`,
`translation`, `category_id`, `description`, `benefits`, `reference`, `color`,
`icon`, `sort_order`, `visibility`, `default_increment`. Admin-managed only.
Indexes: `(group_id, sort_order)`, text `arabic_name,english_name,translation`.

### categories
`name`, `slug`, `color`, `icon`, `sort_order`. Index: unique `slug`.

### weekly_sessions
`group_id`, `week_number`, `hijri_week`, `gregorian_week`, `start_date`,
`end_date`, `status` (ACTIVE|LOCKED|ARCHIVED), `totals{}`, `locked_at`,
`locked_by`. Invariant: **at most one ACTIVE per group**.
Indexes: **partial-unique** `(group_id)` where `status="ACTIVE"`;
`(group_id, week_number)`.

### submissions
`group_id`, `week_id`, `user_id`, `recitation_id`, `count`, `note`,
`source` (app|bulk|offline_sync), `client_uuid` (idempotency).
Indexes: unique sparse `(user_id, client_uuid)`; `(week_id, user_id)`;
`(group_id, recitation_id)`; `(created_at)`.

### leaderboards
Cached read-model snapshots: `group_id`, `scope` (daily|weekly|monthly|yearly|
lifetime), `period_key`, `entries[]{user_id,total,rank}`, `generated_at`.
Index: unique `(group_id, scope, period_key)`.

### statistics
Per-week / per-user snapshots produced at Friday lock (most-read surah, most
active day/hour, averages, heatmap buckets, completion rate).

### activity_feed
`group_id`, `actor_id`, `type`, `text`, `payload{}`, `reactions{emoji:count}`.
Indexes: `(group_id, created_at desc)`.

### notifications
`user_id`, `type`, `title`, `body`, `data{}`, `read`. TTL optional on old read
items. Index: `(user_id, created_at desc)`.

### device_tokens
`user_id`, `fcm_token` (unique), `platform`. For push.

### announcements / reports / achievements / badges / goals /
### achievement_progress / audit_logs / user_preferences
As described in the product spec — scaffolded models, extended over time.

## Index strategy summary
- **Unique**: firebase_uid, invite_code, (group_id,user_id), (user_id,client_uuid).
- **Partial unique**: one ACTIVE weekly_session per group.
- **Compound** for every list/range query (group+time, week+user).
- **Text** for search (users, groups, recitations).
- **TTL** for ephemeral notifications / expired invite requests.

## Transactions
Multi-collection writes (submission roll-up, Friday lock) run inside MongoDB
multi-document transactions so totals can never drift from submissions.
