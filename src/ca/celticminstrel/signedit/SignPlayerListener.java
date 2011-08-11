package ca.celticminstrel.signedit;

import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerChatEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerListener;
import org.bukkit.inventory.ItemStack;

final class SignPlayerListener extends PlayerListener {
	private final SignEdit signEdit;
	
	SignPlayerListener(SignEdit instance) {
		this.signEdit = instance;
	}

	@Override
	public void onPlayerInteract(PlayerInteractEvent evt) {
		if(evt.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Player player = evt.getPlayer();
		String sneak = this.signEdit.getConfiguration().getString("sneaking","both");
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
				if(this.signEdit.canStackSigns(clicked, evt.getBlockFace())) return;
				Block source = target.getRelative(evt.getBlockFace());
				if(!this.signEdit.hasPermission(player)) {
					evt.setCancelled(true);
					player.sendMessage("Sorry, you do not have permission to edit signs.");
					return;
				}
				if(!this.signEdit.isOwnerOf(player, target.getLocation())) {
					evt.setCancelled(true);
					player.sendMessage("Sorry, you are not the owner of that sign.");
					return;
				}
				if(source.getType() == Material.AIR) {
					this.signEdit.updates.put(source.getLocation(), new SignUpdater(this.signEdit, target, source, player));
					itemInHand.setAmount(itemInHand.getAmount()+1);
				}
			}
		} else if(holding == Material.getMaterial(this.signEdit.getConfiguration().getInt("view-owner", 280))) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
			player.sendMessage("That sign is owned by " + this.signEdit.getOwnerOf(target));
		} else if(holding == Material.getMaterial(this.signEdit.getConfiguration().getInt("set-owner", 288))) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
			if(!this.signEdit.canSetOwner(player)) {
				evt.setCancelled(true);
				player.sendMessage("Sorry, you do not have permission to set the owner of signs.");
				return;
			}
			this.signEdit.ownerSetting.put(player.getName(),target.getLocation());
			player.sendMessage("Who should be the new owner of the sign?");
			player.sendMessage("(Punch them or enter their name into chat.)");
		}
	}
	
	@Override
	public void onPlayerChat(PlayerChatEvent evt) {
		Player player = evt.getPlayer();
		if(!this.signEdit.ownerSetting.containsKey(player.getName())) return;
		String[] split = evt.getMessage().trim().split("\\s+");
		split[0] = split[0].trim();
		if(split[0].equals("@")) {
			this.signEdit.ownership.put(this.signEdit.ownerSetting.get(player.getName()), player.getName());
			player.sendMessage("Owner set to " + player.getName());
		} else if(split[0].equals("#")) {
			this.signEdit.ownership.remove(this.signEdit.ownerSetting.get(player.getName()));
			player.sendMessage("Owner set to no-one");
		} else {
			this.signEdit.ownership.put(this.signEdit.ownerSetting.get(player.getName()), split[0]);
			player.sendMessage("Owner set to " + (split[0].equals("*") ? "everyone" : split[0]));
			player.sendMessage("(Note: if no player by that name exists, no-one will be able to edit this sign.)");
		}
		this.signEdit.ownerSetting.remove(player.getName());
		evt.setCancelled(true);
	}
}