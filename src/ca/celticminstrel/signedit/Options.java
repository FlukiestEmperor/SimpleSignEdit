package ca.celticminstrel.signedit;

public final class Options {
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
	private Options() {}
}
