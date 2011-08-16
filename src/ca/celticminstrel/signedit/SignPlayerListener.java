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
		signEdit = instance;
	}

	@Override
	public void onPlayerInteract(PlayerInteractEvent evt) {
		if(evt.getAction() != Action.RIGHT_CLICK_BLOCK) return;
		Player player = evt.getPlayer();
		String sneak = Option.SNEAKING.get();
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
				if(!signEdit.isOwnerOf(player, target.getLocation())) {
					evt.setCancelled(true);
					player.sendMessage("Sorry, you are not the owner of that sign.");
					return;
				}
				if(source.getType() == Material.AIR) {
					if(evt.isCancelled()) evt.setCancelled(false);
					signEdit.updates.put(source.getLocation(), new SignUpdater(signEdit, target, source, player));
				}
			}
		} else if(holding == Material.getMaterial(Option.VIEW_OWNER.get())) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
			player.sendMessage("That sign is owned by " + signEdit.getOwnerOf(target));
		} else if(holding == Material.getMaterial(Option.SET_OWNER.get())) {
			if(clicked != Material.WALL_SIGN && clicked != Material.SIGN_POST) return;
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
	
	@Override
	public void onPlayerChat(PlayerChatEvent evt) {
		Player player = evt.getPlayer();
		if(!signEdit.ownerSetting.containsKey(player.getName())) return;
		String[] split = evt.getMessage().trim().split("\\s+");
		split[0] = split[0].trim();
		if(split[0].equals("@")) {
			signEdit.setSignOwner(signEdit.ownerSetting.get(player.getName()), player.getName());
			player.sendMessage("Owner set to " + player.getName());
		} else {
			signEdit.setSignOwner(signEdit.ownerSetting.get(player.getName()), split[0]);
			String who = split[0];
			if(split[0].equals("#")) who = "no-one";
			else if(split[0].equals("*")) who = "everyone";
			player.sendMessage("Owner set to " + who);
			player.sendMessage("(Note: if no player by that name exists, no-one will be able to edit this sign.)");
		}
		signEdit.ownerSetting.remove(player.getName());
		evt.setCancelled(true);
	}
}