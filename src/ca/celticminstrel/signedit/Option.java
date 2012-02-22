package ca.celticminstrel.signedit;

import org.bukkit.configuration.Configuration;

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
	public static OptionString DEFAULT_OWNER = new OptionString("default-owner", "placer");
	public static OptionBoolean USE_LWC = new OptionBoolean("use-lwc", true);
	protected String node;
	protected Object def;
	private static Configuration config;
	
	@SuppressWarnings("hiding")
	protected Option(String node, Object def) {
		this.node = node;
		this.def = def;
	}
	
	public abstract Object get();
	
	public void set(Object value) {
		config.set(node, value);
	}
	
	public void reset() {
		set(def);
	}
	
	public static void setConfiguration(Configuration c) {
		config = c;
	}
	
	protected Configuration setDefault() {
		if(config.get(node) == null) config.set(node, def);
		return config;
	}
}

class OptionBoolean extends Option {
	@SuppressWarnings("hiding") OptionBoolean(String node, boolean def) {
		super(node, def);
	}

	@Override
	public Boolean get() {
		return setDefault().getBoolean(node, (Boolean) def);
	}
}

class OptionString extends Option {
	@SuppressWarnings("hiding") OptionString(String node, String def) {
		super(node, def);
	}

	@Override
	public String get() {
		return setDefault().getString(node, (String) def);
	}
}

class OptionInteger extends Option {
	@SuppressWarnings("hiding") OptionInteger(String node, int def) {
		super(node, def);
	}

	@Override
	public Integer get() {
		return setDefault().getInt(node, (Integer) def);
	}
}
