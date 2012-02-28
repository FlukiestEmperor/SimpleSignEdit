package ca.celticminstrel.signedit;

import org.bukkit.Location;
import org.bukkit.block.Block;

public interface SignEditAPI {
	/**
	 * Public API function to set the owner of a sign. It's recommended that plugins which handle
	 * right-clicks on signs set the owner of their signs to no-one.
	 * @param whichSign The location of the sign whose ownership you are changing.
	 * @param owner The name of the new owner. Use "#" for no-one and "*" for everyone. Null is also no-one.
	 * @return Whether a sign's owner was actually changed. Will return false if there is no sign at the location,
	 * if the sign already has the requested owner, or if the tracking of sign owners has been disabled in the
	 * config file.
	 */
	boolean setSignOwner(Location whichSign, String owner);
	/**
	 * Public API function to set the owner of a sign. It's recommended that plugins which handle
	 * right-clicks on signs set the owner of their signs to no-one.
	 * @param whichSign The sign whose ownership you are changing.
	 * @param owner The name of the new owner. Use "#" for no-one and "*" for everyone. Null is also no-one.
	 * @return Whether a sign's owner was actually changed. Will return false if there is no sign at the location,
	 * if the sign already has the requested owner, or if the tracking of sign owners has been disabled in the
	 * config file.
	 */
	boolean setSignOwner(Block whichSign, String owner);
	/**
	 * Public API function to get the owner of a sign.
	 * @param whichSign The location of the sign whose ownership you are checking.
	 * @return The sign's current owner; "#" means no-one, "*" means everyone.
	 */
	String getSignOwner(Location whichSign);
	/**
	 * Public API function to get the owner of a sign.
	 * @param whichSign The sign whose ownership you are checking.
	 * @return The sign's current owner; "#" means no-one, "*" means everyone.
	 */
	String getSignOwner(Block whichSign);
	/**
	 * Convenience method to check if a sign has an owner
	 * @param whichSign The location of the sign whose owned status you are checking.
	 * @return True if the sign is owned by someone (or everyone), false if it is owned by no-one.
	 */
	boolean isSignOwned(Location whichSign);
	/**
	 * Convenience method to check if a sign has an owner
	 * @param whichSign The sign whose owned status you are checking.
	 * @return True if the sign is owned by someone (or everyone), false if it is owned by no-one.
	 */
	boolean isSignOwned(Block whichSign);
}
