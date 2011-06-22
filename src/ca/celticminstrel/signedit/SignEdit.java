package ca.celticminstrel.signedit;

import java.util.Formatter;
import java.util.HashMap;
import java.util.List;
import java.util.logging.Logger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet130UpdateSign;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.util.config.Configuration;
import org.bukkit.ChatColor;

import com.nijiko.permissions.PermissionHandler;
import com.nijikokun.bukkit.Permissions.Permissions;

public class SignEdit extends JavaPlugin {
    protected final class SignUpdater implements Runnable {
        private final Block target;
        private final Block source;
        private String[] lines;
        private Player setter;

        private SignUpdater(Block targ, Block src, Player who) {
            this.target = targ;
            this.source = src;
            this.setter = who;
        }
        
        private SignUpdater setLines(String[] newLines) {
            this.lines = newLines;
            return this;
        }

        public void run() {
            if(target.getType() != Material.WALL_SIGN && target.getType() != Material.SIGN_POST) {
                return;
            }
            if(!hasPermission(setter)) {
                source.setType(Material.AIR);
                setter.sendMessage("Sorry, your sign editing permissions were revoked while you were editing.");
                return;
            }
            Sign targetState = (Sign) target.getState();
            for(int i = 0; i < 4; i++)
                targetState.setLine(i, lines[i]);
            source.setType(Material.AIR);
            for(Player who : setter.getWorld().getPlayers())
                sendSignUpdate(target, who);
        }
    }

    private static Pattern locpat = Pattern.compile("([^(]+)\\((-?\\d+),(-?\\d+),(-?\\d+)\\)");
    private Logger logger = Logger.getLogger("Minecraft.SignEdit");
    private HashMap<Location,SignUpdater> updates = new HashMap<Location,SignUpdater>();
    private HashMap<Location,String> ownership = new HashMap<Location,String>();
    private HashMap<String,Location> ownerSetting = new HashMap<String,Location>();
    private PermissionHandler p = null;
    
    private BlockListener bl = new BlockListener() {
        @Override
        public void onSignChange(SignChangeEvent evt) {
            Location loc = evt.getBlock().getLocation();
            Player setter = evt.getPlayer();
            for(int i = 0; i < 4; i++)
                evt.setLine(i, parseColour(evt.getLine(i), setter));
            if(updates.containsKey(loc)) {
                //logger.info("Editing sign at " + loc);
                updates.get(loc).setLines(evt.getLines()).run();
                updates.remove(loc);
            } else if(!ownership.containsKey(loc)) {
                //logger.info("Placing sign at " + loc);
                ownership.put(loc, setter.getName());
            }
        }
        
        @Override
        public void onBlockBreak(BlockBreakEvent evt) {
            Block block = evt.getBlock();
            if(updates.containsKey(block.getLocation())) {
                //logger.info("Cancelled breaking of an updater sign.");
                evt.setCancelled(true);
            } else if(block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST)
                ownership.remove(block.getLocation());
        }
        
        @Override
        public void onBlockPlace(BlockPlaceEvent evt) {
            Block block = evt.getBlockPlaced();
            if(block.getType() != Material.WALL_SIGN && block.getType() != Material.SIGN_POST) return;
            if(updates.containsKey(block.getLocation())) {
                Sign updater = (Sign) block.getState();
                Sign editing = (Sign) evt.getBlockAgainst().getState();
                int i = 0;
                for(String line : editing.getLines())
                    updater.setLine(i++, line.replace("&","&&").replace('\u00A7', '&'));
                updater.update();
            }
        }
    };
    
