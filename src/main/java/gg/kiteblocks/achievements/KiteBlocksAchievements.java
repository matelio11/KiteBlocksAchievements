package gg.kiteblocks.achievements;

import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Monster;
import org.bukkit.entity.Animals;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.GameMode;
import org.bukkit.block.data.Ageable;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.InventoryHolder;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitTask;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

public final class KiteBlocksAchievements extends JavaPlugin implements Listener {
    private File dataFile;
    private FileConfiguration data;
    private final Map<UUID, MenuState> openMenus = new HashMap<>();
    private final List<String> categoryOrder = List.of("playtime", "boss_damage", "mining", "pvp_kills", "fishing", "farming", "mcmmo_levels", "earn_money", "mob_grinding");
    private static final int[] TIER_SLOTS = {10,11,12,13,14,15,16,19,20,21,22,23,24,25,28,29,30,31,32,33};

    private record MenuState(String category, int page) {}
    private record AchMenuHolder(String category, int page) implements InventoryHolder {
        @Override public Inventory getInventory() { return null; }
    }

    @Override public void onEnable() {
        saveDefaultConfig();
        dataFile = new File(getDataFolder(), "players.yml");
        data = YamlConfiguration.loadConfiguration(dataFile);
        Bukkit.getPluginManager().registerEvents(this, this);
        Bukkit.getScheduler().runTaskTimer(this, () -> {
            for (Player player : Bukkit.getOnlinePlayers()) {
                addProgress(player, "playtime", 1);
            }
        }, 1200L, 1200L);
        Bukkit.getScheduler().runTaskTimer(this, this::saveData, 6000L, 6000L);
        getLogger().info("KiteBlocksAchievements beta enabled. XP and special-item rewards require admin configuration.");
    }

