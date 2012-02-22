package ca.celticminstrel.signedit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import static org.bukkit.event.EventPriority.*;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.player.PlayerChatEvent;

final class OwnerListener implements Listener {
	private final SignEdit signEdit;
	
	OwnerListener(SignEdit instance) {
		signEdit = instance;
	}

	@EventHandler(priority=NORMAL)
	public void onEntityDamage(EntityDamageEvent event) {
		if(!(event instanceof EntityDamageByEntityEvent)) return;
		EntityDamageByEntityEvent evt = (EntityDamageByEntityEvent) event;
		Entity damager = evt.getDamager();
		if(!(damager instanceof Player)) return;
		Entity damaged = evt.getEntity();
		if(!(damaged instanceof Player)) return;
		Player player = (Player) damaged, setter = (Player) damager;
		if(!signEdit.ownerSetting.containsKey(setter.getName())) return;
		signEdit.setSignOwner(signEdit.ownerSetting.get(setter.getName()), player.getName());
		setter.sendMessage("Owner set to " + player.getName());
		signEdit.ownerSetting.remove(setter.getName());
		evt.setCancelled(true);
	}
	
	@EventHandler(priority=NORMAL)
	public void onPlayerChat(PlayerChatEvent evt) {
		Player player = evt.getPlayer();
		if(!signEdit.ownerSetting.containsKey(player.getName())) return;
		String[] split = evt.getMessage().trim().split("\\s+");
		split[0] = split[0].trim();
		if(split[0].equals(SignEdit.ME)) {
			signEdit.setSignOwner(signEdit.ownerSetting.get(player.getName()), player.getName());
			player.sendMessage("Owner set to " + player.getName());
		} else {
			signEdit.setSignOwner(signEdit.ownerSetting.get(player.getName()), split[0]);
			String who = split[0];
			if(split[0].equals(SignEdit.NO_OWNER)) who = "no-one";
			else if(split[0].equals(SignEdit.PUBLIC)) who = "everyone";
			player.sendMessage("Owner set to " + who);
			player.sendMessage("(Note: if no player by that name exists, no-one will be able to edit this sign.)");
		}
		signEdit.ownerSetting.remove(player.getName());
		evt.setCancelled(true);
	}
}