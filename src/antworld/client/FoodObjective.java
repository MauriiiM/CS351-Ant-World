package antworld.client;

import antworld.client.navigation.Path;
import antworld.common.FoodData;

import java.util.ArrayList;

/**
 * A food objective references a food site and has a collection of ants allocated to it.
 * Used by FoodManager to efficiently allocate ants to a food site
 * Created by John on 12/1/2016.
 */
public class FoodObjective extends Objective
{
  private FoodData foodSite;    //Could this give a null pointer exception once all the food has been collected?
  private ArrayList<AntGroup> allocatedGroups;
  private volatile int foodLeft;
  private Path pathToNest = null;

  public FoodObjective(FoodData foodSite, Path pathToNest)
  {
    this.foodSite = foodSite;
    this.objectiveX = foodSite.gridX;
    this.objectiveY = foodSite.gridY;
    foodLeft = foodSite.getCount();
    allocatedGroups = new ArrayList<>();
    this.pathToNest = pathToNest;
  }

  public void allocateGroup(AntGroup group)
  {
    allocatedGroups.add(group);
    group.setGroupObjective(this);
  }

  public void reduceFoodLeft(int amount)
  {
    foodLeft = foodLeft - amount;
    if(foodLeft <= 0 && !completed)
    {
      foodLeft = 0;
      completed = true;
    }
  }

  public int getFoodLeft()
  {
    return foodLeft;
  }

  public void unallocateGroup(AntGroup group)
  {
    allocatedGroups.remove(group);
  }

  public FoodData getFoodData()
  {
    return foodSite;
  }

  public void setFoodData(FoodData newData)
  {
    this.foodSite = newData;
  }

  public ArrayList<AntGroup> getAllocatedAnts()
  {
    return allocatedGroups;
  }

  public Path getPathToNest()
  {
    return pathToNest;
  }
}
