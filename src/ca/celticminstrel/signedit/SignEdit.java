package ca.celticminstrel.signedit;

import java.sql.Connection;
import static java.sql.DatabaseMetaData.sqlStateXOpen;
import static java.sql.DatabaseMetaData.sqlStateSQL;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Formatter;
import java.util.HashMap;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

public class SignEdit extends JavaPlugin {
	private static Pattern locpat = Pattern.compile("([^(]+)\\((-?\\d+),(-?\\d+),(-?\\d+)\\)");
	Logger logger = Logger.getLogger("Minecraft.SignEdit");
	private Listener signL = new SignListener(this);
	private Listener ownerL = new OwnerListener(this);
	HashMap<Location,SignUpdater> updates = new HashMap<Location,SignUpdater>();
	private SignsMap ownership;
	HashMap<String,Location> ownerSetting = new HashMap<String,Location>();
	Connection db;
	private Thread dbUpdater;
	
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
		if(owner.equals("#")) ownership.remove(whichSign);
		else ownership.put(whichSign, owner);
		if(owner.equalsIgnoreCase(oldOwner)) return false;
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
	
	/**
	 * Convenience method to check if a sign has an owner
	 * @param whichSign The location of the sign whose owned status you are checking.
	 * @return True if the sign is owned by someone (or everyone), false if it is owned by no-one.
	 */
	public boolean isSignOwned(Location whichSign) {
		return !getSignOwner(whichSign).equals("#");
	}
	
	/**
	 * Convenience method to check if a sign has an owner
	 * @param whichSign The sign whose owned status you are checking.
	 * @return True if the sign is owned by someone (or everyone), false if it is owned by no-one.
	 */
	public boolean isSignOwned(Block whichSign) {
		return !getSignOwner(whichSign).equals("#");
	}

	boolean hasPermission(Player who) {
		return who.hasPermission("simplesignedit.edit");
	}

