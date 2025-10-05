package tile;

public class Water extends Tile{

	public Water(int x, int y) {
		super(x, y);
	}
	public boolean isObstruction() {
		return false;
	}
}
