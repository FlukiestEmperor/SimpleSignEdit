package ca.celticminstrel.signedit;

public final class Options {
	public static Option<Boolean> ALLOW_STACKING = new OptionBoolean("allow-stacking", true);
	public static Option<Boolean> BREAK_PROTECT = new OptionBoolean("break-protect", false);
	public static Option<String> SNEAKING = new OptionString("sneaking", "both");
	public static Option<Integer> VIEW_OWNER = new OptionInteger("view-owner", 280);
	public static Option<Integer> SET_OWNER = new OptionInteger("set-owner", 288);
	public static Option<Boolean> ORPHANED_BREAKABLE = new OptionBoolean("orphaned-breakable", false);
	public static Option<String> DATABASE = new OptionString("database.url","jdbc:sqlite:plugins/SimpleSignEdit/signs.db");
	public static Option<String> DB_CLASS = new OptionString("database.class","org.sqlite.JDBC");
	public static Option<Boolean> AUTO_SAVE = new OptionBoolean("auto-save",true);
	public static Option<String> DEFAULT_OWNER = new OptionString("default-owner", "placer");
	public static Option<Boolean> USE_LWC = new OptionBoolean("use-lwc", true);
	private Options() {}
}
