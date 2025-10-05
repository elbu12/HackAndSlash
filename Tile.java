package tile;

import java.awt.Image;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.List;

import loot.TriggeredInteraction;
import solid.Solid;

public abstract class Tile {
	private final int x;
	private final int y;
	private World world;
	private List <TriggeredInteraction> triggeredInteractions = new ArrayList <>();
	public World getWorld() {
		return world;
	}
	public void setWorld(World world) {
		this.world = world;
	}
	public static final List <Solid> NO_SOLIDS = new ArrayList<>();
	public Tile(int x, int y) {
		this.x = x;
		this.y = y;
	}
	public final int getX() {
		return x;
	}
	public final int getY() {
		return y;
	}
	public boolean isObstruction() {
		return true;
	}
	public boolean isWalkable() {
		return false;
	}
	public List <Solid> getSolids(){
		return NO_SOLIDS;
	}
	public boolean add(Solid solid) {
		return false;
	}
	public boolean remove(Solid solid) {
		return false;
	}
	public void add(TriggeredInteraction i) {
		triggeredInteractions.add(i);
	}
	public void remove(TriggeredInteraction i) {
		triggeredInteractions.remove(i);
	}
	public List <TriggeredInteraction> getTriggeredInteractions(){
		return triggeredInteractions;
	}
	public String toString() {
		return "Tile (" + x + ", " + y + ")";
	}
	protected BufferedImage getImage() {
		return null;
	}
	/*
	 * For optimization, some tiles get their scaled images saved, to prevent
	 * recalculating every time the screen is drawn. These are saved in the
	 * world. Each tile class that uses this caching has a unique index
	 * number, representing its place in the world's cache.
	 * 
	 * Tiles that elect to use this caching should override getTileTypeNumber
	 * to return a number that is unique to that class. If getTileTypeNumber
	 * returns -1, then caching will not be used.
	 */
	protected int getTileTypeNumber() {
		return -1;
	}
	final Image getScaledImage(int width, int height) {
		int tileTypeNumber = getTileTypeNumber();
		Image image;
		if (tileTypeNumber >= 0) {
			//Look for cached version
			image = world.getCachedTileImage(getTileTypeNumber(), width, height);
			if (image != null) {
				return image;
			}
		}
		//Need a new image
		image = getImage().getScaledInstance(width, height, Image.SCALE_FAST);
		if (tileTypeNumber >= 0) {
			//cache the image
			world.setCachedTileImage(tileTypeNumber, image, width, height);
		}
		return image;
	}
}
