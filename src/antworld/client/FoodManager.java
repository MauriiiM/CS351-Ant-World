package antworld.client;

import antworld.client.navigation.Coordinate;
import antworld.client.navigation.Path;
import antworld.client.navigation.PathFinder;
import antworld.common.AntData;
import antworld.common.FoodData;

import java.util.*;

import static antworld.client.Ant.mapManager;

/**
 * Manages food sites and directs the collection of food
 * (except water, which is done independently if needed when an ant heals~ Mauricio)
 * Created by John on 12/1/2016.
 */
public class FoodManager extends Thread
{
  private ArrayList<FoodObjective> foodObjectives;
  private FoodData[] serverFoodSetCopy;
  private PriorityQueue<FoodObjective> unprocessedFoodObjectives;
  private PathFinder pathFinder;
  public static final int OPTIMALCARRYCAPACITY = 25;  //The optimal amount of food for an ant to carry to the nest
  private final int FOODGRADIENTRADIUS = 30;    //The radius of food gradients
  private final int FOODGRADIENTHYPOTENUSE = (FOODGRADIENTRADIUS*2)/3;  //The hypotenuse for distance calculations 3/2 approx = sqrt(2)
  private final int FOODRESPONSEBUFFER = 400;  //How much of a buffer should be added when calculated the max food response. Ex: 10 cell radius past nest
  private volatile boolean running = true;
  private volatile boolean readFoodSet = false;
  private HashMap<Integer, AntGroup> groupMap;

  public FoodManager(HashMap<Integer, AntGroup> groupMap, PathFinder pathFinder)
  {
    this.groupMap = groupMap;
    this.pathFinder = pathFinder;
    FoodObjectiveComparator foodComparator = new FoodObjectiveComparator();
    unprocessedFoodObjectives = new PriorityQueue<>(foodComparator);
    serverFoodSetCopy = new FoodData[0];  //Initialize an empty array to synchronize on the first time its pointer is switched
    foodObjectives = new ArrayList<>();
  }

  public synchronized FoodData[] getServerFoodSetCopy()
  {
    return serverFoodSetCopy;
  }

  public void setFoodSet(FoodData[] newServerFoodSet)
  {
    synchronized (serverFoodSetCopy)  //Get a lock on food manager's copy
    {
      serverFoodSetCopy = newServerFoodSet; //Switch pointer
    }
    readFoodSet = true; //Mark new server food set to be read
  }

  //Reads a foodSet and updates the manager's own set of foodsites
  private void readFoodSet(FoodData[] foodArray)
  {
    FoodData nextServerSite;
    int serverSiteX;
    int serverSiteY;

    FoodObjective nextStoredSite;
    FoodData nextStoredSiteData;
    int storedSiteX;
    int storedSiteY;

    for (int i = 0; i < foodArray.length; i++)  //Iterate over the food array from the server
    {
      nextServerSite = foodArray[i];
      serverSiteX = nextServerSite.gridX;
      serverSiteY = nextServerSite.gridY;

      if (foodObjectives.size() == 0)
      {
        addFoodObjective(nextServerSite);   //Add the objective
        mapManager.updateCellFoodProximity(serverSiteX, serverSiteY); //Create a diffusion gradient around the food site
        String foodData = nextServerSite.toString();
      }
      else
      {
        boolean newSite = true;

        for (Iterator<FoodObjective> iterator = foodObjectives.iterator(); iterator.hasNext(); )
        {
          nextStoredSite = iterator.next();
          nextStoredSiteData = nextStoredSite.getFoodData();
          storedSiteX = nextStoredSiteData.gridX;
          storedSiteY = nextStoredSiteData.gridY;


          if(nextStoredSite.completed)
          {

            if (nextStoredSite.getAllocatedAnts().size() == 0)
            {
              iterator.remove();
              mapManager.removeFoodProximityGradient(storedSiteX, storedSiteY);
              Coordinate pathToNestStart = nextStoredSite.getPathToNest().getPathStart();
              mapManager.removePathProximityGradient(pathToNestStart.getX(), pathToNestStart.getY());
            }

          }
          else  //This foodsite still has food, make sure it has enough ants allocated to it, if not add to unprocessed queue
          {
            int storedSiteFoodLeft = nextStoredSite.getFoodLeft();
            int groupsRequired = groupsRequiredForFoodSite(storedSiteFoodLeft);
            if(groupsRequired > nextStoredSite.getAllocatedAnts().size() && !unprocessedFoodObjectives.contains(nextStoredSite))
            {
              unprocessedFoodObjectives.add(nextStoredSite);
            }
          }

          if (storedSiteX == serverSiteX && storedSiteY == serverSiteY)  //If the coords match, must be an old site
          {
            nextStoredSite.setFoodData(nextServerSite); //Update existing site FoodData
            newSite = false;
          }
        }

        if (newSite)
        {
          addFoodObjective(nextServerSite);   //Add the objective
          mapManager.updateCellFoodProximity(serverSiteX, serverSiteY); //Create a diffusion gradient around the food site
        }
      }
    }
  }

