package antworld.client;

import antworld.common.LandType;
import antworld.server.Cell;

/**
 * Instead of calculating a color and drawing paths on the actual map, we can store those values in the cells
 * Ex: food proximity, enemy proximity, enemy nest proximity, etc.
 * Created by John on 11/25/2016.
 */
public class MapCell extends Cell
{

  private int foodProximityVal;


  public MapCell(LandType landType, int height, int x, int y)
  {
    super(landType,height,x,y);
    foodProximityVal = 0;
  }

  public int getFoodProximityVal()
  {
    return this.foodProximityVal;
  }

  public void setFoodProximityVal(int newVal)
  {
    this.foodProximityVal = newVal;
  }
}
