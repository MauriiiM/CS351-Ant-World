package antworld.client;

import antworld.client.navigation.Path;
import antworld.common.FoodData;

import java.util.ArrayList;

/**
 * A food objective references a food site and has a collection of ants allocated to it.
 * Used by FoodManager to efficiently allocate ants to a food site
 * Created by John on 12/1/2016.
 */
public class FoodObjective
{
  private FoodData foodSite;    //Could this give a null pointer exception once all the food has been collected?
  private ArrayList<Ant> allocatedAnts;
  private int objectiveX = 0;
  private int objectiveY = 0;
  private volatile int foodLeft;
  private Path pathToNest = null;

  public FoodObjective(FoodData foodSite, Path pathToNest)
  {
    this.foodSite = foodSite;
    objectiveX = foodSite.gridX;
    objectiveY = foodSite.gridY;
    foodLeft = foodSite.getCount();
    allocatedAnts = new ArrayList<>();
    this.pathToNest = pathToNest;
  }

  public void allocateAnt(Ant ant)
  {
    allocatedAnts.add(ant);
    ant.setFoodObjective(this);
  }

  public void reduceFoodLeft(int amount)
  {
    foodLeft = foodLeft - amount;
    if(foodLeft < 0)
    {
      foodLeft = 0;
    }
  }

  public int getFoodLeft()
  {
    return foodLeft;
  }

  public void unallocateAnt(Ant ant)
  {
    allocatedAnts.remove(ant);
  }

  public FoodData getFoodData()
  {
    return foodSite;
  }

  public void setFoodData(FoodData newData)
  {
    this.foodSite = newData;
  }

  public ArrayList<Ant> getAllocatedAnts()
  {
    return allocatedAnts;
  }

  public int getObjectiveX()
  {
    return objectiveX;
  }

  public int getObjectiveY()
  {
    return objectiveY;
  }

  public Path getPathToNest()
  {
    return pathToNest;
  }
}
