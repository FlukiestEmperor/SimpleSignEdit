package ca.celticminstrel.signedit;

import java.sql.Connection;
import static java.sql.DatabaseMetaData.sqlStateXOpen;
import static java.sql.DatabaseMetaData.sqlStateSQL;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
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
	Logger logger = Logger.getLogger("Minecraft.SignEdit");
	private BlockListener bl = new SignBlockListener(this);
	private PlayerListener pl = new SignPlayerListener(this);
	private EntityListener el = new SignEntityListener(this);
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
			Configuration config = getConfiguration();
			config.removeProperty("signs");
			for(Location loc : ownership.keySet()) {
				if(loc == null) continue;
				Formatter fmt = new Formatter();
				String locString = fmt.format("%s(%d,%d,%d)", loc.getWorld().getName(),
						loc.getBlockX(), loc.getBlockY(), loc.getBlockZ()).toString();
				config.setProperty("signs." + locString, ownership.get(loc));
			}
			config.save();
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
		if(Option.AUTO_SAVE.get()) getConfiguration().save();
		logger.info("Disabled " + getDescription().getFullName());
	}

	@Override
	public void onEnable() {
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getFullName() + " enabled.");
		getServer().getPluginManager().registerEvent(Type.SIGN_CHANGE, bl, Priority.Normal, this);
		// TODO: Wonder if setting this to Highest would cause problems?
		getServer().getPluginManager().registerEvent(Type.PLAYER_INTERACT, pl, Priority.Highest, this);
		getServer().getPluginManager().registerEvent(Type.PLAYER_CHAT, pl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.ENTITY_DAMAGE, el, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.BLOCK_BREAK, bl, Priority.Normal, this);
		getServer().getPluginManager().registerEvent(Type.BLOCK_PLACE, bl, Priority.Highest, this);
		Configuration config = getConfiguration();
		config.load();
		Option.setConfiguration(config);
		Properties dbOptions = new Properties();
		if(config.getNode("database.options") != null) {
			List<String> keys = config.getKeys("database.options");
			for(String key : keys) dbOptions.setProperty(key, config.getString("database.options." + key));
		}
		String dbUrl = Option.DATABASE.get();
		if(!dbUrl.equalsIgnoreCase("yaml")) {
			try {
				Class.forName(Option.DB_CLASS.get());
				db = DriverManager.getConnection(dbUrl, dbOptions);
				try {
					logger.info("[SimpleSignEdit] Checking for table...");
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
		List<String> keys = config.getKeys("signs");
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
				config.removeProperty("signs");
				config.save();
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

class SignsMap implements Map<Location, String>, Runnable {
	private Map<Location, String> map = new HashMap<Location, String>();
	Queue<Entry<Location, String>> queue = new ArrayDeque<Entry<Location, String>>();
	private SignEdit plugin;
	public volatile boolean done = false;
	private PreparedStatement select, countByKey, countByValue;

	public SignsMap(SignEdit signEdit) {
		plugin = signEdit;
		if(plugin.db == null) return;
		try {
			select = plugin.db.prepareStatement(
				"select owner from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			countByKey = plugin.db.prepareStatement(
				"select count(*) from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			countByValue = plugin.db.prepareStatement(
				"select count(*) from sign_ownership " +
				"where owner = ?"
			);
		} catch(SQLException e) {
			try {
				plugin.db.close();
			} catch(SQLException x) {
				x.printStackTrace();
			}
			plugin.db = null;
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
		PreparedStatement delete, insert;
		try {
			delete = plugin.db.prepareStatement(
				"delete from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			insert = plugin.db.prepareStatement(
				"insert into sign_ownership(world, x, y, z, owner) " +
				"values (?, ?, ?, ?, ?)"
			);
		} catch(SQLException e) {
			try {
				plugin.db.close();
			} catch(SQLException x) {
				x.printStackTrace();
			}
			plugin.db = null;
			e.printStackTrace();
			return;
		}
		try {
			while(!done) {
				while(!queue.isEmpty()) {
					Map.Entry<Location, String> update;
					synchronized(queue) {
						update = queue.poll();
					}
					try {
						delete.setString(1, update.getKey().getWorld().getName());
						delete.setInt(2, update.getKey().getBlockX());
						delete.setInt(3, update.getKey().getBlockY());
						delete.setInt(4, update.getKey().getBlockZ());
						if(delete.execute()) {
							plugin.logger.warning("Unexpected ResultSet...!");
						} else {
							if(delete.getUpdateCount() > 1) {
								plugin.logger.warning("Deleted more than 1 row for some reason!?");
							}
						}
						if(update.getValue() == null) continue;
						insert.setString(1, update.getKey().getWorld().getName());
						insert.setInt(2, update.getKey().getBlockX());
						insert.setInt(3, update.getKey().getBlockY());
						insert.setInt(4, update.getKey().getBlockZ());
						insert.setString(5, update.getValue());
						if(insert.execute()) {
							plugin.logger.warning("Unexpected ResultSet...!");
						} else {
							if(insert.getUpdateCount() > 1) {
								plugin.logger.warning("Inserted more than 1 row for some reason?");
							}
						}
					} catch(SQLException e) {
						plugin.logger.severe("An SQL error has occurred:");
						plugin.logger.severe("--> " + e.getMessage());
						plugin.logger.warning("This may indicate a loss of sign ownership data!");
					}
				}
				synchronized(queue) {
					queue.wait();
				}
			}
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
		try {
			delete.close();
			insert.close();
		} catch(SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public void clear() {
		try {
			plugin.db.createStatement().execute("delete from sign_ownership");
		} catch(SQLException e) {
			e.printStackTrace();
		}
	}

	@Override
	public boolean containsKey(Object key) {
		if(map.containsKey(key)) return true;
		if(!(key instanceof Location)) return false;
		Location loc = (Location) key;
		try {
			countByKey.setString(1, loc.getWorld().getName());
			countByKey.setInt(2, loc.getBlockX());
			countByKey.setInt(3, loc.getBlockY());
			countByKey.setInt(4, loc.getBlockZ());
			if(countByKey.execute()) {
				ResultSet set = countByKey.getResultSet();
				set.next();
				int count = set.getInt(1);
				return count > 0;
			}
		} catch(SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public boolean containsValue(Object value) {
		if(map.containsValue(value)) return true;
		if(!(value instanceof String)) return false;
		String str = (String) value;
		try {
			countByValue.setString(1, str);
			if(countByValue.execute()) {
				ResultSet set = countByKey.getResultSet();
				set.next();
				int count = set.getInt(1);
				return count > 0;
			}
		} catch(SQLException e) {
			e.printStackTrace();
		}
		return false;
	}

	@Override
	public Set<Entry<Location,String>> entrySet() {
		return map.entrySet();
	}

	@Override
	public String get(Object key) {
		if(map.containsKey(key)) return map.get(key);
		if(!(key instanceof Location)) return null;
		Location loc = (Location) key;
		try {
			select.setString(1, loc.getWorld().getName());
			select.setInt(2, loc.getBlockX());
			select.setInt(3, loc.getBlockY());
			select.setInt(4, loc.getBlockZ());
			if(select.execute()) {
				ResultSet set = select.getResultSet();
				if(set.next()) return set.getString(1);
			}
		} catch(SQLException e) {
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public boolean isEmpty() {
		return map.isEmpty();
	}

	@Override
	public Set<Location> keySet() {
		return map.keySet();
	}

	@Override
	public String put(Location key, String value) {
		synchronized(queue) {
			queue.add(new QueueEntry(key,value));
			queue.notify();
		}
		return map.put(key, value);
	}

	@Override
	public void putAll(Map<? extends Location,? extends String> m) {
		for(Entry<? extends Location,? extends String> entry : map.entrySet())
			put(entry.getKey(), entry.getValue());
	}

	@Override
	public String remove(Object obj) {
		if(!(obj instanceof Location)) return map.remove(obj);
		Location key = (Location) obj;
		synchronized(queue) {
			queue.add(new QueueEntry(key,null));
			queue.notify();
		}
		return map.remove(key);
	}

	@Override
	public int size() {
		return map.size();
	}

	@Override
	public Collection<String> values() {
		return map.values();
	}
	
	private class QueueEntry implements Entry<Location,String> {
		private final Location key;
		private String val;
		
		public QueueEntry(Location k, String v) {
			key = k;
			val = v;
		}

		@Override
		public Location getKey() {
			return key;
		}

		@Override
		public String getValue() {
			return val;
		}

		@Override
		public String setValue(String value) {
			String ret = val;
			val = value;
			return ret;
		}
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
