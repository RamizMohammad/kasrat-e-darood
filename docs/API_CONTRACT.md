# Noor — REST API Contract (v1)

Base URL: `/api/v1`  ·  Auth: `Authorization: Bearer <access_token>` (app JWT).
All list endpoints use cursor pagination: `?cursor=<id>&limit=20` →
`{ "items": [...], "next_cursor": "<id|null>" }`.
All responses are JSON. Errors: `{ "error": { "code": "...", "message": "..." } }`.

## Auth
| Method | Path | Body | Returns |
|---|---|---|---|
| POST | `/auth/firebase` | `{ id_token }` (Firebase ID token) | `{ access_token, refresh_token, user }` |
| POST | `/auth/refresh` | `{ refresh_token }` | `{ access_token, refresh_token }` |
| POST | `/auth/logout` | `{ refresh_token }` | `204` |
| GET  | `/auth/me` | — | `User` |

## Users
| GET | `/users/me` | — | `User` |
| PATCH | `/users/me` | `{ display_name?, photo_url?, preferences? }` | `User` |
| GET | `/users/{id}` | — | `User (public)` |
| GET | `/users/search?q=` | — | `[User]` |

## Groups
| POST | `/groups` | `{ name, description?, privacy }` | `Group` |
| GET | `/groups` | — (mine) | `[Group]` |
| GET | `/groups/{id}` | — | `Group` |
| PATCH | `/groups/{id}` | partial | `Group` |
| POST | `/groups/{id}/archive` | — | `Group` |
| POST | `/groups/join` | `{ invite_code }` | `Membership` |
| POST | `/groups/{id}/leave` | — | `204` |
| POST | `/groups/{id}/invite-code` | — | `{ invite_code }` |

## Members
| GET | `/groups/{id}/members` | — | `[Membership]` |
| POST | `/groups/{id}/members/{uid}/role` | `{ role }` | `Membership` |
| POST | `/groups/{id}/members/{uid}/ban` | — | `Membership` |
| POST | `/groups/{id}/members/{uid}/approve` | — | `Membership` |

## Categories & Recitations
| GET | `/categories` | — | `[Category]` |
| GET | `/recitations?group_id=&q=&category=` | — | `[Recitation]` |
| GET | `/recitations/{id}` | — | `Recitation` |
| POST | `/recitations` | admin: full body | `Recitation` |
| PATCH | `/recitations/{id}` | admin | `Recitation` |

## Submissions  (core)
| POST | `/submissions` | `{ group_id, recitation_id, count, note?, client_uuid }` | `{ submission, totals }` |
| POST | `/submissions/bulk` | `{ group_id, items:[{recitation_id,count,client_uuid}] }` | `{ totals }` |
| GET | `/submissions?group_id=&week_id=&user_id=` | — | paginated `[Submission]` |
| DELETE | `/submissions/{id}` | (within undo window) | `{ totals }` |

`totals` = `{ user_week, user_lifetime, group_week, today }`.

## Weekly session
| GET | `/groups/{id}/week/current` | — | `WeeklySession` |
| GET | `/groups/{id}/week/history` | — | paginated `[WeeklySession]` |
| POST | `/groups/{id}/week/present` | admin → **Friday lock** | `{ locked_week, hall_of_fame, new_week }` |

## Dashboard (one optimized call)
| GET | `/dashboard?group_id=` | — | `DashboardResponse` |

```
DashboardResponse {
  current_week, user_total, today_total, weekly_total, monthly_total,
  lifetime_total, group_total, remaining_days, streak,
  goal { target, progress, percent },
  top_contributors: [ { user, total, rank } ],
  recent_activity: [ ActivityItem ],
  weekly_chart: [ { day, value } ]
}
```

## Leaderboard
| GET | `/leaderboards?group_id=&scope=weekly&around_me=false&limit=100` | — | `{ entries:[{user,total,rank,streak}], me:{rank,total} }` |

## Statistics
| GET | `/statistics/user?group_id=&range=weekly` | — | `UserStats` |
| GET | `/statistics/group/{id}?range=weekly` | — | `GroupStats` (most_read_surah, most_active_day/hour, heatmap, completion_rate, growth) |

## Activity feed
| GET | `/groups/{id}/feed` | — | paginated `[ActivityItem]` |
| POST | `/feed/{id}/react` | `{ emoji }` | `ActivityItem` |

## Notifications / Achievements / Goals / Reports / Announcements
| GET | `/notifications` · POST `/notifications/{id}/read` |
| GET | `/achievements?group_id=` · GET `/goals` · POST `/goals` |
| POST | `/reports` `{ type, range, format }` → `{ report_id }` · GET `/reports/{id}` |
| GET/POST | `/groups/{id}/announcements` |
| POST | `/devices/token` `{ fcm_token, platform }` |

## Health / Ops
`GET /health` · `GET /health/ready` · `GET /health/live` · `GET /metrics`.

## WebSocket
`WS /ws?group_id=` emits events:
`submission.created`, `leaderboard.updated`, `group.total.updated`,
`announcement.created`, `member.joined`, `week.locked`,
`notification`, `achievement.unlocked`.
