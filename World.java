package tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.geom.AffineTransform;
import java.awt.image.BufferedImage;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import loot.TriggeredInteraction;
import main.Game;
import solid.Solid;
import solid.SolidActor;
import solid.TileSolid;
import support.Geometry;
import support.NodeMap;
import support.PathNode;
import support.PathNodeHeap;

public class World {
	public static final int SCREEN_WIDTH_IN_TILES = 30;
	public static final int SCREEN_HEIGHT_IN_TILES = 30;
	public static final double SCREEN_WIDTH_IN_TILES_075 = SCREEN_WIDTH_IN_TILES * 0.75;
	public static final double SCREEN_HEIGHT_IN_TILES_075 = SCREEN_HEIGHT_IN_TILES * 0.75;
	
	private final Tile[][] tiles;
	private final List <TileImageCache> cachedTileImageList = new ArrayList <> ();
	private final List <Tile> tileList;
	private final Set <Object> drawSet;
	private final TileSolid tileSolid = new TileSolid();
	private BufferedImage bigImage;
	private BufferedImage smallImage;
	private final AffineTransform transform = new AffineTransform();
	public final int width;
	public final int height;
	//Each instance of path finding gets its own serial number
	private int pathFindingNumber = 0;
	
	private Game game;
	
	private ArrayList <NodeMap> nodeMaps = new ArrayList<>();
	public World(Tile[][] tiles) {
		this.tiles = tiles;
		width = tiles.length;
		height = tiles[0].length;
		for (Tile[] column : tiles) {
			for (Tile tile : column) {
				tile.setWorld(this);
			}
		}
		tileList = new ArrayList<>();
		drawSet = new HashSet<>();
	}
	public void setGame(Game game) {
		if (this.game != null) {
			throw new RuntimeException("Attempting to set game for a world that already has a game.");
		}
		this.game = game;
	}
	public Game getGame() {
		return game;
	}
	public Tile get(int x, int y) {
		if (x < 0 || x >= width || y < 0 || y >= height) {
			return null;
		}
		return tiles[x][y];
	}
	private boolean canStandOnTile(Solid solid, double x, double y) {
		double cx;	//center x
		double cy;	//center y
		if (solid.getShape() == Solid.RECTANGLE) {
			cx = x + (solid.getWidth() * 0.5);
			cy = y + (solid.getHeight() * 0.5);
		}
		else {
			cx = x;
			cy = y;
		}
		if (cx < 0 || cy < 0) {
			//No moving off the map
			return false;
		}
		Tile t = get((int) cx, (int) cy);
		if (t == null) {
			return false;
		}
		else if (t.isObstruction() || (solid.walks() && !t.isWalkable())) {
			return false;
		}
		return true;
	}
	public final boolean canMove(Solid solid, double x, double y) {
		return canMove(solid, x, y, false);
	}
	public final boolean canMove(Solid solid, double x, double y, boolean ignoreMobileSolids) {
		if (!canStandOnTile(solid, x,y)) {
			return false;
		}
		drawSet.clear();
		for (Tile tile : solid.getIntersectedTiles(x, y, this, tileList)) {
			if (tile.isObstruction() || (solid.walks() && !tile.isWalkable())) {
				return false;
			}
			for (Solid s : tile.getSolids()) {
				if (s == solid || drawSet.contains(s)) {
					continue;
				}
				else if (solid.wouldIntersectSolid(x, y, s, ignoreMobileSolids)) {
					return false;
				}
				drawSet.add(s);
			}
		}
		return true;
	}
	//Returns whether move was successful. Mostly used for testing
	public final boolean moveIfCan(Solid solid, double x, double y) {
		if (!canStandOnTile(solid, x,y)) {
			return false;
		}
		drawSet.clear();
		TileArrayGenerator newTiles = new TileArrayGenerator();
		for (Tile tile : solid.getIntersectedTiles(x, y, this, tileList)) {
			if (tile.isObstruction()) {
				return false;
			}
			for (Solid s : tile.getSolids()) {
				if (s == solid || drawSet.contains(s)) {
					continue;
				}
				else if (solid.wouldIntersectSolid(x, y, s, false)) {
					return false;
				}
				drawSet.add(s);
			}
			newTiles.update(tile);
		}
		/**
		 * If we get here, then the move is legal. Put new tiles into an
		 * array. Old tiles get their own array. Now we can iterate through
		 * old and new tile lists and determine which to add/remove.
		 */
		TileArrayGenerator oldTiles = new TileArrayGenerator();
		for (Tile tile : solid.getTiles()) {
			oldTiles.update(tile);
		}
		newTiles.generate();
		oldTiles.generate();
		for (Tile tile : tileList) {
			newTiles.add(tile);
		}
		for (Tile tile : solid.getTiles()) {
			if (!newTiles.contains(tile)) {
				//Old tile, not in new set
				tile.remove(solid);
			}
			oldTiles.add(tile);
		}
		//Temporarily clear solid.getTiles; will be rebuilt in next step
		solid.getTiles().clear();
		for (Tile tile : tileList) {
			solid.getTiles().add(tile);
			if (!oldTiles.contains(tile)) {
				//New tile, not in old set
				tile.add(solid);
			}
		}
		solid.setX(x);
		solid.setY(y);
		solid.setWorld(this);
		return true;
	}
	public void place(TriggeredInteraction i, double x, double y) {
		i.setX(x);
		i.setY(y);
		//Find all intersected tiles
		int x0;
		int y0;
		int x1;
		int y1;
		switch (i.getShape()) {
		case Solid.CIRCLE:
			x0 = (int)(x - i.getRadius());
			y0 = (int)(y - i.getRadius());
			x1 = (int)(x + i.getRadius());
			y1 = (int)(y + i.getRadius());
			break;
		case Solid.RECTANGLE:
			x0 = (int)(x);
			y0 = (int)(y);
			x1 = (int)(x + i.getWidth());
			y1 = (int)(y + i.getHeight());
			break;
		default:
			x0 = (int)(x);
			y0 = (int)(y);
			x1 = x0;
			y1 = y0;
			break;
		}
		for (int j=x0; j<=x1; j++) {
			for (int k=y0; k<=y1; k++) {
				Tile tile = get(j, k);
				if (tile == null) {
					continue;
				}
				boolean intersection;
				switch (i.getShape()) {
				case Solid.CIRCLE:
					intersection = Geometry.circleIntersectRectangle(x, y, i.getRadius(), j, k, 1, 1);
					break;
				case Solid.RECTANGLE:
					intersection = Geometry.rectangleIntersectRectangle(x, y, i.getWidth(), i.getHeight(), j, k, 1, 1);
					break;
				default:
					intersection = true;
					break;
				}
				if (intersection) {
					tile.add(i);
					i.add(tile);
				}
			}
		}
		
	}
	/**
	 * Attempts to move solid to (x,y). If move is impossible, will try again
	 * with smaller steps.
	 */
	public final boolean moveAsMuchAsPossible(Solid solid, double x, double y) {
		double dx = x - solid.getX();
		double dy = y - solid.getY();
		for (int i=0; i<3; i++) {
			if (moveIfCan(solid, solid.getX() + dx, solid.getY() + dy)) {
				return true;
			}
			dx *= 0.5;
			dy *= 0.5;
		}
		return false;
	}
	/**
	 * canMove with the extra arguments determines if a solid can move along the
	 * given line segment without intersecting a null/obstructing tile/solid
	 * obstruction.
	 * 
	 * Note that this will NOT check for intersections with solids that can move.
	 * It is used for path finding. Therefore, mobile solids are expected to move
	 * out of the way.
	 */
	public final boolean canMove(Solid solid, double x0, double y0, double dx, double dy) {
		double x1 = x0 + dx;
		double y1 = y0 + dy;
		//test destination
		if (!canMove(solid, x1, y1, true)) {
			return false;
		}
		if (dx < 0) {
			//assume left-to-right movement
			double temp = x0;
			x0 = x1;
			x1 = temp;
			dx = 0 - dx;
			temp = y0;
			y0 = y1;
			y1 = temp;
			dy = 0 - dy;
		}
		drawSet.clear();
		if (dx == 0) {
			//strictly vertical
			double leftEdge = x0;
			double rightEdge = x0;
			double top = Math.max(y0, y1);
			double bottom = Math.min(y0, y1);
			switch (solid.getShape()) {
			case Solid.CIRCLE:
				leftEdge -= solid.getRadius();
				rightEdge += solid.getRadius();
				break;
			case Solid.RECTANGLE:
				rightEdge += solid.getWidth();
				top += solid.getHeight() * 0.5;
				bottom += solid.getHeight() * 0.5;
				break;
			//last case is for a point
			}
			for (int x=(int)leftEdge; x<=rightEdge; x++) {
				for (int y=(int)bottom; y<=top; y++) {
					Tile tile = get(x, y);
					if (tile == null || tile.isObstruction()) {
						return false;
					}
					else if (solid.walks() && !tile.isWalkable()) {
						//Only blocks path if solid's center crosses it
						if (Geometry.lineSegmentIntersectRectangle(x0, y0, 0, dy,
								tile.getX(), tile.getY(), 1, 1)) {
							return false;
						}
					}
					for (Solid s : tile.getSolids()) {
						if (s == solid || s.isMobile() || drawSet.contains(s)) {
							continue;
						}
						if (Geometry.parallelogramIntersectSolid(leftEdge, bottom, leftEdge,
								top, rightEdge - leftEdge, 0, s)) {
							return false;
						}
						drawSet.add(s);
					}
				}
			}
			return true;
		}
		/**
		 * If we are here it means dx != 0.
		 * Determine the parallelogram traversed by this solid.
		 */
		double lowerLeftX;
		double lowerLeftY;
		double upperLeftX;
		double upperLeftY;
		switch (solid.getShape()) {
		case Solid.CIRCLE:
			//Get vector perpendicular to (dx, dy)
			double px = dy;
			double py = 0 - dx;
			double r = solid.getRadius() / Math.sqrt((dx * dx) + (dy * dy));
			px *= r;
			py *= r;
			if (py < 0) {
				py = 0 - py;
				px = 0 - px;
			}
			lowerLeftX = x0 - px;
			lowerLeftY = y0 - py;
			upperLeftX = x0 + px;
			upperLeftY = y0 + py;
			break;
		case Solid.RECTANGLE:
			if (dy < 0) {
				lowerLeftX = x0;
				lowerLeftY = y0;
				upperLeftX = x0 + solid.getWidth();
				upperLeftY = y0 + solid.getHeight();
			}
			else {
				lowerLeftX = x0 + solid.getWidth();
				lowerLeftY = y0;
				upperLeftX = x0;
				upperLeftY = y0 + solid.getHeight();
			}
			break;
		default:	//last case is for a point
			lowerLeftX = x0;
			lowerLeftY = y0;
			upperLeftX = x0;
			upperLeftY = y0;
			break;
		}
		//Iterate per column
		int xEnd = (int) Math.max(lowerLeftX + dx, upperLeftX + dx);
		double slope = dy / dx;
		
		for (int i=(int)(Math.min(lowerLeftX, upperLeftX)); i<=xEnd; i++) {
			/**
			 * We now consider the tile column spanning x = i to (i+1)
			 * We want to find the highest and lowest y values in this
			 * column. Check each of the parallelogram's edges
			 */
			double xMin = Math.max(Math.min(lowerLeftX, upperLeftX), i);
			double xMax = Math.min(Math.max(lowerLeftX, upperLeftX) + dx, i + 1);
			double yMin;
			double yMax;
			if (upperLeftX > lowerLeftX) {
				/**
				 * Shape is like this:
				 *   __
				 *  /_/
				 *  
				 */
				if (upperLeftX < xMin) {
					//Only upper border is top edge
					yMax = upperLeftY + (
							(dy > 0 ? xMax : xMin) - upperLeftX
							) * slope;
				}
				else if (upperLeftX > xMax) {
					//Only upper border is left edge
					yMax = lowerLeftY + (xMax - upperLeftX) *
							(upperLeftY - lowerLeftY) / (upperLeftX - lowerLeftX);
				}
				//Corner is in this column
				else if (dy > 0){
					yMax = upperLeftY + (xMax - upperLeftX) * slope;
				}
				else {
					yMax = upperLeftY;
				}
				if (lowerLeftX + dx < xMin) {
					//Only lower border is right edge
					yMin = upperLeftY + dy + (xMin - (upperLeftX + dx)) *
							(lowerLeftY - upperLeftY) / (lowerLeftX - upperLeftX);
				}
				else if (lowerLeftX + dx > xMax) {
					//Only lower border is bottom edge
					yMin = lowerLeftY + (
							(dy > 0 ? xMin : xMax) - lowerLeftX
							) * slope;
				}
				//Corner is in this column
				else if (dy > 0) {
					yMin = lowerLeftY + (xMin - lowerLeftX) * slope;
				}
				else {
					yMin = lowerLeftY + dy;
				}
			}
			else {
				/**
				 * Shape is like this:
				 *  __
				 *  \_\
				 *  
				 */
				if (upperLeftX + dx < xMin) {
					//Only upper border is right edge
					yMax = lowerLeftY + dy + (xMin - (lowerLeftX + dx)) *
							(upperLeftY - lowerLeftY) / (upperLeftX - lowerLeftX);
				}
				else if (upperLeftX + dx > xMax) {
					//Only upper border is top edge
					yMax = upperLeftY + (
							(dy > 0 ? xMax : xMin) - upperLeftX
							) * slope;
				}
				//Corner is in this column
				else if (dy > 0){
					yMax = upperLeftY + dy;
				}
				else {
					yMax = upperLeftY + (xMin - upperLeftX) * slope;					
				}
				if (lowerLeftX < xMin) {
					//Only lower border is bottom edge
					yMin = lowerLeftY + (
							(dy > 0 ? xMin : xMax) - lowerLeftX
							) * slope;
				}
				else if (lowerLeftX > xMax) {
					//Only lower border is left edge
					yMin = upperLeftY + (xMax - upperLeftX) *
							(lowerLeftY - upperLeftY) / (lowerLeftX - upperLeftX);
				}
				//Corner is in this column
				else if (dy > 0) {
					yMin = lowerLeftY;
				}
				else {
					yMin = lowerLeftY + (xMax - lowerLeftX) * slope;
				}
			}
			for (int j=(int)yMin; j<=yMax; j++) {
				Tile tile = get(i, j);
				if (tile == null || tile.isObstruction()) {
					return false;
				}
				else if (solid.walks() && !tile.isWalkable()) {
					//Unwalkable tile only blocks movement if solid is centered on it
					if (Geometry.lineSegmentIntersectRectangle(x0, y0, dx, dy,
							tile.getX(), tile.getY(), 1, 1)) {
						return false;
					}
				}
				for (Solid s : tile.getSolids()) {
					if (s == solid || s.isMobile() || drawSet.contains(s)) {
						continue;
					}
					if (Geometry.parallelogramIntersectSolid(lowerLeftX, lowerLeftY,
							upperLeftX, upperLeftY, dx, dy, s)){
						return false;
					}
					drawSet.add(s);
				}
			}
		}
		return true;
	}
	private class TileArrayGenerator{
		/**
		 * This class is for tracking tiles which will contain a solid.
		 * They are put into an array for easy checking. Need to remove
		 * old tiles and add new tiles as a solid moves.
		 */
		private int xMax;
		private int xMin;
		private int yMax;
		private int yMin;
		private boolean[][] arr;
		private TileArrayGenerator() {
			xMax = Integer.MIN_VALUE;
			xMin = Integer.MAX_VALUE;
			yMax = Integer.MIN_VALUE;
			yMin = Integer.MAX_VALUE;
		}
		private void update(Tile tile) {
			xMax = Math.max(xMax, tile.getX());
			xMin = Math.min(xMin, tile.getX());
			yMax = Math.max(yMax, tile.getY());
			yMin = Math.min(yMin, tile.getY());
		}
		private void generate() {
			if (xMax < xMin || yMax < yMin) {
				//No data
				arr = null;
			}
			else {
				arr = new boolean[xMax + 1 - xMin][yMax + 1 - yMin];
			}
		}
		private void add(Tile tile) {
			arr[tile.getX() - xMin][tile.getY() - yMin] = true;
		}
		private boolean contains(Tile tile) {
			if (arr == null) {
				return false;
			}
			int x = tile.getX() - xMin;
			if (x < 0 || x >= arr.length) {
				return false;
			}
			int y = tile.getY() - yMin;
			if (y < 0 || y >= arr[x].length) {
				return false;
			}
			return arr[x][y];
		}
	}
//	long total = 0; long count=0;
	/**
	 * getImage returns an image that is width x height, centered on the point
	 * (x, y). The amount of world space drawn is SCREEN_WIDTH_IN_TILES by
	 * SCREEN_HEIGHT_IN_TILES. The image is rotated by angle.
	 */
	public BufferedImage getImage(double x, double y, int width, int height, double angle) {
//		long t0 = System.currentTimeMillis();
		//First we draw an image that is too big, then rotate and crop it.
		final int bigWidth = width * 3 / 2;
		final int bigHeight = height * 3 / 2;
		final double xRatio = 1.0 * width / SCREEN_WIDTH_IN_TILES;
		final double yRatio = 1.0 * height / SCREEN_HEIGHT_IN_TILES;
		final double width075 = width * 0.75;
		final double height075 = height * 0.75;
		Graphics2D g;
		if (bigImage == null || bigImage.getWidth() != bigWidth || bigImage.getHeight() != bigHeight) {
			bigImage = new BufferedImage(bigWidth, bigHeight, BufferedImage.TYPE_INT_RGB);
			g = bigImage.createGraphics();
			g.setBackground(Color.BLACK);
		}
		else {
			//Reuse old image
			g = bigImage.createGraphics();
			g.setColor(Color.BLACK);
			g.fillRect(0, 0, bigWidth, bigHeight);
		}
		Solid player = null;
		
		drawSet.clear();
		List <Solid> solids = new ArrayList <> ();
		List <TriggeredInteraction> triggeredInteractions = new ArrayList <> ();
		//Iterate through game tile columns
		int gameX = (int)(x - SCREEN_WIDTH_IN_TILES_075);
		int screenX = (int)(width075 + (gameX - x) * xRatio);
		while (screenX < bigWidth) {
			//This column spans gameX to (gameX+1) in game units
			//screenX to nextScreenX in screen units
			int nextScreenX = (int)(width075 + (gameX + 1 - x) * xRatio);
			final int columnWidth = nextScreenX - screenX;
			int gameY = (int)(y - SCREEN_HEIGHT_IN_TILES_075);
			//subtract one from gameY because we draw tiles from their top left corner
			int prevScreenY = (int)(height075 - (gameY - (1 + y)) * yRatio);
			while (prevScreenY >= 0) {
				int screenY = (int)(height075 - (gameY - y) * yRatio);
				Tile tile = get(gameX, gameY - 1);
				if (tile != null) {
					for (Solid s : tile.getSolids()) {
						if (s.isPlayer()) {
							player = s;
						}
						else if (drawSet.add(s)) {
							solids.add(s);
						}
					}
					for (TriggeredInteraction i : tile.getTriggeredInteractions()) {
						if (i.getImage() != null && drawSet.add(i)) {
							triggeredInteractions.add(i);
						}
					}
					Image tImage = tile.getScaledImage(columnWidth, prevScreenY - screenY);
					if (tImage != null) {
						/**
						 * Use a scaled instance here because we must be certain of the dimensions.
						 * We do not want rounding error to create gaps between tiles.
						 */
						g.drawImage(tImage, screenX, screenY, null);
					}
				}
				gameY++;
				prevScreenY = screenY;
			}
			gameX++;
			screenX = nextScreenX;
		}
		//Draw the triggered interactions first, and then the solids, so the interactions appear underneath
		for (TriggeredInteraction i : triggeredInteractions) {
			drawImage(i.getX(), i.getY(), i.getDirection(), i.getShape(), i.getRadius(), i.getWidth(), i.getHeight(),
					1, 1, i.getImage(), g, width075, height075, xRatio, yRatio, x, y);
		}
		for (Solid solid : solids) {
			drawImage(solid.getX(), solid.getY(), solid.getDirection(), solid.getShape(), solid.getRadius(), solid.getWidth(), solid.getHeight(),
					solid.getImageScaleX(), solid.getImageScaleY(), solid.getImage(), g, width075, height075, xRatio, yRatio, x, y);
		}
		//Draw the bigImage onto a smaller one of the preferred size
		if (smallImage == null || smallImage.getWidth() != width || smallImage.getHeight() != height) {
			smallImage = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
		}
		transform.setToTranslation(-width*0.25, -height*0.25);
		transform.rotate(angle, width075, height075);
		g.dispose();
		g = smallImage.createGraphics();
		g.drawImage(bigImage, transform, null);
		/**
		 * Draw the player. The world should be rotated relative to the player's
		 * orientation. Thus, the player always faces the same direction,
		 * relative to the screen. No need to rotate it just to rotate back.
		 */
		if (player != null) {
			double xr = player.getRadius() * player.getImageScaleX();
			double yr = player.getRadius() * player.getImageScaleY();
			double cornerX = width*0.5 + ((player.getX() - xr) - x) * xRatio;
			double cornerY = height*0.5 - (player.getY() + yr - y) * yRatio;
			double endX = cornerX + (xr * 2 * xRatio);
			double endY = cornerY + (yr * 2 * yRatio);
			Image pImage = player.getImage().getScaledInstance((int)(endX) - (int)(cornerX), (int)(endY) - (int)(cornerY), Image.SCALE_DEFAULT);
			g.drawImage(pImage, (int)cornerX, (int)cornerY, null);
		}
		g.dispose();
//		long t1 = System.currentTimeMillis();total+=t1-t0;count++;if(count==100) {count=0;System.out.println(total/100.0);total=0;}
		return smallImage;
	}
	private void drawImage(double x, double y, double direction, int shape, double radius, double w, double h,double imageScaleX, double imageScaleY,
			BufferedImage image, Graphics2D g, double width075, double height075, double xRatio, double yRatio, double bigImageX, double bigImageY) {
		double sx;	//center x
		double sy;	//center y
		double xr;	//x radius
		double yr;	//y radius
		switch (shape) {
		case Solid.CIRCLE:
			sx = x;
			sy = y;
			xr = radius;
			yr = radius;
			break;
		case Solid.RECTANGLE:
			xr = w * 0.5;
			yr = h * 0.5;
			sx = x + xr;
			sy = y + yr;
			break;
		case Solid.POINT:
			//Point's width and height are ignored when drawing
			sx = x;
			sy = y;
			xr = 1;
			yr = 1;
			break;
		default:	//what else could it be?
			return;
		}
		xr *= imageScaleX;
		yr *= imageScaleY;
		double cornerX = width075 + ((sx - xr) - bigImageX) * xRatio;
		double cornerY = height075 - (sy + yr - bigImageY) * yRatio;
		double endX = cornerX + (xr * 2 * xRatio);
		double endY = cornerY + (yr * 2 * yRatio);
		int imageWidth = (int)(endX) - (int)(cornerX);
		int imageHeight = (int)(endY) - (int)(cornerY);
		if (direction == 0) {
			//Scaled instance appears faster than affine transform
			Image sImage = image.getScaledInstance(imageWidth, imageHeight, Image.SCALE_DEFAULT);
			g.drawImage(sImage, (int)cornerX, (int)cornerY, null);
		}
		else {
			BufferedImage sImage = image;
			transform.setToTranslation(cornerX, cornerY);
			transform.scale(1.0 * imageWidth / sImage.getWidth(), 1.0 * imageHeight / sImage.getHeight());
			transform.rotate(direction, sImage.getWidth() * 0.5, sImage.getHeight() * 0.5);
			g.drawImage(sImage, transform, null);
		}
	}
	public TileSolid getTileSolid(Tile tile) {
		tileSolid.setTile(tile);
		return tileSolid;
	}
	/**
	 * Gets a node map for the representative solid. Creates one if none
	 * already exist.
	 */
	public NodeMap getNodeMap(Solid representative) {
		if (nodeMaps.isEmpty()) {
			NodeMap nodeMap = new NodeMap(this, representative);
			nodeMaps.add(nodeMap);
			return nodeMap;
		}
		//NodeMaps should be sorted, so do a binary search
		//Sort by solid radius, then by walking (walks comes first)
		int l = 0;
		int r = nodeMaps.size() - 1;
		NodeMap lm = nodeMaps.get(l);
		int comparison = lm.compareTo(representative);
		if (comparison > 0) {
			//Need a new one
			NodeMap nodeMap = new NodeMap(this, representative);
			nodeMaps.add(0, nodeMap);
			return nodeMap;
		}
		else if (comparison == 0) {
			return lm;
		}
		NodeMap rm = nodeMaps.get(r);
		comparison = rm.compareTo(representative);
		if (comparison < 0) {
			//Need a new one
			NodeMap nodeMap = new NodeMap(this, representative);
			nodeMaps.add(nodeMap);
			return nodeMap;
		}
		else if (comparison == 0) {
			return rm;
		}
		while (r > l + 1) {
			int m = (r + l) / 2;
			NodeMap mm = nodeMaps.get(m);
			comparison = mm.compareTo(representative);
			if (comparison == 0) {
				return mm;
			}
			else if (comparison > 0) {
				r = m;
			}
			else {
				l = m;
			}
		}
		NodeMap nodeMap = new NodeMap(this, representative);
		nodeMaps.add(r, nodeMap);
		return nodeMap;
	}
	/**
	 * For path finding. Sets a solid actor's path to be a sequence of
	 * nodes leading to the destination.
	 */
	public void setPath(SolidActor solid, double destinationX, double destinationY) {
		solid.setDestination(destinationX, destinationY);
		solid.clearPath();
		pathFindingNumber++;
		final double sx = solid.getX();
		final double sy = solid.getY();
		//Can we just move directly there?
		if (canMove(solid, sx, sy, destinationX - sx, destinationY - sy)) {
			//Yes. No need for nodes
			return;
		}
		NodeMap nodeMap = getNodeMap(solid);
		//Find nodes connected to current position
		//TO DO: Check for correct path finding number!
		PathNodeHeap heap = new PathNodeHeap();
		for (int i=-1 + (int)(sx); i<=1 + (int)(sx); i++){
			for (int j=-1 + (int)(sy); j<=1 + (int)(sy); j++) {
				for (PathNode node : nodeMap.getNodes(i, j)) {
					double dx = node.x - sx;
					double dy = node.y - sy;
					if (canMove(solid, sx, sy, dx, dy)) {
						node.initialize(destinationX, destinationY, pathFindingNumber);
						node.setDistanceTo(Math.sqrt((dx * dx) + (dy * dy)));
						node.setParent(null);
						heap.push(node);
					}
				}
			}
		}
		//Keep track of the reachable node closest to destination, in case
		//we can't get entirely there
		PathNode closest = null;
		while (!heap.isEmpty()) {
			//Get the best estimate
			PathNode current = heap.pop();
			if (closest == null || current.getDistanceFrom() < closest.getDistanceFrom()) {
				closest = current;
			}
			//Is this close enough to connect to the destination?
			final double cx = current.x;
			final double cy = current.y;
			if (Math.abs((int)(cx)-(int)(destinationX)) <= 1 && 
					Math.abs((int)(cy)-(int)(destinationY)) <= 1 &&
					canMove(solid, cx, cy, destinationX - cx, destinationY - cy)) {
				//Found the last node.
				closest = current;
				break;
			}
			else {
				//Examine connected nodes for unconsidered ones
				for (PathNode neighbor : current.getConnected()) {
					double newDistanceTo = current.getDistanceTo() + current.getDistanceBetween(neighbor);
					if (neighbor.getPathFindingNumber() != pathFindingNumber) {
						//We have not yet considered this one.
						neighbor.initialize(destinationX, destinationY, pathFindingNumber);
						neighbor.setDistanceTo(newDistanceTo);
						neighbor.setParent(current);
						heap.push(neighbor);
					}
					else if (newDistanceTo < neighbor.getDistanceTo()) {
						//This is a shorter path. Update and resift
						neighbor.setDistanceTo(newDistanceTo);
						neighbor.setParent(current);
						heap.resift(neighbor);
					}
				}
			}
		}
		if (closest == null) {
			//Path finding failed
			return;
		}
		/**
		 * Node "closest" is the closest to the destination. Optimize the path
		 * by looking for direct shortcuts.
		 */
		PathNode node = closest;
		while (true) {
			PathNode parent = node.getParent();
			if (parent == null) {
				//Can we move to destination directly from starting point?
				if (!canMove(solid, sx, sy, destinationX - sx, destinationY - sy)) {
					//No; we need node
					solid.push(node);
				}
				break;
			}
			if (!canMove(solid, parent.x, parent.y,
					destinationX - parent.x, destinationY - parent.y)) {
				//We cannot skip "node" so it must be included
				solid.push(node);
				destinationX = node.x;
				destinationY = node.y;
			}
			node = parent;
		}
		//solid's path should now be set. Nothing to return.
	}
	/*
	 * For optimization, we cache scaled tile images. The world keeps a list
	 * of scaled image caches. Each cache is for a tile class. It stores a 2D
	 * array of images, indexed by dimension, and the dimension offsets. 
	 */
	private static class TileImageCache{
		private int widthOffset;
		private int heightOffset;
		private Image[][] images;
		private TileImageCache(Image image, int widthOffset, int heightOffset) {
			/*
			 * When we make a new tile image cache, the given image is put in
			 * the array center. We keep versions that are larger or smaller
			 * by two pixels in each dimension.
			 */
			images = new Image[5][5];
			images[2][2] = image;
			this.widthOffset = widthOffset;
			this.heightOffset = heightOffset;
		}
	}
	public Image getCachedTileImage(int tileTypeNumber, int width, int height) {
		if (tileTypeNumber >= cachedTileImageList.size()) {
			return null;
		}
		TileImageCache cache = cachedTileImageList.get(tileTypeNumber);
		int w = width - cache.widthOffset;
		int h = height - cache.heightOffset;
		if (w > 4 || w < 0 || h > 4 || h < 0) {
			//Current cache does not contain something of those dimensions
			return null;
		}
		return cache.images[w][h];
	}
	public void setCachedTileImage(int tileTypeNumber, Image image, int width, int height) {
		//make space if necessary
		while (cachedTileImageList.size() <= tileTypeNumber) {
			cachedTileImageList.add(null);
		}
		TileImageCache cache = cachedTileImageList.get(tileTypeNumber);
		if (cache == null) {
			//No cache yet, so start a new one.
			cache = new TileImageCache(image, width - 2, height - 2);
			cachedTileImageList.set(tileTypeNumber, cache);
		}
		else {
			int w = width - cache.widthOffset;
			int h = height - cache.heightOffset;
			if (w < 0 || w > 4 || h < 0 || h > 4) {
				//Previous cache was for wrong dimensions
				cache.widthOffset = width - 2;
				cache.heightOffset = height - 2;
				cache.images = new Image[5][5];
				cache.images[2][2] = image;
			}
			else {
				cache.images[w][h] = image;
			}
		}
	}
}
