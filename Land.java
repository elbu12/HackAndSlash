package tile;

import java.util.ArrayList;
import java.util.List;

import solid.Solid;

public class Land extends Tile{
	private List <Solid> solids;
	public Land(int x, int y) {
		super(x, y);
		solids = new ArrayList <> ();
	}
	//Returns false if solid is already contained
	public boolean add(Solid solid) {
		if (solids.contains(solid)) {
			return false;
		}
		solids.add(solid);
		return true;
	}
	//Returns false if solid is not already contained
	public boolean remove(Solid solid) {
		for (int i=0; i<solids.size(); i++) {
			if (solids.get(i) == solid) {
				if (i == solids.size() - 1) {
					solids.remove(i);
				}
				else {
					//Replace with last element
					solids.set(i, solids.remove(solids.size() - 1));
				}
				return true;
			}
		}
		return false;
	}
	public List <Solid> getSolids(){
		return solids;
	}
	public boolean isWalkable() {
		return true;
	}
	public boolean isObstruction() {
		return false;
	}
}