    @Override public void onDisable() { saveData(); }
    private void saveData() {
        if (data == null) return;
        try { data.save(dataFile); } catch (IOException e) { getLogger().severe("Failed to save player progress: " + e.getMessage()); }
    }
    private String key(Player player, String category) { return "players." + player.getUniqueId() + "." + category; }
    private long progress(Player p, String category) { return data.getLong(key(p, category) + ".progress", 0); }
    private void addProgress(Player p, String category, long amount) {
        if (amount <= 0) return;
        String path = key(p, category) + ".progress";
        data.set(path, Math.min(Long.MAX_VALUE - amount, data.getLong(path, 0)) + amount);
    }
    private boolean claimed(Player p, String category, int tier) {
        return data.getBoolean(key(p, category) + ".claimed." + tier, false);
    }
    private void setClaimed(Player p, String category, int tier) {
        data.set(key(p, category) + ".claimed." + tier, true);
        saveData();
    }
    private ConfigurationSection category(String id) { return getConfig().getConfigurationSection("categories." + id); }
    private List<Map<?, ?>> tiers(String id) {
        ConfigurationSection s = category(id);
        return s == null ? List.of() : s.getMapList("tiers");
    }
    private long number(Object obj) {
        if (obj instanceof Number n) return n.longValue();
        try { return Long.parseLong(String.valueOf(obj)); } catch (Exception ex) { return 0; }
    }
    private int tierIndex(Map<?, ?> tier) { return (int) number(tier.get("tier")); }
    private String color(String s) { return ChatColor.translateAlternateColorCodes('&', s); }
    private Material material(String s, Material fallback) {
        try { return Material.valueOf(s.toUpperCase(java.util.Locale.ROOT)); } catch (Exception ex) { return fallback; }
    }
    private ItemStack item(Material mat, String title, String... lore) {
        return item(mat, title, Arrays.asList(lore));
    }
    private ItemStack item(Material mat, String title, List<String> lore) {
        ItemStack i = new ItemStack(mat);
        ItemMeta meta = i.getItemMeta();
        meta.setDisplayName(color(title));
        meta.setLore(lore.stream().map(this::color).toList());
        i.setItemMeta(meta);
        return i;
    }
    private String display(String id) {
        ConfigurationSection s = category(id);
        return s == null ? id : s.getString("display", id);
    }
    private void fill(Inventory inv) {
        Material fill = material(getConfig().getString("gui.fill", "GRAY_STAINED_GLASS_PANE"), Material.GRAY_STAINED_GLASS_PANE);
        ItemStack pane = item(fill, "&7", List.of());
        for (int i=0;i<inv.getSize();i++) inv.setItem(i, pane);
    }
    private void openRoot(Player p) {
        Inventory inv = Bukkit.createInventory(new AchMenuHolder(null, 0), 54, color(getConfig().getString("gui.title", "&dKiteBlocks &8» &fAchievements")));
        fill(inv);
        for (String id : categoryOrder) {
            ConfigurationSection c = category(id);
            if (c == null) continue;
            int slot = getConfig().getInt("gui.category_slots." + id, -1);
            if (slot < 0 || slot >= 54) continue;
            int count = tiers(id).size();
            List<String> lore = new ArrayList<>();
            lore.add("&7Progress: &f" + progress(p,id));
            lore.add("&7Available tiers: &f" + count);
            lore.add(count == 0 ? "&cComing soon" : "&eLeft-click to view tiers");
            inv.setItem(slot, item(material(c.getString("icon", "BOOK"), Material.BOOK), "&d"+display(id), lore));
        }
        inv.setItem(40, item(Material.FEATHER, "&bFly Command", "&7Unlock through Playtime Tier 10", "&eRight-click to claim if eligible"));
        openMenus.put(p.getUniqueId(), new MenuState(null, 0));
        p.openInventory(inv);
    }
    private void openCategory(Player p, String id, int page) {
        List<Map<?, ?>> all = tiers(id);
        if (all.isEmpty()) { p.sendMessage(color("&eNo tiers configured for " + display(id) + " yet.")); return; }
        int lastPage = Math.max(0, (all.size()-1) / TIER_SLOTS.length);
        page = Math.max(0, Math.min(page, lastPage));
        Inventory inv = Bukkit.createInventory(new AchMenuHolder(id, page), 54, color("&dKiteBlocks &8» &f"+display(id)));
        fill(inv);
        for (int i=0;i<TIER_SLOTS.length;i++) {
            int index = page*TIER_SLOTS.length+i;
            if (index >= all.size()) break;
            Map<?, ?> tier = all.get(index);
            int n = tierIndex(tier);
            long target = number(tier.get("requirement"));
            long current = progress(p,id);
            boolean done = claimed(p,id,n);
            List<String> lore = new ArrayList<>();
            lore.add("&7Requirement: &f"+current+"&7/&f"+target);
            lore.add("&7Rewards:");
            Object rawRewards = tier.get("rewards");
            if (rawRewards instanceof List<?> rewards) {
                for (Object r : rewards) if (r instanceof Map<?, ?> rm) lore.add("&7- &f"+rewardText(rm));
            }
            if (done) lore.add("&aAlready claimed");
            else if (target <= 0) lore.add("&cNot configured");
            else if (current >= target) lore.add("&eRight-click to claim!");
            else lore.add("&cNot unlocked yet");
            inv.setItem(TIER_SLOTS[i], item(done ? Material.LIME_STAINED_GLASS_PANE : current >= target ? Material.EMERALD : Material.RED_STAINED_GLASS_PANE, "&dTier " + n, lore));
        }
        inv.setItem(49, item(Material.ARROW, "&eBack", "&7Return to categories"));
        inv.setItem(45, item(Material.ARROW, "&ePrevious Page"));
        inv.setItem(53, item(Material.ARROW, "&eNext Page"));
        openMenus.put(p.getUniqueId(), new MenuState(id, page));
        p.openInventory(inv);
    }
    private String rewardText(Map<?, ?> rm) {
        if (rm.containsKey("xp")) return number(rm.get("xp")) + " custom XP";
        if (rm.containsKey("credits")) return number(rm.get("credits")) + " Credits";
        if (rm.containsKey("money")) return "$" + number(rm.get("money"));
        if (rm.containsKey("key")) return number((rm.containsKey("amount") ? rm.get("amount") : 1)) + " " + rm.get("key") + " keys";
        if (rm.containsKey("permission")) return "/fly permission";
        if (rm.containsKey("special_item")) return "Special: " + rm.get("special_item");
        return "Unknown reward";
    }
    @Override public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("achievements")) {
            if (!(sender instanceof Player player)) { sender.sendMessage("Players only."); return true; }
            openRoot(player); return true;
        }
        if (!sender.hasPermission("kiteblocks.achievements.admin")) { sender.sendMessage("No permission."); return true; }
        if (args.length >= 4 && args[0].equalsIgnoreCase("add")) {
            Player target = Bukkit.getPlayerExact(args[1]);
            if (target == null) { sender.sendMessage("Player must be online."); return true; }
            if (category(args[2]) == null) { sender.sendMessage("Unknown category."); return true; }
            long amount;
            try { amount = Long.parseLong(args[3]); } catch (NumberFormatException ex) { sender.sendMessage("Enter a number."); return true; }
            if (amount <= 0) { sender.sendMessage("Amount must be positive."); return true; }
            addProgress(target, args[2], amount); saveData(); sender.sendMessage("Updated " + target.getName() + "."); return true;
        }
        if (args.length == 1 && args[0].equalsIgnoreCase("reload")) { reloadConfig(); sender.sendMessage("Achievements config reloaded."); return true; }
        sender.sendMessage("/kba add <player> <category> <amount>  |  /kba reload"); return true;
    }
    @EventHandler public void click(InventoryClickEvent e) {
        if (!(e.getView().getTopInventory().getHolder() instanceof AchMenuHolder holder)) return;
        e.setCancelled(true);
        if (!(e.getWhoClicked() instanceof Player p)) return;
        if (e.getClickedInventory() != e.getView().getTopInventory()) return;
        int slot = e.getRawSlot();
        if (holder.category() == null) {
            if (slot == 40 && e.isRightClick()) { claim(p, "playtime", 10); return; }
            for (String id : categoryOrder) {
                if (slot == getConfig().getInt("gui.category_slots."+id, -1)) { openCategory(p,id,0); return; }
            }
        } else {
            if (slot == 49) { openRoot(p); return; }
            if (slot == 45) { openCategory(p,holder.category(),holder.page()-1); return; }
            if (slot == 53) { openCategory(p,holder.category(),holder.page()+1); return; }
            if (!e.isRightClick()) return;
            for (int i=0;i<TIER_SLOTS.length;i++) {
                if (slot != TIER_SLOTS[i]) continue;
                int index = holder.page()*TIER_SLOTS.length+i;
                List<Map<?, ?>> all = tiers(holder.category());
                if (index < all.size()) claim(p,holder.category(),tierIndex(all.get(index)));
                return;
            }
        }
    }
    @EventHandler public void drag(InventoryDragEvent e) {
        if (e.getView().getTopInventory().getHolder() instanceof AchMenuHolder) e.setCancelled(true);
    }
    private void claim(Player p, String categoryId, int number) {
        Map<?, ?> tier = null;
        for (Map<?, ?> t : tiers(categoryId)) if (tierIndex(t) == number) { tier=t; break; }
        if (tier == null) { p.sendMessage(color("&cThat tier isn't configured.")); return; }
        if (claimed(p,categoryId,number)) { p.sendMessage(color("&eAlready claimed.")); return; }
        long requirement = number(tier.get("requirement"));
        if (requirement <= 0 || progress(p,categoryId) < requirement) { p.sendMessage(color("&cAchievement not completed.")); return; }
        Object raw = tier.get("rewards");
        if (!(raw instanceof List<?> rewards) || rewards.isEmpty()) { p.sendMessage(color("&cRewards aren't configured.")); return; }
        List<String> commands = new ArrayList<>();
        for (Object o : rewards) {
            if (!(o instanceof Map<?,?> r)) { p.sendMessage(color("&cInvalid reward configuration.")); return; }
            String command = rewardCommand(p,r);
            if (command == null) { p.sendMessage(color("&cThis reward needs administrator configuration before it can be claimed.")); return; }
            commands.add(command);
        }
        // Mark before dispatching to prevent duplicate claims from rapid clicking.
        setClaimed(p,categoryId,number);
        for (String c : commands) {
            try {
                if (!Bukkit.dispatchCommand(Bukkit.getConsoleSender(), c)) getLogger().warning("Reward command returned false: " + c);
            } catch (Exception ex) { getLogger().severe("Reward command failed: " + c + " / " + ex.getMessage()); }
        }
        p.sendMessage(color("&aClaimed " + display(categoryId) + " Tier " + number + "!"));
        MenuState state = openMenus.get(p.getUniqueId());
        if (state != null) { if (state.category()==null) openRoot(p); else openCategory(p,state.category(),state.page()); }
    }
    private String rewardCommand(Player p, Map<?,?> r) {
        String type, value;
        if (r.containsKey("xp")) { type="xp"; value=String.valueOf(number(r.get("xp"))); }
        else if (r.containsKey("credits")) { type="credits"; value=String.valueOf(number(r.get("credits"))); }
        else if (r.containsKey("money")) { type="money"; value=String.valueOf(number(r.get("money"))); }
        else if (r.containsKey("permission")) {
            if (!"essentials.fly".equals(String.valueOf(r.get("permission")))) return null;
            type="fly_permission"; value="1";
        } else if (r.containsKey("key")) {
            type="key"; value=String.valueOf(number(r.containsKey("amount")?r.get("amount"):1));
        } else if (r.containsKey("special_item")) {
            type="special_item"; value=String.valueOf(number(r.containsKey("amount")?r.get("amount"):1));
        } else return null;
        String template;
        if (type.equals("key")) {
            String id = String.valueOf(r.get("key"));
            if (!List.of("vote","pvp","lucky","royal").contains(id)) return null;
            String actualId = getConfig().getString("crate_keys."+id);
            if (actualId == null || actualId.isBlank()) return null;
            template = getConfig().getString("crate_key_command_template");
            if (template == null) return null;
            template=template.replace("%key_id%",actualId);
        } else if (type.equals("special_item")) {
            template=getConfig().getString("special_item_commands."+r.get("special_item"));
        } else {
            template=getConfig().getString("reward_commands."+type);
        }
        if (template == null || template.isBlank() || template.startsWith("REQUIRES_")) return null;
        return template.replace("%player%",p.getName()).replace("%amount%",value);
    }
    @EventHandler public void onBreak(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (p.getGameMode() != GameMode.SURVIVAL || e.isCancelled()) return;
        String type = e.getBlock().getType().name();
        if (type.endsWith("_ORE") || type.equals("ANCIENT_DEBRIS") || type.equals("STONE") || type.equals("DEEPSLATE") || type.endsWith("_STONE") || type.endsWith("_DEEPSLATE")) addProgress(p,"mining",1);
        if (e.getBlock().getBlockData() instanceof Ageable crop && crop.getAge() == crop.getMaximumAge()) addProgress(p,"farming",1);
    }
    @EventHandler public void onDeath(EntityDeathEvent e) {
        Player killer=e.getEntity().getKiller();
        if (killer==null) return;
        if (e.getEntity() instanceof Player) addProgress(killer,"pvp_kills",1);
        else if (e.getEntity() instanceof Monster || e.getEntity() instanceof Animals) addProgress(killer,"mob_grinding",1);
    }
    @EventHandler public void onFish(PlayerFishEvent e) {
        if (e.getState()==PlayerFishEvent.State.CAUGHT_FISH) addProgress(e.getPlayer(),"fishing",1);
    }
    @EventHandler public void onQuit(PlayerQuitEvent e) { openMenus.remove(e.getPlayer().getUniqueId()); saveData(); }
}
