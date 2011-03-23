package ca.celticminstrel.signedit;

import java.util.Arrays;
import java.util.HashMap;
import java.util.logging.Logger;

import net.minecraft.server.EntityPlayer;
import net.minecraft.server.Packet130UpdateSign;
import net.minecraft.server.Packet53BlockChange;
import net.minecraft.server.WorldServer;

import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.craftbukkit.entity.CraftPlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.Event.Priority;
import org.bukkit.event.Event.Type;
import org.bukkit.event.block.BlockCanBuildEvent;
import org.bukkit.event.block.BlockListener;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerItemEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginDescriptionFile;
import org.bukkit.plugin.java.JavaPlugin;

public class SignEdit extends JavaPlugin {
    private final class SignUpdater implements Runnable {
        private final Block target;
        private final Block source;
        private String[] lines;
        private Player setter;

        private SignUpdater(Block target, Block source, Player who) {
            this.target = target;
            this.source = source;
            this.setter = who;
        }
        
        private SignUpdater setLines(String[] newLines) {
            this.lines = newLines;
            return this;
        }

        public void run() {
            logger.info("Running task.");
            if(target.getType() != Material.WALL_SIGN && target.getType() != Material.SIGN_POST) {
                //logger.info("Sign placement failed?");
                return;
            }
            Sign targetState = (Sign) target.getState();
            for(int i = 0; i < 4; i++) {
                //logger.info("Source[" + i + "] = \"" + lines[i] + "\"");
                //logger.info("Target[" + i + "] = \"" + targetState.getLine(i) + "\"");
                if(!lines[i].isEmpty())
                    targetState.setLine(i, lines[i]);
                //logger.info("R[" + i + "] = \"" + targetState.getLine(i) + "\"");
            }
            source.setType(Material.AIR);
            int i = target.getX(), j = target.getY(), k = target.getZ();
            // This line updates the sign for the user.
            ((EntityPlayer) ((CraftPlayer) setter).getHandle()).a.b(new Packet130UpdateSign(i, j, k, targetState.getLines()));
            //((EntityPlayer) ((CraftPlayer) setter).getHandle()).a.b(new Packet53BlockChange(i, j, k, (WorldServer) target.getWorld()));
            /*
             * [Mar 23@12:58:25am] Verrier: Looks like BlockLever has some nice craftbukkit code that forces a
             *   client side update
             * [Mar 23@12:59:17am] Verrier: assuming it's canceled, I mean:
             *   ((EntityPlayer) entityhuman).a.b(new Packet53BlockChange(i, j, k, (WorldServer) world));  :D
             *   There you go celticminstrel :)  lol
*/
        }
    }

    Logger logger = Logger.getLogger("Minecraft.SignEdit");
    private SignEdit plugin;
    private HashMap<Location,SignUpdater> updates = new HashMap<Location,SignUpdater>();

    public void onDisable() {
        logger.info("Disabled " + getDescription().getFullName());
    }
    
    private BlockListener bl = new BlockListener() {
//        @Override
//        public void onBlockPlace(BlockPlaceEvent evt) {
//            if(evt.getBlock().getType() == Material.SIGN_POST)
//                logger.info("Placed a sign post.");
//            if(evt.getBlock().getType() == Material.WALL_SIGN)
//                logger.info("Placed a wall sign.");
//            logger.info(evt.getType() + ", " + evt.getBlock().getType());
//        }
        @Override
        public void onSignChange(SignChangeEvent evt) {
            //logger.info(evt.getType() + ", " + evt.getBlock().getType() + ", " + Arrays.asList(evt.getLines()));
            Location loc = evt.getBlock().getLocation();
            if(updates.containsKey(loc)) {
                updates.get(loc).setLines(evt.getLines()).run();
            }
            //logger.info(loc.toString());
            //logger.info(updates.toString());
        }
//        @Override
//        public void onBlockCanBuild(BlockCanBuildEvent evt) {
//            logger.info("Checking can build " + evt.getMaterial() + " on " + evt.getBlock());
//        }
    };
    
    private PlayerListener pl = new PlayerListener() {
        @Override
        public void onPlayerItem(PlayerItemEvent evt) {
            Material holding = evt.getPlayer().getItemInHand().getType();
            Material clicked = evt.getBlockClicked().getType();
            logger.info(evt.getType() + ", " + clicked + ", " + holding + ", " + evt.getBlockFace());
            if(holding != Material.SIGN) return;
            if(clicked == Material.WALL_SIGN || clicked == Material.SIGN_POST) {
                Block target = evt.getBlockClicked();
                Block source = target.getRelative(evt.getBlockFace());
                //getServer().getScheduler().scheduleSyncDelayedTask(plugin,
                updates.put(source.getLocation(), new SignUpdater(target, source, evt.getPlayer()));
                evt.getPlayer().getItemInHand().setAmount(evt.getPlayer().getItemInHand().getAmount()+1);
            }
        }
    };

    public void onEnable() {
        plugin = this;
        PluginDescriptionFile pdfFile = this.getDescription();
        logger.info("Enabled " + pdfFile.getFullName());
        //getServer().getPluginManager().registerEvent(Type.BLOCK_PLACED, bl, Priority.Normal, this);
        //getServer().getPluginManager().registerEvent(Type.BLOCK_CANBUILD, bl, Priority.Normal, this);
        getServer().getPluginManager().registerEvent(Type.SIGN_CHANGE, bl, Priority.Normal, this);
        getServer().getPluginManager().registerEvent(Type.PLAYER_ITEM, pl, Priority.Normal, this);
    }

}
