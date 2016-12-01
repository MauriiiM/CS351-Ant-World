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

  private int foodProximityVal; //How close this cell is to food
  private int explorationVal; //Has this cell been explored or has it been a while since it was explored?


  public MapCell(LandType landType, int height, int x, int y)
  {
    super(landType,height,x,y);
    foodProximityVal = 0;
    if(landType == LandType.WATER || landType == LandType.NEST)
    {
      this.explorationVal = 0;
    }
    else
    {
      this.explorationVal = 1000;
    }

  }

  public int getExplorationVal()
  {
    return this.explorationVal;
  }

  public void setExplorationVal(int newVal)
  {
    this.explorationVal = newVal;
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
