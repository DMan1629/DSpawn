package me.DMan16.DSpawn;

import net.md_5.bungee.api.ChatColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.*;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.ItemSpawnEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.world.WorldLoadEvent;
import org.bukkit.event.world.WorldUnloadEvent;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.jetbrains.annotations.NotNull;

@SuppressWarnings({"ConstantConditions","deprecation"})
public class DSpawnMain extends JavaPlugin implements Listener,CommandExecutor {
	final String pluginNameColors = "&c&lD&b&lSpawn";
	final int version = Integer.parseInt(Bukkit.getServer().getVersion().split("\\(MC:")[1].split("\\)")[0].trim().split(" ")[0].trim().split("\\.")[1]);
	final boolean AbstractArrowExists = checkClass("org.bukkit.entity.AbstractArrow",false);
	final boolean TridentExists = checkClass("org.bukkit.entity.Trident",false);
	final boolean TippedArrowExistsNotDeprecated = checkClass("org.bukkit.entity.TippedArrow",true);
	
	public void onEnable() {
		saveDefaultConfig();
		getServer().getPluginManager().registerEvents(this,this);
		Bukkit.getWorlds().forEach(world -> onWorldLoad(new WorldLoadEvent(world)));
		getCommand("DSpawn").setExecutor(this);
		chatColorsLogPlugin(pluginNameColors + " &aloaded!");
	}
	
	public void onDisable() {
		Bukkit.getWorlds().forEach(world -> onWorldUnload(new WorldUnloadEvent(world)));
		Bukkit.getScheduler().cancelTasks(this);
		chatColorsLogPlugin(pluginNameColors + " &adisabed");
	}
	
	public boolean onCommand(@NotNull CommandSender sender, @NotNull Command cmd, @NotNull String label, String[] args) {
		this.reloadConfig();
		try {
			sender.sendMessage(chatColors(this.getConfig().getString("command-reload-message")));
		} catch (Exception e) {}
		Bukkit.getWorlds().forEach(world -> onWorldLoad(new WorldLoadEvent(world)));
		return true;
	}
	
	@EventHandler
	public void onWorldLoad(WorldLoadEvent event) {
		World world = event.getWorld();
		if (getConfigBoolean("purge-running.items")) world.getEntitiesByClasses(Item.class).forEach(entity -> initiateDespawn(entity,Type.ITEM));
		if (TridentExists && getConfigBoolean("purge-running.tridents")) world.getEntitiesByClasses(Trident.class).stream().map(trident -> (Trident) trident).filter(trident -> !getConfigBoolean("purge-running.ignore-player-tridents") || !(trident.getShooter() instanceof Player)).forEach(entity -> initiateDespawn(entity,Type.TRIDENT));
		if (!getConfigBoolean("purge-running.arrows")) return;
		if (AbstractArrowExists) world.getEntitiesByClasses(AbstractArrow.class).stream().filter(entity -> !(entity instanceof Trident)).forEach(entity -> initiateDespawn(entity,Type.ARROW));
		else {
			world.getEntitiesByClasses(Arrow.class).forEach(entity -> initiateDespawn(entity,Type.ARROW));
			if (version >= 9) {
				world.getEntitiesByClasses(SpectralArrow.class).forEach(entity -> initiateDespawn(entity,Type.ARROW));
				if (TippedArrowExistsNotDeprecated) world.getEntitiesByClasses(TippedArrow.class).forEach(entity -> initiateDespawn(entity,Type.ARROW));
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true)
	public void onWorldUnload(WorldUnloadEvent event) {
		World world = event.getWorld();
		if (getConfigBoolean("purge-disable.items")) world.getEntitiesByClasses(Item.class).stream().filter(Entity::isValid).forEach(Entity::remove);
		if (TridentExists && getConfigBoolean("purge-disable.tridents")) world.getEntitiesByClasses(Trident.class).stream().map(trident -> (Trident) trident).filter(trident -> !getConfigBoolean("purge-disable.ignore-player-tridents") || !(trident.getShooter() instanceof Player)).filter(Entity::isValid).forEach(Entity::remove);
		if (getConfigBoolean("purge-disable.arrows")) return;
		if (AbstractArrowExists) world.getEntitiesByClasses(AbstractArrow.class).stream().filter(entity -> !(entity instanceof Trident)).filter(Entity::isValid).forEach(Entity::remove);
		else {
			world.getEntitiesByClasses(Arrow.class).stream().filter(Entity::isValid).forEach(Entity::remove);
			if (version >= 9) {
				world.getEntitiesByClasses(SpectralArrow.class).stream().filter(Entity::isValid).forEach(Entity::remove);
				if (TippedArrowExistsNotDeprecated) world.getEntitiesByClasses(TippedArrow.class).stream().filter(Entity::isValid).forEach(Entity::remove);
			}
		}
	}
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onItemSpawn(ItemSpawnEvent event) {
		if (getConfigBoolean("purge-running.items")) initiateDespawn(event.getEntity(),Type.ITEM);
	}
	
	@EventHandler(ignoreCancelled = true, priority = EventPriority.MONITOR)
	public void onProjectileHit(ProjectileHitEvent event) {
		boolean abstractArrow = false;
		Type type = Type.ARROW;
		if (getConfigBoolean("purge-running.arrows"))  {
			if (AbstractArrowExists) abstractArrow = (event.getEntity() instanceof AbstractArrow) && !(event.getEntity() instanceof Trident);
			else abstractArrow = event.getEntity().getClass().getName().endsWith("Arrow");
		}
		if (TridentExists && getConfigBoolean("purge-running.tridents")) if (event.getEntity() instanceof Trident) if (!getConfigBoolean("purge-running.ignore-player-tridents") || !(event.getEntity().getShooter() instanceof Player)) {
			abstractArrow = true;
			type = Type.TRIDENT;
		}
		if (abstractArrow) initiateDespawn(event.getEntity(),type);
	}
	
	void initiateDespawn(@NotNull Entity entity, @NotNull Type type) {
		if (entity == null || type == null) return;
		new BukkitRunnable() {
			public void run() {
				if (entity.isValid()) entity.remove();
			}
		}.runTaskLater(this,Math.max(Bukkit.getServer().spigot().getConfig().getLong(("world-settings.default." + type + "-despawn-rate").toLowerCase()),1));
	}
	
	static boolean checkClass(@NotNull String name, boolean checkDeprecated) {
		try {
			Class<?> clazz = Class.forName(name);
			if (checkDeprecated) return clazz.getAnnotation(Deprecated.class) == null;
			return true;
		} catch (Exception e) {}
		return false;
	}
	
	String chatColors(@NotNull String str) {
		return ChatColor.translateAlternateColorCodes('&',str);
	}
	
	void chatColorsLogPlugin(@NotNull String str) {
		Bukkit.getLogger().info(chatColors("&d[" + pluginNameColors + "&d]&r " + str));
	}
	
	boolean getConfigBoolean(@NotNull String name) {
		return this.getConfig().getBoolean(name);
	}
	
	enum Type {
		ITEM,
		ARROW,
		TRIDENT
	}
}