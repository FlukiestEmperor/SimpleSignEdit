package ca.celticminstrel.signedit;

import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.Sign;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

final class SignUpdater implements Runnable {
	private final SignEdit signEdit;
	private final Block target;
	private final Block source;
	private String[] lines;
	private Player setter;

	SignUpdater(SignEdit instance, Block targ, Block src, Player who) {
		signEdit = instance;
		target = targ;
		source = src;
		setter = who;
	}
	
	SignUpdater setLines(String[] newLines) {
		lines = newLines;
		return this;
	}

	@Override
	public void run() {
		if(target.getType() != Material.WALL_SIGN && target.getType() != Material.SIGN_POST) return;
		if(!signEdit.hasPermission(setter)) {
			source.setType(Material.AIR);
			setter.sendMessage("Sorry, your sign editing permissions were revoked while you were editing.");
			return;
		}
		Sign targetState = (Sign) target.getState();
		for(int i = 0; i < 4; i++) targetState.setLine(i, lines[i]);
		source.setType(Material.AIR);
		signEdit.sendSignUpdate(targetState);
		if(setter.getGameMode() != GameMode.CREATIVE) {
			ItemStack itemInHand = setter.getItemInHand();
			if(itemInHand == null || itemInHand.getType() == Material.AIR)
				setter.setItemInHand(new ItemStack(Material.SIGN,1));
			else itemInHand.setAmount(itemInHand.getAmount()+1);
		}
	}
}