# Event membership check

Public endpoint, available in every environment:

```text
GET https://p01--baddie-bot--tjjwsp2l8xzp.code.run/api/event/members/exists?username=example
```

No Authorization or X-Event-Key header is required. A successful response is a JSON boolean (`true` or `false`). Pass Discord `user.username`, not the guild nickname or global display name. Matching is exact and case-sensitive. Blank or oversized input returns HTTP 400; Discord/configuration failures return 502/503, not `false`.

The bot loads all guild members, including every page and members with no selected roles. It caches an immutable `List<String>` containing their usernames in process memory for 10 minutes from completion of the successful load. First use after expiry refreshes the list. Concurrent requests share a single refresh. Restarting the bot clears the cache; each bot instance has its own cache. Membership and username changes can take up to 10 minutes to appear. A failed refresh never publishes an incomplete cache.

Hosting must configure `service.discord.guildId` and `service.discord.token`. The application imports `envs/${APP_ENV:local}.properties` when available. Discord must allow listing guild members (Server Members Intent).

This replaces the previous `/api/event/members/{userId}` endpoint and its X-Event-Key contract. The separate local-only `/api/discord/members` endpoint keeps its existing role-filtered behavior.

For the Halloween site, set `DISCORDBOT_URL` to the hosting base URL above and change its membership request to `/api/event/members/exists?username=...`. Expect a boolean response. During OAuth, use the username supplied by Discord's `/users/@me` response, rather than trusting a username typed by a visitor. The site must still authenticate the visitor through Discord before saving a participant.

The endpoint supports server-to-server calls. Browser cross-origin access is not enabled; use the site's backend for the OAuth membership check.

## Member profile and avatar

```text
GET /api/event/members/profile?username=luckerfourleaf
```

Uses the same exact Discord username lookup and shared 10-minute cache as `/exists`. The response adds `avatarUrl` without changing existing fields:

```json
{
  "member": true,
  "displayName": "это sunny",
  "roles": ["998350106023698432"],
  "joined_at": "2020-06-01T00:00:00Z",
  "avatarUrl": "https://cdn.discordapp.com/avatars/123456789012345679/aaaaaaaaaaaaaaaaaaaaaaaaaaaaaaaa.png?size=512"
}
```

Avatar priority: guild-specific avatar, global user avatar, then Discord's default avatar. Custom avatars request size 512; animated hashes use animated WebP. Default avatar images have a fixed resolution even when a size is requested. URL construction follows [Discord's image formatting reference](https://github.com/discord/discord-api-docs/blob/main/developers/reference.mdx#image-formatting).

For an unknown username, `member` is `false`, `displayName`, `joined_at` and `avatarUrl` are `null`, and `roles` is empty. Avatar changes follow the shared cache lifetime; restarting the bot clears that cache. Deploy/restart the updated bot for the new field to appear in the hosted API. The BADDIE website already reads `avatarUrl`.
