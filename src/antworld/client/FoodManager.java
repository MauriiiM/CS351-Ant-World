package antworld.client;

import antworld.client.navigation.Path;
import antworld.client.navigation.PathFinder;
import antworld.common.AntData;
import antworld.common.FoodData;
import antworld.common.Util;

import java.util.*;

import static antworld.client.Ant.mapManager;

/**
 * Manages food sites and directs the collection of food
 * Created by John on 12/1/2016.
 */
public class FoodManager extends Thread
{
  //private ArrayList<AntData> antList;         //Work out how to share antList...
  private ArrayList<FoodObjective> foodObjectives;
  private HashSet<FoodData> foodSites;      //This will be the only thing touched by other threads
  private PriorityQueue<FoodObjective> unprocessedFoodObjectives;
  private PathFinder pathFinder;
  private final int OPTIMALCARRYCAPACITY = 100;  //The optimal amount of food for an ant to carry to the nest
  private final int FOODGRADIENTRADIUS = 30;    //The radius of food gradients
  //private final int MINFOODRESPONSE = 200;
  private volatile boolean running = true;
  private HashMap<Integer, Ant> antMap;     //This doesn't work because the antData is constantly changing

  /**
   * @todo When a food objective is found a path should be found from the food site back to the nest for all ants to follow after they have collected food
   * @todo DEFINITELY need to check out concurrency issues and probably synchronize some variables
   */

  public FoodManager(HashMap<Integer, Ant> antMap, PathFinder pathFinder)
  {
    this.antMap = antMap;
    this.pathFinder = pathFinder;
    FoodObjectiveComparator foodComparator = new FoodObjectiveComparator();
    unprocessedFoodObjectives = new PriorityQueue<>(foodComparator);
    foodSites = new HashSet<>();
    foodObjectives = new ArrayList<>();
  }

  //Reads a foodSet and updates the manager's own set of foodsites
  public void readFoodSet(FoodData[] foodArray)
  {
    synchronized (foodSites)
    {
      if (foodArray.length > 0) {
        FoodData nextFood;
        FoodData nextSite;
        int foodX;
        int foodY;
        int siteX;
        int siteY;

        for (int i = 0; i < foodArray.length; i++)  //Iterate over the food set from the server
        {
          nextFood = foodArray[i];
          foodX = nextFood.gridX;
          foodY = nextFood.gridY;

          if(foodSites.size() == 0)
          {
            String foodData = nextFood.toString();
            foodSites.add(nextFood);
            mapManager.updateCellFoodProximity(foodX, foodY);
            System.err.println("First Site: FoodManager: Found Food @ (" + foodX + "," + foodY + ") : " + foodData);
          }

          for (Iterator<FoodData> j = foodSites.iterator(); j.hasNext(); )  //Iterate over our own set of food sites
          {
            nextSite = j.next();    //GETTING CONCURRENTMODIFICATIONEXCEPTION HERE!!!! WHEN A SECOND FOOD SITE IS FOUND
            siteX = nextSite.gridX;
            siteY = nextSite.gridY;

            if(siteX != foodX || siteY != foodY)  //If the coords don't match, must be a new site
            {
              String foodData = nextFood.toString();
              foodSites.add(nextFood);
              mapManager.updateCellFoodProximity(foodX, foodY);
              System.err.println("Second Site: FoodManager: Found Food @ (" + foodX + "," + foodY + ") : " + foodData);
              System.err.println("\t foodSite size = " + foodSites.size());
            }
          }
        }
      }
    }
  }

  /**
   * @todo Still not sure what to do about this in terms of synchronization....
   */
  /*
  public void setAntList(ArrayList<AntData> newAntList)  //Will this become null once the antlist is sent to null and sent back to the server?
  {
    this.antList = newAntList;
  }
  */

  private void addFoodObjective(FoodData foodSite)
  {
    FoodObjective newObjective = new FoodObjective(foodSite);
    foodObjectives.add(newObjective);   //Add new objective to collection of food objectives
    unprocessedFoodObjectives.add(newObjective);
    //System.err.println("FoodManager added: " + foodSite.toString() + " || to foodObjectives and unprocessed!");
    //System.err.println("foodObjectives size = " + foodObjectives.size());
    //System.err.println("uprocessed size= " + unprocessedFoodObjectives.size());
  }

