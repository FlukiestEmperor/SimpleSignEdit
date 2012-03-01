package ca.celticminstrel.signedit;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import static org.bukkit.event.EventPriority.*;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.block.SignChangeEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.ItemStack;

final class SignListener implements Listener {
	private final SignEdit signEdit;
	
	SignListener(SignEdit instance) {
		signEdit = instance;
	}

	@EventHandler(priority=NORMAL)
	public void onSignChange(SignChangeEvent evt) {
		Location loc = evt.getBlock().getLocation();
		Player setter = evt.getPlayer();
		for(int i = 0; i < 4; i++)
			evt.setLine(i, signEdit.parseColour(evt.getLine(i), setter));
		if(signEdit.updates.containsKey(loc)) {
			//logger.info("Editing sign at " + loc);
			signEdit.updates.get(loc).setLines(evt.getLines()).run();
			signEdit.updates.remove(loc);
			evt.setCancelled(true);
		} else if(!signEdit.isSignOwned(loc)) {
			//logger.info("Placing sign at " + loc);
			String owner = null, dflt = Options.DEFAULT_OWNER.get();
			if(dflt.equalsIgnoreCase("placer")) owner = setter.getName();
			else if(dflt.equalsIgnoreCase("none")) owner = SignEdit.NO_OWNER;
			else if(dflt.equals(SignEdit.PUBLIC)) owner = SignEdit.PUBLIC;
			signEdit.setSignOwner(loc, owner);
		}
	}
	
	@EventHandler(priority=NORMAL)
	public void onBlockBreak(BlockBreakEvent evt) {
		final Block block = evt.getBlock();
		if(signEdit.updates.containsKey(block.getLocation())) {
			//logger.info("Cancelled breaking of an updater sign.");
			evt.setCancelled(true);
		} else if(block.getType() == Material.WALL_SIGN || block.getType() == Material.SIGN_POST) {
			if(Options.BREAK_PROTECT.get()) {
				Player player = evt.getPlayer();
				if(!signEdit.canEditSign(player, evt.getBlock().getLocation())) {
					if(signEdit.getSignOwner(evt.getBlock()).equals(SignEdit.NO_OWNER) && Options.ORPHANED_BREAKABLE.get())
						return; // Orphaned (ownerless) signs have been configured as being breakable by all
					evt.setCancelled(true);
					player.sendMessage("Sorry, you are not the owner of that sign.");
					return;
				}
			}
			Bukkit.getScheduler().scheduleSyncDelayedTask(signEdit, new Runnable() {
				@Override public void run() {
					signEdit.setSignOwner(block.getLocation(), SignEdit.NO_OWNER);
				}
			});
		}
	}

	@EventHandler(priority=HIGHEST)
	public void onPlayerInteract(PlayerInteractEvent evt) {
		if(evt.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Player player = evt.getPlayer();
		String sneak = Options.SNEAKING.get();
		Boolean needsSneak = null;
		if(sneak.equalsIgnoreCase("true")) needsSneak = true;
		else if(sneak.equalsIgnoreCase("false")) needsSneak = false;
		if(needsSneak != null && !needsSneak.equals(player.isSneaking())) return;
		ItemStack itemInHand = player.getItemInHand();
		if(itemInHand == null) return;
		Material holding = itemInHand.getType();
		Block target = evt.getClickedBlock();
		Material clicked = target.getType();
		if(holding == Material.SIGN) {
			if(clicked == Material.WALL_SIGN || clicked == Material.SIGN_POST) {
				if(signEdit.canStackSigns(clicked, evt.getBlockFace())) return;
				Block source = target.getRelative(evt.getBlockFace());
				if(!signEdit.hasPermission(player)) {
					evt.setCancelled(true);
					player.sendMessage("Sorry, you do not have permission to edit signs.");
					return;
				}
				if(!signEdit.canEditSign(player, target.getLocation())) {
					evt.setCancelled(true);
					player.sendMessage("Sorry, you are not allowed to edit that sign.");
					return;
				}
				if(source.getType() == Material.AIR) {
					if(evt.isCancelled()) evt.setCancelled(false);
					signEdit.updates.put(source.getLocation(), new SignUpdater(signEdit, target, source, player));
				}
			}
		} else if(holding == Material.getMaterial(Options.VIEW_OWNER.get())) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
			player.sendMessage("That sign is owned by " + signEdit.getOwnerOf(target));
		} else if(holding == Material.getMaterial(Options.SET_OWNER.get())) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
			if(signEdit.ownerSetting.containsKey(player.getName())) {
				Location loc = signEdit.ownerSetting.get(player.getName());
				if(loc.equals(target.getLocation())) {
					signEdit.setSignOwner(loc, player.getName());
					player.sendMessage("Owner set to " + player.getName());
					signEdit.ownerSetting.remove(player.getName());
					return;
				}
			}
			if(!signEdit.canSetOwner(player)) {
				evt.setCancelled(true);
				player.sendMessage("Sorry, you do not have permission to set the owner of signs.");
				return;
			}
			signEdit.ownerSetting.put(player.getName(),target.getLocation());
			player.sendMessage("Who should be the new owner of the sign?");
			player.sendMessage("(Punch them or enter their name into chat.)");
		}
	}
	
	@EventHandler(priority=HIGHEST)
	public void onBlockPlace(BlockPlaceEvent evt) {
		Block block = evt.getBlockPlaced();
		if(block.getType() != Material.WALL_SIGN && block.getType() != Material.SIGN_POST) return;
		if(signEdit.updates.containsKey(block.getLocation())) {
			if(evt.isCancelled()) evt.setCancelled(false);
			Sign updater = (Sign) block.getState();
			Sign editing = (Sign) evt.getBlockAgainst().getState();
			int i = 0;
			for(String line : editing.getLines())
				updater.setLine(i++, line.replace("&","&&").replace('\u00A7', '&'));
			updater.update();
		}
	}
}