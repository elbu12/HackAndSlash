package tile;

import java.awt.Color;
import java.awt.Graphics;
import java.awt.image.BufferedImage;

public class Stone extends Tile{
	private static BufferedImage img = null;

	public Stone(int x, int y) {
		super(x, y);
	}
	public BufferedImage getImage() {
		if (img == null) {
			img = new BufferedImage(1, 1, BufferedImage.TYPE_INT_RGB);
			Graphics g = img.getGraphics();
			g.setColor(Color.DARK_GRAY);
			g.fillRect(0, 0, 1, 1);
			g.dispose();
		}
		return img;
	}
	@Override
	protected int getTileTypeNumber() {
		return 1;
	}
}