  private void addFoodObjective(FoodData foodSite)
  {
    Coordinate pathStart = getPathToNestStart(foodSite.gridX, foodSite.gridY);
    Path pathToNest = pathFinder.findPath(pathStart.getX(), pathStart.getY(), NestManager.NESTX, NestManager.NESTY);
    mapManager.createPathStartGradient(pathStart.getX(), pathStart.getY());
    FoodObjective newObjective = new FoodObjective(foodSite, pathToNest);
    foodObjectives.add(newObjective);   //Add new objective to collection of food objectives
    unprocessedFoodObjectives.add(newObjective);
  }

  private Coordinate getPathToNestStart(int foodX, int foodY)
  {
    int nestX = NestManager.NESTX;
    int nestY = NestManager.NESTY;
    int pathStartX;
    int pathStartY;

    if (foodX > nestX) //Food site is East of the nest
    {
      pathStartX = foodX - 1;
    }
    else if (foodX == nestX) //Food site is directly North/South of the nest
    {
      pathStartX = foodX;
    }
    else  //Food site is West of the nest
    {
      pathStartX = foodX + 1;
    }

    if (foodY > nestY) //Food site is South of the nest
    {
      pathStartY = foodY - 1;
    }
    else if (foodY == nestY) //Food site is directly East/West of the nest
    {
      pathStartY = foodY;
    }
    else  //Food site is North of the nest
    {
      pathStartY = foodY + 1;
    }

    return new Coordinate(pathStartX, pathStartY);
  }

  //Returns the number of ants required to carry all of the food from a food site back to the nest
  private int groupsRequiredForFoodSite(int foodQuantity)
  {
    int groupCarryCapacity = AntGroup.ANTS_PER_GROUP*OPTIMALCARRYCAPACITY;
    return ((foodQuantity-4)/groupCarryCapacity)+1;
  }