  //Returns the number of ants required to carry all of the food from a food site back to the nest
  private int antsRequiredForFoodSite(int foodQuantity)
  {
    return (foodQuantity/OPTIMALCARRYCAPACITY)+1;
  }

  //Decides which ants to allocate to a food site
  private void allocateAntsToFoodSite(FoodObjective foodObjective)
  {
    System.err.println("allocateAntsToFoodSite()...");
    System.err.println("Food Objective: " + foodObjective.getFoodData().toString());

    FoodData foodData = foodObjective.getFoodData();
    int foodX = foodObjective.getObjectiveX();
    int foodY = foodObjective.getObjectiveY();
    int antsRequired = antsRequiredForFoodSite(foodData.getCount());
    int antsAllocated = foodObjective.getAllocatedAnts().size();

    System.err.println("foodX = " + foodX + " | foodY = " + foodY + " | antsRequired = " + antsRequired + " | antsAllocated = " + antsAllocated);

    AntComparator comparator = new AntComparator(foodX, foodY);
    PriorityQueue<Ant> antQueue = new PriorityQueue<>(comparator);

    for (Integer antData : antMap.keySet())
    {
      Ant ant = antMap.get(antData);
      if(ant.getCurrentGoal() != Goal.RETURNTONEST) //If the ant was not already returning to the nest for a reason/ant is available to go collect food
      {
        antQueue.add(ant);
      }
    }

    while(antsAllocated < antsRequired && antQueue.size() > 0)
    {
      System.err.println("Allocating ants to food objective....");
      Ant nextAnt = (Ant) antQueue.poll();  //Add ant to foodObjective
      if(nextAnt.getCurrentGoal() == Goal.GOTOFOODSITE) //If the ant is already heading to a food site
      {
        FoodObjective bestObjective = compareFoodSites(nextAnt, nextAnt.getFoodObjective(), foodObjective);
        if(bestObjective == foodObjective)  //If this objective is the best objective for the nextAnt, reroute it to this objective
        {
          FoodObjective previousObjective = nextAnt.getFoodObjective();
          previousObjective.unallocateAnt(nextAnt);  //Unallocate ant from previous food objective
          foodObjective.allocateAnt(nextAnt);      //Allocate ant to new food objective
          nextAnt.setPath(generatePathToFood(nextAnt.getAntData(),foodData));  //Give the ant a path to the new food objective
          unprocessedFoodObjectives.add(previousObjective);   //Add previous objective to priority queue so a new ant gets allocated to it later
          antsAllocated ++;
        }
      }
      else
      {
        System.err.println("ALLOCATING: " + nextAnt.getAntData().toString());
        nextAnt.setCurrentGoal(Goal.GOTOFOODSITE);
        nextAnt.setPath(generatePathToFood(nextAnt.getAntData(), foodData));
        foodObjective.allocateAnt(nextAnt);
        antsAllocated++;
      }
    }
  }

  //Compares two food sites and returns the most favorable one for an ant to pursue
  private FoodObjective compareFoodSites(Ant ant, FoodObjective currentObjective, FoodObjective otherObjective)
  {
    if(currentObjective != null && otherObjective != null)
    {
      int antX = ant.getAntData().gridX;
      int antY = ant.getAntData().gridY;
      int currentX = currentObjective.getObjectiveX();
      int currentY = currentObjective.getObjectiveY();
      int otherX = currentObjective.getObjectiveX();
      int otherY = currentObjective.getObjectiveY();
      int currentDistance = Util.manhattanDistance(antX,antY,currentX,currentY);
      int otherDistance = Util.manhattanDistance(antX,antY,otherX,otherY);

      if(currentDistance >= otherDistance)  //If the current food objective is just as good or better, maintain current objective
      {
        return  currentObjective;
      }
      else
      {
        return otherObjective;
      }
    }
    else
    {
      if(currentObjective == null)
      {
        return otherObjective;
      }
      else
      {
        return currentObjective;
      }
    }
  }

