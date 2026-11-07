# KiteBlocks Achievements (1.0.0 beta)

Upload the **contents** of this source ZIP into the GitHub repository (not the ZIP itself), preserving paths. GitHub Actions builds a downloadable JAR under Actions -> latest successful run -> Artifacts.

Install the JAR into Minehut `plugins/`, restart, and edit `plugins/KiteBlocksAchievements/config.yml`. The plugin copies the provided achievements blueprint and creates `players.yml` for progress/claims. Commands: `/achievements`, `/ach`, `/kba add <player> <category> <amount>` (admin-only), `/kba reload`.

## Currently implemented
- 6-row category / achievement-tier GUIs, category icons, pages, right-click claim, one-time saved claims, progress counters.
- Playtime counted by online minutes; survival block mining; mature crop breaking; player kills; animal/monster kills; fish caught.
- Reward commands, including configured Vote/PvP/Lucky/Royal crate keys and `/fly` unlock using LuckPerms. Credits/money via console commands.
- All tiers and reward data as in the provided YAML blueprint.

## Important limitations / setup
1. **Custom XP is NOT connected by default.** `reward_commands.xp` is set to `REQUIRES_MULTICURRENCY_API_INTEGRATION` in the source YAML. Configure this to the correct *console command* for your MultiCurrency XP currency (including `%player%` and `%amount%`) before XP tiers can be claimed. Do not guess a command, and verify it on a test player.
2. Some rewards are custom items (`pet_egg`, hoppers, blessings). Their `special_item_commands` values are not configured. Add your real server console commands; otherwise those tiers won't be claimable. To avoid partial reward loss, all commands for a tier are checked before awarding anything.
3. `boss_damage`, `mcmmo_levels`, `earn_money` have no automated integration or tiers yet. `fishing` can track progress but has no tiers in the reference. These categories show Coming Soon. Additional screenshot tiers weren't invented.
4. Mining counts mined stone/deepslate/ore; this is not exactly the same as all block types. Blocks placed then broken may still count. For a production prison server, add block-placement anti-farming and plugin API integrations.
5. Rewards are dispatched as console commands. A missing or mistyped downstream command may fail after a claim is recorded. **Test every reward on a staging server first.** Check ExcellentCrates key IDs, PlayerPoints command, EssentialsX economy, LuckPerms permissions.
6. The initial GUI tier slots include 20 slots per page. Default `Playtime Tier 10` unlocks `/fly` permanently (you can alter the config).
7. This is beta source prepared for GitHub build, not a tested JAR. Use Java 25 / Paper 26.2 and confirm Actions builds cleanly.

## Project layout
- `src/main/java/gg/kiteblocks/achievements/KiteBlocksAchievements.java`
- `src/main/resources/config.yml`
- `src/main/resources/plugin.yml`
- `build.gradle`, `settings.gradle`
- `.github/workflows/build.yml`
