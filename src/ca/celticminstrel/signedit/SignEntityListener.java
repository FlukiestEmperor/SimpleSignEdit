package ca.celticminstrel.signedit;

import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityListener;

final class SignEntityListener extends EntityListener {
	private final SignEdit signEdit;
	
	SignEntityListener(SignEdit instance) {
		signEdit = instance;
	}

	@Override
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
}