  //Generates a path to a food site for an ant to follow
  private Path generatePathToFood(AntData ant, FoodData food)
  {
    int antX = ant.gridX;
    int antY = ant.gridY;
    int foodX = food.gridX;
    int foodY = food.gridY;
    int deltaX = Math.abs(antX - foodX);
    int deltaY = Math.abs(antY - foodY);
    int targetX=0;
    int targetY=0;

    if(deltaX > 0 && deltaY > 0)
    {
      double angleToFood = Math.atan(deltaY/deltaX);
      double targetDeltaX = Math.cos(angleToFood)*FOODGRADIENTRADIUS;
      double targetDeltaY = Math.sin(angleToFood)*FOODGRADIENTRADIUS;

      if(antX > foodX)  //Ant is East of food
      {
        targetX = foodX + (int) targetDeltaX;
      }
      else    //Ant is West of food
      {
        targetX = foodX - (int) targetDeltaX;
      }

      if(antY > foodY)  //Ant is South of food
      {
        targetY = foodY + (int) targetDeltaY;
      }
      else  //Ant is North of food
      {
        targetY = foodY - (int) targetDeltaY;
      }
    }
    else if(deltaX == 0)
    {
      targetX = foodX;
      if(antY > foodY)  //Ant is directly South of food
      {
        targetY = foodY + 29;
      }
      else    //Ant is directly North of food
      {
        targetY = foodY - 29;
      }
    }
    else if(deltaY == 0)
    {
      targetY = foodY;
      if(antX > foodX)  //Ant is directly East of food
      {
        targetX = foodX + 29;
      }
      else    //Ant is directly West of food
      {
        targetX = foodX - 29;
      }
    }

    return pathFinder.findPath(targetX,targetY,antX,antY);
  }

  @Override
  public void run()
  {
    while(running)
    {
      synchronized (foodSites)
      {
        if(foodSites.size() > foodObjectives.size())  //If there's a new food site, add it to the objectives and process it
        {
          FoodData nextFood;
          boolean newSite;
          for (Iterator<FoodData> i = foodSites.iterator(); i.hasNext(); )
          {
            nextFood = i.next();
            newSite = true;
            for (FoodObjective objective : foodObjectives)
            {
              if (objective.getFoodData() == nextFood) //This food site has already been stored as an objective
              {
                newSite = false; 
              }
            }

            if (newSite) //If this site has not been stored as an objective, make a new one, store it.
            {
              addFoodObjective(nextFood);
            }
          }
        }
      }

      if(unprocessedFoodObjectives.size() > 0)
      {
        FoodObjective nextFoodObjective = unprocessedFoodObjectives.poll();
        allocateAntsToFoodSite(nextFoodObjective);   //Allocate ants to the new food objective
      }
    }
  }

  private class FoodObjectiveComparator implements Comparator<FoodObjective>
  {
    //***********************************
    //The comparator compares food sites according to their quantity
    //This method returns zero if the objects are equal.
    //It returns a negative value if food site 1 has more food than food site 2. Otherwise, a positive value is returned.
    //***********************************
    @Override
    public int compare(FoodObjective objective1, FoodObjective objective2)
    {
      int objectiveCount1 = objective1.getFoodData().getCount();
      int objectiveCount2 = objective2.getFoodData().getCount();

      if(objectiveCount1 > objectiveCount2)
      {
        return -1;
      }
      else if(objectiveCount1 < objectiveCount2)
      {
        return 1;
      }
      return 0;
    }
  }

  //Compares ants based on their proximity to a food site
  private class AntComparator implements Comparator<Ant>
  {
    private int foodX;
    private int foodY;
    private AntComparator(int foodX, int foodY)
    {
      this.foodX = foodX;
      this.foodY = foodY;
    }
    //***********************************
    //The comparator compares ants based on their proximity to a food site
    //This method returns zero if the objects are equal.
    //It returns a negative value if ant1 is closer than ant2. Otherwise, a positive value is returned.
    //***********************************
    @Override
    public int compare(Ant ant1, Ant ant2) {

      int ant1Distance = Util.manhattanDistance(ant1.getAntData().gridX, ant1.getAntData().gridY, foodX, foodY);
      int ant2Distance = Util.manhattanDistance(ant2.getAntData().gridX, ant2.getAntData().gridY, foodX, foodY);

      if(ant1Distance < ant2Distance)
      {
        return -1;
      }
      else if(ant1Distance > ant2Distance)
      {
        return 1;
      }
      return 0;
    }
  }
}