  //Decides which ants to allocate to a food site
  private void allocateGroupsToFoodSite(FoodObjective foodObjective)
  {
    FoodData foodData = foodObjective.getFoodData();
    int foodX = foodObjective.getObjectiveX();
    int foodY = foodObjective.getObjectiveY();
    int groupsRequired = groupsRequiredForFoodSite(foodObjective.getFoodLeft());
    int groupsAllocated = foodObjective.getAllocatedAnts().size();
    GroupComparator comparator = new GroupComparator(foodX, foodY);
    PriorityQueue<AntGroup> groupQueue = new PriorityQueue<>(comparator);
    int maxFoodResponseDistance = NestManager.calculateDistance(foodX,foodY,NestManager.NESTX,NestManager.NESTY) + FOODRESPONSEBUFFER;

    for (Integer id : groupMap.keySet())
    {
      AntGroup antGroup = groupMap.get(id);                                                 //Need to make sure that the ant is not underground either!
      if(antGroup.getGroupGoal() == Goal.EXPLORE || antGroup.getGroupGoal() == Goal.GOTOFOODSITE || antGroup.getGroupGoal() == Goal.ATTACK) //If the ant was not already returning to the nest for a reason/ant is available to go collect food
      {
        if(!antGroup.underground)
          groupQueue.add(antGroup);
      }
    }

    while(groupsAllocated < groupsRequired && groupQueue.size() > 0)
    {
      AntGroup nextGroup = groupQueue.poll();  //Add ant to foodObjective
      AntData nextAntData = nextGroup.getGroupLeader().getAntData();
      int distanceToFood = NestManager.calculateDistance(foodX,foodY,nextAntData.gridX,nextAntData.gridY);
      if (distanceToFood <= maxFoodResponseDistance)   //If an ant is within the range of the food objective
      {
        if(nextGroup.getGroupGoal() == Goal.GOTOFOODSITE && nextGroup.getGroupObjective() != foodObjective) //If the ant is already heading to a food site
        {
          FoodObjective bestObjective = compareFoodSites(nextGroup, (FoodObjective) nextGroup.getGroupObjective(), foodObjective);
          if(bestObjective == foodObjective)  //If this objective is the best objective for the nextAnt, reroute it to this objective
          {
            FoodObjective previousObjective = (FoodObjective) nextGroup.getGroupObjective();
            previousObjective.unallocateGroup(nextGroup);  //Unallocate ant from previous food objective
            allocateGroup(nextGroup,foodObjective,foodData,foodX,foodY);
            if(!unprocessedFoodObjectives.contains(previousObjective))  //Add the previous objective if it's not already in the queue
            {
              unprocessedFoodObjectives.add(previousObjective);   //Add previous objective to priority queue so a new ant gets allocated to it later
            }
            groupsAllocated ++;
          }
        }
        else if(nextGroup.getGroupGoal() != Goal.GOTOFOODSITE)
        {
          allocateGroup(nextGroup,foodObjective,foodData,foodX,foodY);
          groupsAllocated++;
        }
      }

      if(groupsAllocated == groupsRequired)
      {
        unprocessedFoodObjectives.remove(foodObjective);
      }
    }
  }

  private void allocateGroup(AntGroup group, FoodObjective foodObjective, FoodData foodData, int foodX, int foodY)
  {
    group.setGroupGoal(Goal.GOTOFOODSITE);
    foodObjective.allocateGroup(group);      //Allocate ant to new food objective
    AntData antData = group.getGroupLeader().getAntData();
    int distanceToFoodSite = NestManager.calculateDistance(antData.gridX,antData.gridY,foodX,foodY);
    if(distanceToFoodSite >= FOODGRADIENTRADIUS-1)
    {
      group.setPath(generatePathToFood(group.getGroupLeader().getAntData(), foodData));
    }
    else
    {
      group.endPath();
    }
  }

  /**
   * todo has not actually been tested yet!
   * @param currentObjective
   * @param otherObjective
   * @return
   */
  //Compares two food sites and returns the most favorable one for an ant to pursue
  private FoodObjective compareFoodSites(AntGroup group, FoodObjective currentObjective, FoodObjective otherObjective)
  {
    if (currentObjective != null && otherObjective != null)
    {
      int groupX = group.getGroupLeader().getAntData().gridX;
      int groupY = group.getGroupLeader().getAntData().gridY;
      int currentX = currentObjective.getObjectiveX();
      int currentY = currentObjective.getObjectiveY();
      int otherX = otherObjective.getObjectiveX();
      int otherY = otherObjective.getObjectiveY();
      int currentDistance = NestManager.calculateDistance(groupX,groupY,currentX,currentY);
      int otherDistance = NestManager.calculateDistance(groupX,groupY,otherX,otherY);

      if (currentDistance <= otherDistance)  //If the current food objective is just as good or better, maintain current objective
      {
        return currentObjective;
      }
      else
      {
        return otherObjective;
      }
    }
    else
    {
      if (currentObjective == null)
      {
        return otherObjective;
      }
      else
      {
        return currentObjective;
      }
    }
  }

