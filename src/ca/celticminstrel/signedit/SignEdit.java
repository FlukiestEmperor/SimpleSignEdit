package ca.celticminstrel.signedit;

import java.util.Formatter;
import java.util.HashMap;
import java.util.logging.Logger;

import com.griefcraft.lwc.LWC;
import com.griefcraft.lwc.LWCPlugin;
import com.griefcraft.model.Protection;
import com.griefcraft.model.Protection.Type;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.permissions.Permission;
import org.bukkit.permissions.PermissionDefault;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.ChatColor;

public class SignEdit extends JavaPlugin implements SignEditAPI {
	public static final String ME = "@";
	public static final String PUBLIC = "*";
	public static final String NO_OWNER = "#";
	Logger logger;
	private Listener signL = new SignListener(this);
	private Listener ownerL = new OwnerListener(this);
	private LWC lwc;
	HashMap<Location,SignUpdater> updates = new HashMap<Location,SignUpdater>();
	private SignsMap ownership;
	HashMap<String,Location> ownerSetting = new HashMap<String,Location>();
	
	@Override
	public boolean setSignOwner(Location whichSign, String owner) {
		if(ownership == null && lwc == null) return false;
		Material sign = whichSign.getWorld().getBlockAt(whichSign).getType();
		if(sign != Material.SIGN_POST && sign != Material.WALL_SIGN) {
			ownership.remove(whichSign);
			return false;
		}
		String oldOwner = getSignOwner(whichSign);
		if(oldOwner == null) oldOwner = NO_OWNER;
		if(lwc != null) {
			if(owner.equals(PUBLIC)) lwcSetPublic(whichSign);
			else lwcSetOwner(whichSign, owner);
		}
		if(owner == null) owner = NO_OWNER;
		if(ownership != null) {
			if(owner.equals(NO_OWNER)) ownership.remove(whichSign);
			else ownership.put(whichSign, owner);
		}
		if(owner.equalsIgnoreCase(oldOwner)) return false;
		return true;
	}
	
	@Override
	public boolean setSignOwner(Block whichSign, String owner) {
		return setSignOwner(whichSign.getLocation(), owner);
	}
	
	@Override
	public String getSignOwner(Location whichSign) {
		if(ownership == null && lwc == null) return PUBLIC;
		String owner = null;
		if(lwc != null) owner = lwcGetOwner(whichSign);
		if(owner != null) return owner;
		if(ownership == null) return PUBLIC;
		if(ownership.containsKey(whichSign))
			return ownership.get(whichSign);
		else return NO_OWNER;
	}
	
	@Override
	public String getSignOwner(Block whichSign) {
		return getSignOwner(whichSign.getLocation());
	}
	
	@Override
	public boolean isSignOwned(Location whichSign) {
		return !getSignOwner(whichSign).equals(NO_OWNER);
	}
	
	@Override
	public boolean isSignOwned(Block whichSign) {
		return !getSignOwner(whichSign).equals(NO_OWNER);
	}

	private void lwcSetPublic(Location whichSign) {
		Protection prot = lwc.findProtection(whichSign.getBlock());
		if(prot != null) prot.remove();
	}

	private void lwcSetOwner(Location whichSign, String owner) {
		Protection prot = lwc.findProtection(whichSign.getBlock());
		if(prot == null) {
			World world = whichSign.getWorld();
			int blockId = world.getBlockTypeIdAt(whichSign);
			int x = whichSign.getBlockX();
			int y = whichSign.getBlockY();
			int z = whichSign.getBlockZ();
			// create the protection
			lwc.getPhysicalDatabase().registerProtection(blockId, Type.PRIVATE, world.getName(), owner, "", x, y, z);
		} else prot.setOwner(owner.equals(NO_OWNER) ? "" : owner);
	}
	
	private String lwcGetOwner(Location whichSign) {
		Protection prot = lwc.findProtection(whichSign.getBlock());
		if(prot != null) {
			String owner = prot.getOwner();
			if(owner.isEmpty()) owner = NO_OWNER;
			String myOwner = ownership.get(whichSign);
			if(myOwner == null) myOwner = NO_OWNER;
			if(!myOwner.equals(owner)) {
				if(owner.equals(NO_OWNER)) ownership.remove(whichSign);
				else ownership.put(whichSign, owner);
			}
			return owner;
		}
		return null;
	}

	boolean hasPermission(Player who) {
		return who.hasPermission("simplesignedit.edit");
	}

	boolean canStackSigns(Material clicked, BlockFace face) {
		if(clicked != Material.SIGN_POST) return false;
		if(face != BlockFace.UP) return false;
		return Options.ALLOW_STACKING.get();
	}

	boolean canSetOwner(Player who) {
		return who.hasPermission("simplesignedit.setowner");
	}
	
	boolean isOwnerOf(Player player, Location location) {
		String owner = getSignOwner(location);
		boolean canEditAll = player.hasPermission("simplesignedit.edit.all");
		if(owner == null) return canEditAll;
		if(owner.equalsIgnoreCase(player.getName())) return true;
		if(owner.equals(PUBLIC)) return true;
		return canEditAll;
	}
	
	private boolean hasColour(Player who, ChatColor clr) {
		String colourName = clr.name().toLowerCase().replace("_", "");
		return who.hasPermission("simplesignedit.colour." + colourName);
	}

	@Override
	public void onDisable() {
		ownership.close();
		if(Options.AUTO_SAVE.get()) saveConfig();
		logger.info("Disabled " + getDescription().getFullName());
	}

	@Override
	public void onEnable() {
		logger = getLogger();
		getServer().getPluginManager().registerEvents(signL, this);
		getServer().getPluginManager().registerEvents(ownerL, this);
		FileConfiguration config = getConfig();
		Option.setConfiguration(config);
		saveConfig();
		if(Options.USE_LWC.get()) {
			Plugin lwcPlugin = getServer().getPluginManager().getPlugin("LWC");
			if(lwcPlugin != null) {
			    lwc = ((LWCPlugin) lwcPlugin).getLWC();
				logger.info("LWC support enabled!");
			}
		}
		ownership = SignsMap.setup(logger, config, this);
		
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
		PluginDescriptionFile pdfFile = this.getDescription();
		logger.info(pdfFile.getFullName() + " enabled.");
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
		if(owner.equals(PUBLIC)) return "everyone";
		return owner;
	}
}
