package tile;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;

public class TiledFloor extends Land{
	private static BufferedImage img = null;
	public TiledFloor(int x, int y) {
		super(x, y);
	}
	public BufferedImage getImage() {
		if (img == null) {
			img = new BufferedImage(50, 50, BufferedImage.TYPE_INT_RGB);
			Color tileColor = Color.GRAY;
			Color lightEdgeColor = Color.LIGHT_GRAY;
			Color darkEdgeColor = Color.DARK_GRAY;
			Graphics2D g = img.createGraphics();
			g.setColor(tileColor);
			g.fillRect(0, 0, img.getWidth(), img.getHeight());
			g.setColor(darkEdgeColor);
			g.fillRect(0, img.getHeight() - 3, img.getWidth(), 2);
			g.fillRect(img.getWidth() - 3, 0, 2, img.getHeight());
			g.setColor(lightEdgeColor);
			g.fillRect(0, 0, img.getWidth(), 2);
			g.fillRect(0, 0, 2, img.getHeight() - 2);
			g.dispose();
		}
		return img;
	}
	@Override
	protected int getTileTypeNumber() {
		return 2;
	}
}
