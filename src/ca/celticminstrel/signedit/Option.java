package ca.celticminstrel.signedit;

import org.bukkit.util.config.Configuration;

public abstract class Option {
	public static OptionBoolean ALLOW_STACKING = new OptionBoolean("allow-stacking", true);
	public static OptionBoolean BREAK_PROTECT = new OptionBoolean("break-protect", false);
	public static OptionString SNEAKING = new OptionString("sneaking", "both");
	public static OptionInteger VIEW_OWNER = new OptionInteger("view-owner", 280);
	public static OptionInteger SET_OWNER = new OptionInteger("set-owner", 288);
	public static OptionBoolean ORPHANED_BREAKABLE = new OptionBoolean("orphaned-breakable", false);
	public static OptionString DATABASE = new OptionString("database.url","jdbc:sqlite:plugins/SimpleSignEdit/signs.db");
	public static OptionString DB_CLASS = new OptionString("database.class","org.sqlite.JDBC");
	public static OptionBoolean AUTO_SAVE = new OptionBoolean("auto-save",true);
	protected String node;
	protected Object def;
	protected static Configuration config;
	
	@SuppressWarnings("hiding")
	protected Option(String node, Object def) {
		this.node = node;
		this.def = def;
	}
	
	public abstract Object get();
	
	public void set(Object value) {
		config.setProperty(node, value);
	}
	
	public void reset() {
		set(def);
	}
	
	public static void setConfiguration(Configuration c) {
		config = c;
	}
	
	public static class OptionBoolean extends Option {
		@SuppressWarnings("hiding") OptionBoolean(String node, boolean def) {
			super(node, def);
		}

		@Override
		public Boolean get() {
			return config.getBoolean(node, (Boolean) def);
		}
	}

	public static class OptionString extends Option {
		@SuppressWarnings("hiding") OptionString(String node, String def) {
			super(node, def);
		}

		@Override
		public String get() {
			return config.getString(node, (String) def);
		}
	}

	public static class OptionInteger extends Option {
		@SuppressWarnings("hiding") OptionInteger(String node, int def) {
			super(node, def);
		}

		@Override
		public Integer get() {
			return config.getInt(node, (Integer) def);
		}
	}
//
//	public static class OptionDouble extends Option {
//		@SuppressWarnings("hiding") OptionDouble(String node, double def) {
//			super(node, def);
//		}
//
//		@Override
//		public Double get() {
//			return config.getDouble(node, (Double) def);
//		}
//	}
//
//	public static class OptionStringList extends Option {
//		@SuppressWarnings("hiding") OptionStringList(String node, List<String> def) {
//			super(node, def);
//		}
//
//		@Override@SuppressWarnings("unchecked")
//		public List<String> get() {
//			return config.getStringList(node, (List<String>) def);
//		}
//	}
//
//	public static class OptionIntegerList extends Option {
//		@SuppressWarnings("hiding") OptionIntegerList(String node, List<Integer> def) {
//			super(node, def);
//		}
//
//		@Override@SuppressWarnings("unchecked")
//		public List<Integer> get() {
//			return config.getIntList(node, (List<Integer>) def);
//		}
//	}
}