    private PlayerListener pl = new PlayerListener() {
        @Override
        public void onPlayerInteract(PlayerInteractEvent evt) {
            if(evt.getAction() != Action.RIGHT_CLICK_BLOCK) return;
            Player player = evt.getPlayer();
            ItemStack itemInHand = player.getItemInHand();
            if(itemInHand == null) return;
            Material holding = itemInHand.getType();
            Block target = evt.getClickedBlock();
            Material clicked = target.getType();
            if(holding == Material.SIGN) {
                if(clicked == Material.WALL_SIGN || clicked == Material.SIGN_POST) {
                	if(canStackSigns(clicked, evt.getBlockFace())) return;
                    Block source = target.getRelative(evt.getBlockFace());
                    if(!hasPermission(player)) {
                        evt.setCancelled(true);
                        player.sendMessage("Sorry, you do not have permission to edit signs.");
                        return;
                    }
                    if(!isOwnerOf(player, target.getLocation())) {
                        evt.setCancelled(true);
                        player.sendMessage("Sorry, you are not the owner of that sign.");
                        return;
                    }
                    if(source.getType() == Material.AIR) {
                        updates.put(source.getLocation(), new SignUpdater(target, source, player));
                        itemInHand.setAmount(itemInHand.getAmount()+1);
                    }
                }
            } else if(holding == Material.getMaterial(getConfiguration().getInt("view-owner", 280))) {
                if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
                player.sendMessage("That sign is owned by " + getOwnerOf(target));
            } else if(holding == Material.getMaterial(getConfiguration().getInt("set-owner", 288))) {
                if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
                if(!canSetOwner(player)) {
                    evt.setCancelled(true);
                    player.sendMessage("Sorry, you do not have permission to set the owner of signs.");
                    return;
                }
                ownerSetting.put(player.getName(),target.getLocation());
                player.sendMessage("Who should be the new owner of the sign?");
                player.sendMessage("(Punch them or enter their name into chat.)");
            }
        }
        
        @Override
        public void onPlayerChat(PlayerChatEvent evt) {
            Player player = evt.getPlayer();
            if(!ownerSetting.containsKey(player.getName())) return;
            String[] split = evt.getMessage().trim().split("\\s+");
            split[0] = split[0].trim();
            if(split[0].equals("@")) {
                ownership.put(ownerSetting.get(player.getName()), player.getName());
                player.sendMessage("Owner set to " + player.getName());
            } else if(split[0].equals("#")) {
                ownership.remove(ownerSetting.get(player.getName()));
                player.sendMessage("Owner set to no-one");
            } else {
                ownership.put(ownerSetting.get(player.getName()), split[0]);
                player.sendMessage("Owner set to " + (split[0].equals("*") ? "everyone" : split[0]));
                player.sendMessage("(Note: if no player by that name exists, no-one will be able to edit this sign.)");
            }
            ownerSetting.remove(player.getName());
            evt.setCancelled(true);
        }
    };
    
    private EntityListener el = new EntityListener() {
        @Override
        public void onEntityDamage(EntityDamageEvent event) {
            if(!(event instanceof EntityDamageByEntityEvent)) return;
            EntityDamageByEntityEvent evt = (EntityDamageByEntityEvent) event;
            Entity damager = evt.getDamager();
            if(!(damager instanceof Player)) return;
            Entity damaged = evt.getEntity();
            if(!(damaged instanceof Player)) return;
            Player player = (Player) damaged, setter = (Player) damager;
            if(!ownerSetting.containsKey(setter.getName())) return;
            ownership.put(ownerSetting.get(setter.getName()), player.getName());
            setter.sendMessage("Owner set to " + player.getName());
            ownerSetting.remove(setter.getName());
            evt.setCancelled(true);
        }
    };

    private boolean hasPermission(Player who) {
        if(p == null) return true;
        return p.has(who, "simplesign.edit");
    }

    private boolean canStackSigns(Material clicked, BlockFace face) {
		if(clicked != Material.SIGN_POST) return false;
		if(face != BlockFace.UP) return false;
		return getConfiguration().getBoolean("allow-stacking", true);
	}

	private boolean canSetOwner(Player who) {
        if(p == null) return who.isOp();
        return p.has(who, "simplesign.setowner");
    }
    
    protected boolean isOwnerOf(Player player, Location location) {
        String owner = ownership.get(location);
        boolean canEditAll = player.isOp();
        if(p != null) canEditAll = p.has(player, "simplesign.edit.all");
        if(owner == null) return canEditAll;
        if(owner.equalsIgnoreCase(player.getName())) return true;
        if(owner.equals("*")) return true;
        return canEditAll;
    }
    
    private boolean hasColour(Player who, ChatColor clr) {
        if(p == null) return who.isOp();
        String colourName = clr.toString().toLowerCase().replace("_", "");
        return p.has(who, "simplesign.colour." + colourName) || p.has(who, "simplesign.color." + colourName);
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
        
        Plugin perms = getServer().getPluginManager().getPlugin("Permissions");
        if(perms != null) {
            p = ((Permissions) perms).getHandler();
            logger.info("Using Permissions for sign editing.");
        } else {
            logger.info("Sign editing restricted to ops and owners.");
        }
    }
    
    private void sendSignUpdate(Block signBlock, Player who) {
        final int i = signBlock.getX(), j = signBlock.getY(), k = signBlock.getZ();
        // This line updates the sign for the user.
        final Sign sign = (Sign) signBlock.getState();
        getServer().getScheduler().scheduleSyncDelayedTask(this, new Runnable() {
            @Override
            public void run() {
                sign.update(true);
            }
        });
    }
    
    private String parseColour(String line, Player setter) {
        String regex = "&(?<!&&)(?=[0-9a-fA-F])";
        Formatter fmt = new Formatter();
        for(ChatColor clr : ChatColor.values()) {
            if(!hasColour(setter, clr)) continue;
            String code = Integer.toHexString(clr.getCode());
            line = line.replaceAll(fmt.format(regex, code).toString(), "\u00A7");
        }
        return line.replace("&&", "&");
        //return line.replaceAll(regex, "\u00A7");
    }

    private String getOwnerOf(Block block) {
        String owner = ownership.get(block.getLocation());
        if(owner == null) return "no-one";
        if(owner.equals("*")) return "everyone";
        return owner;
    }
}