	boolean canStackSigns(Material clicked, BlockFace face) {
		if(clicked != Material.SIGN_POST) return false;
		if(face != BlockFace.UP) return false;
		return Option.ALLOW_STACKING.get();
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
		if(db == null) {
			logger.info("[SimpleSignEdit] Saving ownership to config.yml...");
			FileConfiguration config = getConfig();
			config.set("signs", null); // TODO: Is this really removal?
			for(Location loc : ownership.keySet()) {
				if(loc == null) continue;
				Formatter fmt = new Formatter();
				String locString = fmt.format("%s(%d,%d,%d)", loc.getWorld().getName(),
						loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).toString();
				config.set("signs." + locString, ownership.get(loc));
			}
			saveConfig();
		} else {
			try {
				db.close();
			} catch(SQLException e) {
				e.printStackTrace();
			}
			ownership.done = true;
			try {
				synchronized(ownership.queue) {
					ownership.queue.notify();
				}
				dbUpdater.join();
			} catch(InterruptedException e) {
				e.printStackTrace();
			}
		}
		if(Option.AUTO_SAVE.get()) saveConfig();
		logger.info("Disabled " + getDescription().getFullName());
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getFullName() + " enabled.");
		getServer().getPluginManager().registerEvents(signL, this);
		getServer().getPluginManager().registerEvents(ownerL, this);
		FileConfiguration config = getConfig();
		Option.setConfiguration(config);
		Properties dbOptions = new Properties();
		ConfigurationSection dboptSection = config.getConfigurationSection("database.options");
		if(dboptSection != null) {
			Set<String> keys = dboptSection.getKeys(false);
			for(String key : keys) dbOptions.setProperty(key, config.getString("database.options." + key));
		}
		String dbUrl = Option.DATABASE.get();
		if(!dbUrl.equalsIgnoreCase("yaml")) {
			try {
				Class.forName(Option.DB_CLASS.get());
				db = DriverManager.getConnection(dbUrl, dbOptions);
				try {
					logger.info("[SimpleSignEdit] Checking for table...");
					// TODO: Apparently you can add /* if not exists */ to avoid an error on some drivers
					// How portable is this?
					db.createStatement().execute(
						"create table sign_ownership (" +
							"world varchar(30) not null, " +
							"x integer not null, " +
							"y integer not null, " +
							"z integer not null, " +
							"owner varchar(30) not null, " +
							"primary key(world, x, y, z) " +
						")"
					);
					logger.info("[SimpleSignEdit] Table created successfully!");
				} catch(SQLException e) {
					int type = db.getMetaData().getSQLStateType();
					boolean error = true;
					String message = "";
					switch(type) {
					// TODO: MSSQL apparently uses S0001?
					case sqlStateXOpen:
						message = "XOpen SQLState: " + e.getSQLState();
						break;
					case sqlStateSQL:
						message = "SQL:2003 SQLState: " + e.getSQLState();
						break;
					default:
						message = "Unknown SQLState " + type + ": " + e.getSQLState();
					}
					if(e.getSQLState() == null);
					else if(e.getSQLState().equals("42S01")) error = false;
					else if(e.getSQLState().equals("42P07")) error = false;
					if(e.getMessage().toLowerCase().contains("table") && e.getMessage().toLowerCase().contains("exist"))
						error = false;
					if(error) {
						logger.warning(e.getMessage() + "  [" + message + "]");
						e.printStackTrace();
					} else logger.info("[SimpleSignEdit] Table found! (Error code was " + message +
						"; feel free to post this line on the forum as it may help me improve the plugin; however," +
						" this is not a bug)");
				}
			} catch(SQLException e) {
				db = null;
				dbUpdater = null;
				logger.info("Failed to load database from '" + dbUrl + "'!");
				e.printStackTrace();
			} catch(ClassNotFoundException e) {
				db = null;
				dbUpdater = null;
				logger.info("Could not load class '" + Option.DB_CLASS.get() + "' for the database!");
				e.printStackTrace();
			}
		} else db = null;
		ownership = new SignsMap(this);
		if(db != null) {
			dbUpdater = new Thread(ownership);
			dbUpdater.start();
		}
		// Compatibility with past versions
		ConfigurationSection signs = config.getConfigurationSection("signs");
		Set<String> keys = signs == null ? null : signs.getKeys(false);
		if(keys != null && !keys.isEmpty()) {
			if(db != null)
				logger.info("[SimpleSignEdit] Converting your old sign ownerships from the config format to the database format...");
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
			if(db != null) {
				config.set("signs",null); // TODO: Is this a true removal?
				saveConfig();
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
		String regex = "&(?<!&&)(?=%c)";
		Formatter fmt;
		for(ChatColor clr : ChatColor.values()) {
			if(!hasColour(setter, clr)) continue;
			char code = clr.getChar();
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

//A sort of combination between a map and a queue.
//It allows multiple keys, but returns them in a FIFO order.
//class UpdaterMap extends AbstractMap<Location,SignUpdater> {
//	private Map<Location,Queue<SignUpdater>> map = new HashMap<Location,Queue<SignUpdater>>();
//	private Map<Location,SignUpdater> cache = new HashMap<Location,SignUpdater>();
//	
//	@Override
//	public void clear() {
//		map.clear();
//		cache.clear();
//	}
//	
//	@Override
//	public boolean containsKey(Object key) {
//		return map.containsKey(key);
//	}
//	
//	@Override
//	public boolean containsValue(Object value) {
//		if(cache.containsValue(value)) return true;
//		for(Queue<SignUpdater> val : map.values())
//			if(val.contains(value)) return true;
//		return false;
//	}
//	
//	@Override
//	public Set<Map.Entry<Location,SignUpdater>> entrySet() {
//		return cache.entrySet();
//	}
//	
//	@Override
//	public SignUpdater get(Object key) {
//		if(cache.containsKey(key)) return cache.get(key);
//		if(map.containsKey(key)) {
//			SignUpdater updater = map.get(key).peek();
//			cache.put((Location)key, updater);
//			return updater;
//		}
//		return null;
//	}
//	
//	@Override
//	public boolean isEmpty() {
//		return map.isEmpty();
//	}
//	
//	@Override
//	public Set<Location> keySet() {
//		return cache.keySet();
//	}
//	
//	@Override
//	public SignUpdater put(Location key, SignUpdater value) {
//		if(!map.containsKey(key))
//			map.put(key, new LinkedList<SignUpdater>());
//		map.get(key).add(value);
//		return null;
//	}
//	
//	@Override
//	public SignUpdater remove(Object key) {
//		if(!map.containsKey(key)) return cache.remove(key);
//		Queue<SignUpdater> queue = map.get(key);
//		SignUpdater updater = queue.poll();
//		cache.put((Location)key, queue.peek());
//		if(queue.isEmpty()) map.remove(key);
//		return updater;
//	}
//	
//	@Override
//	public int size() {
//		return map.size();
//	}
//	
//	@Override
//	public Collection<SignUpdater> values() {
//		Collection<SignUpdater> values = new LinkedList<SignUpdater>();
//		for(Location loc : map.keySet())
//			values.addAll(map.get(loc));
//		return values;
//	}
//}
