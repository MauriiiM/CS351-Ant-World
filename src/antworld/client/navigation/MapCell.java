package antworld.client.navigation;

import antworld.common.LandType;
import antworld.server.Cell;

/**
 * Instead of calculating a color and drawing paths on the actual map, we can store those values in the cells
 * Ex: food proximity, enemy proximity, enemy nest proximity, etc.
 * Created by John on 11/25/2016.
 */
public class MapCell
{

  private boolean occupied = false; //True if currently occupied by an ant
  private int foodProximityVal; //How close this cell is to food
  private int explorationVal; //Has this cell been explored or has it been a while since it was explored?
  private int pathVal;  //How close is this cell to the start of a path
  private int enemyVal;
  private final int height;
  private final int x, y;
  private LandType landType;


  public MapCell(LandType landType, int height, int x, int y)
  {
    foodProximityVal = 0;
    this.landType = landType;
    this.height = height;
    this.x = x;
    this.y = y;

    if(landType == LandType.WATER || landType == LandType.NEST)
    {
      if(landType == LandType.WATER)
      {
        this.explorationVal = -10;
      }
      else
      {
        this.explorationVal = 0;
      }
      this.enemyVal = -1000;
    }
    else
    {
      this.explorationVal = 1000;
      this.enemyVal = 0;
    }
  }
  public void setLandType(LandType landType)
  { this.landType = landType;}
  public LandType getLandType() {return landType;}
  public int getHeight() {return height;}
  public int getLocationX() {return x;}
  public int getLocationY() {return y;}

  public int getEnemyVal()
  {
    return this.enemyVal;
  }

  public void setEnemyVal(int newVal)
  {
    enemyVal = newVal;
  }

  public boolean getOccupied()
  {
    return  occupied;
  }

  public void setOccupied(boolean newState)
  {
    occupied = newState;
  }

  public int getExplorationVal()
  {
    if(occupied)
    {
      return 0;
    }
    return this.explorationVal;
  }

  public void setExplorationVal(int newVal)
  {
    this.explorationVal = newVal;
  }

  public int getFoodProximityVal()
  {
    if(occupied)
    {
      return 0;
    }
    return this.foodProximityVal;
  }

  public void setFoodProximityVal(int newVal)
  {
    this.foodProximityVal = newVal;
  }

  public int getPathVal()
  {
    if(occupied)
    {
      return 0;
    }
    return this.pathVal;
  }

  public void setPathVal(int newVal)
  {
    this.pathVal = newVal;
  }
}