  /**
   * todo check the trig on the angle! Saw an ant go past the food to a different point on the radius!
   *
   * @param ant
   * @param food
   * @return
   */
  //Generates a path to a food site for an ant to follow
  private Path generatePathToFood(AntData ant, FoodData food)
  {
    int antX = ant.gridX;
    int antY = ant.gridY;
    int foodX = food.gridX;
    int foodY = food.gridY;
    int deltaX = Math.abs(antX - foodX);
    int deltaY = Math.abs(antY - foodY);
    int targetX = 0;
    int targetY = 0;

    if (deltaX > 0 && deltaY > 0)
    {
      double angleToFood = Math.atan(deltaY / deltaX);
      double targetDeltaX = Math.cos(angleToFood) * FOODGRADIENTHYPOTENUSE;
      double targetDeltaY = Math.sin(angleToFood) * FOODGRADIENTHYPOTENUSE;

      if (antX > foodX)  //Ant is East of food
      {
        targetX = foodX + (int) targetDeltaX;
      }
      else    //Ant is West of food
      {
        targetX = foodX - (int) targetDeltaX;
      }

      if (antY > foodY)  //Ant is South of food
      {
        targetY = foodY + (int) targetDeltaY;
      }
      else  //Ant is North of food
      {
        targetY = foodY - (int) targetDeltaY;
      }
    }
    else if (deltaX == 0)
    {
      targetX = foodX;
      if (antY > foodY)  //Ant is directly South of food
      {
        targetY = foodY + 29;
      }
      else    //Ant is directly North of food
      {
        targetY = foodY - 29;
      }
    }
    else if (deltaY == 0)
    {
      targetY = foodY;
      if (antX > foodX)  //Ant is directly East of food
      {
        targetX = foodX + 29;
      }
      else    //Ant is directly West of food
      {
        targetX = foodX - 29;
      }
    }

    return pathFinder.findPath(antX, antY, targetX, targetY);
  }

  @Override
  public void run()
  {
    while (running)
    {
      if (readFoodSet)   //If there's a new foodSet to read, process it.
      {
        synchronized (serverFoodSetCopy)  //Maintain a lock on the food set it's reading
        {
          readFoodSet(serverFoodSetCopy);

          if (unprocessedFoodObjectives.size() > 0)
          {

            FoodObjective[] array = new FoodObjective[unprocessedFoodObjectives.size()];
            unprocessedFoodObjectives.toArray(array);

            FoodObjective nextFoodObjective = unprocessedFoodObjectives.peek(); //Peek instead of poll in case the food objective can't be processed
            allocateGroupsToFoodSite(nextFoodObjective);   //Allocate ants to the new food objective
          }

          readFoodSet = false;  //Done processing food set
        }
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

      if (objectiveCount1 > objectiveCount2)
      {
        return -1;
      }
      else if (objectiveCount1 < objectiveCount2)
      {
        return 1;
      }
      return 0;
    }
  }

  //Compares ants based on their proximity to a food site
  private class GroupComparator implements Comparator<AntGroup>
  {
    private int foodX;
    private int foodY;
    private GroupComparator(int foodX, int foodY)
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
    public int compare(AntGroup group1, AntGroup group2) {

      int group1Distance = NestManager.calculateDistance(group1.getGroupLeader().getAntData().gridX, group1.getGroupLeader().getAntData().gridY, foodX, foodY);
      int group2Distance = NestManager.calculateDistance(group2.getGroupLeader().getAntData().gridX, group2.getGroupLeader().getAntData().gridY, foodX, foodY);

      if(group1Distance < group2Distance)
      {
        return -1;
      }
      else if(group1Distance > group2Distance)
      {
        return 1;
      }
      return 0;
    }
  }
}
