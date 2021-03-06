package ca.celticminstrel.signedit;

import static java.sql.DatabaseMetaData.sqlStateSQL;
import static java.sql.DatabaseMetaData.sqlStateXOpen;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayDeque;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;
import java.util.Queue;
import java.util.Set;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.logging.Logger;

import org.bukkit.Location;
import org.bukkit.configuration.Configuration;
import org.bukkit.configuration.ConfigurationSection;

class SignsMap implements Map<Location, String>, Runnable {
	private Map<Location, String> map = new HashMap<Location, String>();
	private Queue<Entry<Location, String>> queue = new ArrayDeque<Entry<Location, String>>();
	private SignEdit plugin;
	private Connection db;
	private Thread dbUpdater;
	private volatile boolean done = false;
	private PreparedStatement select, countByKey, countByValue, delete, insert;
	
	public static Future<SignsMap> setup(Logger logger, Configuration config, SignEdit instance) {
		String dbUrl = Options.DATABASE.get();
		final DatabaseFuture db = new DatabaseFuture();
		if(!dbUrl.equalsIgnoreCase("none")) {
			Properties dbOptions = new Properties();
			ConfigurationSection dboptSection = config.getConfigurationSection("database.options");
			if(dboptSection != null) {
				Set<String> keys = dboptSection.getKeys(false);
				for(String key : keys) dbOptions.setProperty(key, config.getString("database.options." + key));
			}
			db.start(new DatabaseLoader(db, logger, dbUrl, dbOptions, instance));
		}
		return db;
	}
	
	private static class DatabaseFuture implements Future<SignsMap> {
		SignsMap map = null;
		Thread thread = null;
		@Override
		public boolean cancel(boolean mayInterruptIfRunning) {
			return false;
		}

		@Override
		public SignsMap get() throws InterruptedException, ExecutionException {
			thread.join();
			return map;
		}

		@Override
		public SignsMap get(long time, TimeUnit unit) throws InterruptedException, ExecutionException, TimeoutException {
			unit.timedJoin(thread, time);
			return map;
		}

		@Override
		public boolean isCancelled() {
			if(thread == null) return true;
			return false;
		}

		@Override
		public boolean isDone() {
			if(thread == null) return true;
			return !thread.isAlive();
		}
		
		public void set(SignsMap to) {
			map = to;
		}
		
		public void start(DatabaseLoader loader) {
			thread = new Thread(loader);
			thread.setPriority(Thread.MAX_PRIORITY);
			thread.start();
		}
	}
	
	private static class DatabaseLoader implements Runnable {
		private DatabaseFuture ret;
		private Logger logger;
		private String dbUrl;
		private Properties dbOptions;
		private SignEdit instance;
		public DatabaseLoader(DatabaseFuture dbf, Logger log, String url, Properties options, SignEdit plugin) {
			ret = dbf;
			logger = log;
			dbUrl = url;
			dbOptions = options;
			instance = plugin;
		}
		@Override
		public void run() {
			Connection db;
			String dbclass = Options.DB_CLASS.get();
			try {
				Class.forName(dbclass);
				logger.info("Loading database driver (" + dbclass + ")");
				db = DriverManager.getConnection(dbUrl, dbOptions);
				logger.info("Checking for table...");
				ResultSet tables = db.getMetaData().getTables(db.getCatalog(), null, "sign_ownership", null);
				if(!tables.next()) {
					try {
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
						logger.info("Table created successfully!");
					} catch(SQLException e) {
						int type = db.getMetaData().getSQLStateType();
						boolean error = true;
						String message = "";
						switch(type) {
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
						else if(e.getSQLState().equals("S0001")) error = false;
						if(e.getMessage().toLowerCase().contains("table") && e.getMessage().toLowerCase().contains("exist"))
							error = false;
						if(error) {
							logger.warning(e.getMessage() + "  [" + message + "]");
							e.printStackTrace();
						} else logger.info("The table could not be created; most likely this is because it" +
								"already exists, but if you experience loss of data, report the following" +
								"error code: [" + message + "]");
					}
				} else logger.info("Table found!");
			} catch(SQLException e) {
				db = null;
				logger.info("Failed to load database from '" + dbUrl + "'!");
				e.printStackTrace();
			} catch(ClassNotFoundException e) {
				db = null;
				logger.info("Could not load class '" + dbclass + "' for the database!");
				e.printStackTrace();
			}
			if(db != null) ret.set(new SignsMap(instance, db));
		}
	}

	public SignsMap(SignEdit signEdit, Connection conn) {
		db = conn;
		plugin = signEdit;
		plugin.logger.info("Finishing database setup...");
		initStatements();
		plugin.logger.info("Starting database listener thread...");
		dbUpdater = new Thread(this);
		dbUpdater.start();
	}

	private void initStatements() {
		try {
			select = db.prepareStatement(
				"select owner from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			countByKey = db.prepareStatement(
				"select count(*) from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			countByValue = db.prepareStatement(
				"select count(*) from sign_ownership " +
				"where owner = ?"
			);
			delete = db.prepareStatement(
				"delete from sign_ownership " +
				"where world = ? and x = ? and y = ? and z = ?"
			);
			insert = db.prepareStatement(
				"insert into sign_ownership(world, x, y, z, owner) " +
				"values (?, ?, ?, ?, ?)"
			);
		} catch(SQLException e) {
			try {
				db.close();
			} catch(SQLException x) {
				x.printStackTrace();
			}
			db = null;
			e.printStackTrace();
		}
	}

	@Override
	public void run() {
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
						initStatements(); // In case the issue can be resolved by recreating the statements
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
			db.createStatement().execute("delete from sign_ownership");
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
				ResultSet set = countByValue.getResultSet();
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

	public void close() {
		try {
			db.close();
		} catch(SQLException e) {
			e.printStackTrace();
		}
		db = null;
		done = true;
		try {
			synchronized(queue) {
				queue.notify();
			}
			dbUpdater.join();
		} catch(InterruptedException e) {
			e.printStackTrace();
		}
		dbUpdater = null;
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