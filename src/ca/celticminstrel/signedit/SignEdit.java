package ca.celticminstrel.signedit;

import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.bukkit.ChatColor;

public class SignEdit extends JavaPlugin {
	private static Pattern locpat = Pattern.compile("([^(]+)\\((-?\\d+),(-?\\d+),(-?\\d+)\\)");
	private Logger logger = Logger.getLogger("Minecraft.SignEdit");
	private BlockListener bl = new SignBlockListener(this);
	private PlayerListener pl = new SignPlayerListener(this);
	private EntityListener el = new SignEntityListener(this);
	HashMap<Location,SignUpdater> updates = new HashMap<Location,SignUpdater>();
	HashMap<Location,String> ownership = new HashMap<Location,String>();
	HashMap<String,Location> ownerSetting = new HashMap<String,Location>();
	
	/**
	 * Public API function to set the owner of a sign. It's recommended that plugins which handle
	 * right-clicks on signs set the owner of their signs to no-one.
	 * @param whichSign The location of the sign whose ownership you are changing.
	 * @param owner The name of the new owner. Use "#" for no-one and "*" for everyone. Null is also no-one.
	 * @return Whether a sign's owner was actually changed. Will return false if there is no sign at the location
	 * or if the sign already has the requested owner.
	 */
	public boolean setSignOwner(Location whichSign, String owner) {
		Material sign = whichSign.getWorld().getBlockAt(whichSign).getType();
		if(sign != Material.SIGN_POST && sign != Material.WALL_SIGN) return false;
		if(owner == null) owner = "#";
		String oldOwner = ownership.get(owner);
		if(oldOwner == null) oldOwner = "#";
		if(owner.equalsIgnoreCase(oldOwner)) return false;
		if(owner.equals("#")) ownership.remove(whichSign);
		else ownership.put(whichSign, owner);
		return true;
	}

	/**
	 * Public API function to set the owner of a sign. It's recommended that plugins which handle
	 * right-clicks on signs set the owner of their signs to no-one.
	 * @param whichSign The sign whose ownership you are changing.
	 * @param owner The name of the new owner. Use "#" for no-one and "*" for everyone. Null is also no-one.
	 * @return Whether a sign's owner was actually changed. Will return false if there is no sign at the location
	 * or if the sign already has the requested owner.
	 */
	public boolean setSignOwner(Block whichSign, String owner) {
		return setSignOwner(whichSign.getLocation(), owner);
	}
	
	/**
	 * Public API function to get the owner of a sign.
	 * @param whichSign The location of the sign whose ownership you are checking.
	 * @return The sign's current owner; "#" means no-one, "*" means everyone.
	 */
	public String getSignOwner(Location whichSign) {
		if(ownership.containsKey(whichSign))
			return ownership.get(whichSign);
		else return "#";
			
	}

	/**
	 * Public API function to get the owner of a sign.
	 * @param whichSign The sign whose ownership you are checking.
	 * @return The sign's current owner; "#" means no-one, "*" means everyone.
	 */
	public String getSignOwner(Block whichSign) {
		return getSignOwner(whichSign.getLocation());
	}

	boolean hasPermission(Player who) {
		return who.hasPermission("simplesignedit.edit");
	}

	boolean canStackSigns(Material clicked, BlockFace face) {
		if(clicked != Material.SIGN_POST) return false;
		if(face != BlockFace.UP) return false;
		return getConfiguration().getBoolean("allow-stacking", true);
	}

	boolean canSetOwner(Player who) {
		return who.hasPermission("simplesignedit.setowner");
	}
	
	boolean isOwnerOf(Player player, Location location) {
		String owner = ownership.get(location);
		boolean canEditAll = player.hasPermission("simplesignedit.edit.all");
		if(owner == null) return canEditAll;
		if(owner.equalsIgnoreCase(player.getName())) return true;
		if(owner.equals("*")) return true;
		return canEditAll;
	}
	
	private boolean hasColour(Player who, ChatColor clr) {
		String colourName = clr.name().toLowerCase().replace("_", "");
		return who.hasPermission("simplesignedit.colour." + colourName);
	}

	@Override
	public void onDisable() {
		//logger.info(ownership.toString());
		Configuration config = getConfiguration();
		@SuppressWarnings("rawtypes")
		HashMap eraser = new HashMap();
		config.setProperty("signs", eraser);
		for(Location loc : ownership.keySet()) {
			Formatter fmt = new Formatter();
			String locString = fmt.format("%s(%d,%d,%d)", loc.getWorld().getName(),
					loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).toString();
			config.setProperty("signs." + locString, ownership.get(loc));
		}
		config.save();
		logger.info("Disabled " + getDescription().getFullName());
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getFullName() + " enabled.");
		getServer().getPluginManager().registerEvent(Type.SIGN_CHANGE, bl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.PLAYER_INTERACT, pl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.PLAYER_CHAT, pl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.ENTITY_DAMAGE, el, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.BLOCK_BREAK, bl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.BLOCK_PLACE, bl, Priority.Normal, this);
		Configuration config = getConfiguration();
		config.load();
		List<String> keys = config.getKeys("signs");
		if(keys != null) {
			for(String loc : keys) {
				Matcher m = locpat.matcher(loc);
				if(!m.matches()) {
					logger.warning("Invalid key in config: " + loc);
					continue;
				}
				String world = m.group(1);
				String x = m.group(2), y = m.group(3), z = m.group(4);
				Location key = new Location(getServer().getWorld(world), Double.valueOf(x), Double.valueOf(y), Double.valueOf(z));
				ownership.put(key, config.getString("signs." + loc));
			}
		}
		
		HashMap<String,Boolean> colours = new HashMap<String,Boolean>();
		PluginManager pm = getServer().getPluginManager();
		Permission perm;
		for(ChatColor colour : ChatColor.values()) {
			String colourName = colour.name().toLowerCase().replace("_", "");
			perm = new Permission(
				"simplesignedit.colour." + colourName,
				"Allows you to use the colour " + colourName + " on signs."
			);
			pm.addPermission(perm);
			HashMap<String,Boolean> child = new HashMap<String,Boolean>();
			child.put("simplesignedit.colour." + colourName, true);
			perm = new Permission(
				"simplesignedit.color." + colourName,
				"Allows you to use the colour " + colourName + " on signs.",
				child
			);
			pm.addPermission(perm);
			colours.put("simplesignedit.colour." + colourName, true);
		}
		perm = new Permission(
			"simplesignedit.colour.*",
			"Allows you to use any colour on a sign.",
			PermissionDefault.OP,
			colours
		);
		pm.addPermission(perm);
	}
	
	void sendSignUpdate(Block signBlock) {
		// This line updates the sign for the user.
		final Sign sign = (Sign) signBlock.getState();
		getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
			@Override
			public void run() {
				sign.update(true);
			}
		});
	}
	
	String parseColour(String line, Player setter) {
		String regex = "&(?<!&&)(?=%s)";
		Formatter fmt;
		for(ChatColor clr : ChatColor.values()) {
			if(!hasColour(setter, clr)) continue;
			String code = Integer.toHexString(clr.getCode());
			fmt = new Formatter();
			line = line.replaceAll(fmt.format(regex, code).toString(), "\u00A7");
		}
		return line.replace("&&", "&");
		//return line.replaceAll(regex, "\u00A7");
	}

	String getOwnerOf(Block block) {
		String owner = ownership.get(block.getLocation());
		if(owner == null) return "no-one";
		if(owner.equals("*")) return "everyone";
		return owner;
	}
}
