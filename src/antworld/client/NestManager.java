package antworld.client;

import antworld.client.navigation.Coordinate;
import antworld.client.navigation.MapManager;
import antworld.client.navigation.PathFinder;
import antworld.common.AntData;
import antworld.common.CommData;
import antworld.common.FoodData;
import antworld.server.Cell;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedList;

/**
 * Manages the ant nest and is the point of contact between the client and the AI
 * Created by John on 12/3/2016.
 */
public class NestManager
{
  static String NEST_NAME;
  static int NESTX;
  static int NESTY;
  static PathFinder pathFinder;

  private Client client;
  private CommData commData;
  private final String mapFilePath; //"resources/AntTestWorldDiffusion.png"
  private MapManager mapManager;
  private FoodManager foodManager;
  private EnemyManager enemyManager;
  private HashMap<Integer, Ant> antMap;  //Must use ID as the key because antData is constantly changing
  private HashMap<Integer, AntGroup> groupMap;
  private Ant ant;
  private Cell[][] geoMap;

  NestManager(Client client, String mapFilePath)
  {
    this.client = client;
    this.mapFilePath = mapFilePath;
    mapManager = new MapManager(mapFilePath);
    geoMap = mapManager.getGeoMap();
    pathFinder = new PathFinder(geoMap, mapManager.getMapWidth(), mapManager.getMapHeight(), NESTX, NESTY);
    antMap = new HashMap<>();
    groupMap = new HashMap<>();
    foodManager = new FoodManager(groupMap,pathFinder);
    foodManager.start();
    enemyManager = new EnemyManager(groupMap,pathFinder);
    enemyManager.start();
    Ant.mapManager = mapManager;
    Ant.pathFinder = pathFinder;
    AntGroup.mapManager = mapManager;
    AntGroup.pathFinder = pathFinder;
    AntGroup.foodManager = foodManager;
    AntGroup.nestManager = this;
  }

  synchronized CommData getCommData()
  {
    return commData;
  }

  synchronized void setCommData(CommData newData)
  {
    commData = newData;
  }

  /**
   * Actually calculates the distance between two points by estimating Euclidean distance!
   *
   * @param x1
   * @param y1
   * @param x2
   * @param y2
   */
  public static int calculateDistance(int x1, int y1, int x2, int y2)
  {
    int deltaX = Math.abs(x1 - x2);
    int deltaY = Math.abs(y1 - y2);
    int distance = deltaX + deltaY;
    distance = (distance * 2) / 3;  //This is what Joel's util was missing
    return distance;
  }

  //Updates every Ant's reference to it's updated AntData variable sent from the server
  private void updateAntMapData(ArrayList<AntData> antList, HashSet<FoodData> foodSet)
  {
    Ant nextAnt;
    LinkedList<Coordinate> objectLocations = new LinkedList<>(); //Does this need to be volatile?
    for (AntData antData : antList)
    {
      nextAnt = antMap.get(antData.id);
      nextAnt.setAntData(antData);
      objectLocations.add(new Coordinate(antData.gridX, antData.gridY));
    }
    for (FoodData foodData : foodSet)
    {
      objectLocations.add(new Coordinate(foodData.gridX, foodData.gridY));
    }
    mapManager.updateCellOccupied(objectLocations);
  }

  /**
   * Used by client to initialize the antMap before the main game loop
   * Also initializes the group map by assigning ants to a group when they are created
   * @param commData
   */
  public void initializeAntMap(CommData commData)
  {
    for (AntData ant : commData.myAntList)
    {
      Ant newAnt = new Ant(ant);
      antMap.put(ant.id, newAnt);

      Integer groupCount = groupMap.size();
      if(groupCount==0) //If a group has not been initialized yet
      {
        AntGroup newGroup = new AntGroup(groupCount,newAnt);  //Create the first group
        groupMap.put(groupCount,newGroup);    //Add it to the group map
      }
      else
      {
        AntGroup lastGroup = groupMap.get(groupCount-1);  //Get the most recently made group
        if(!lastGroup.isFull())   //If it's not full, assign the next ant to it
        {
          lastGroup.addAntToGroup(newAnt);
        }
        else  //Otherwise, create a new group and assign the new ant as the leader
        {
          AntGroup newGroup = new AntGroup(groupCount,newAnt);
          groupMap.put(groupCount,newGroup);
        }
      }
    }
  }

  /**
   * Called by client: Sends commData to nest manager to modify before client sends it back to the server
   *
   * @param commData
   */
  public void chooseActionsOfAllAnts(CommData commData)
  {
    updateAntMapData(commData.myAntList, commData.foodSet);
    mapManager.regenerateExplorationVals();  //LATER: Should be called on seperate thread or something?
//    mapManager.fadeEnemyProximityGradient();

    HashSet<FoodData> foodSet = commData.foodSet;
    if (foodSet.size() > 0)  //If the foodSet is greater than 0, send a copy to the food manager
    {
      FoodData[] foodArray = foodSet.toArray(new FoodData[foodSet.size()]); //Create a FoodData array for the food manager to read. This keeps the foodset thread safe.
      foodManager.setFoodSet(foodArray);
    }

    HashSet<AntData> enemySet = commData.enemyAntSet;
    if(enemySet.size() > 0)  //If the foodSet is greater than 0, send a copy to the food manager
    {
      AntData[] enemyArray = enemySet.toArray(new AntData[enemySet.size()]); //Create a FoodData array for the food manager to read. This keeps the foodset thread safe.
      enemyManager.setEnemySet(enemyArray);
    }

    AntGroup nextGroup;
    for(Integer id : groupMap.keySet())
    {
      nextGroup = groupMap.get(id);
      mapManager.updateCellExploration(nextGroup.getGroupLeader().getAntData().gridX,nextGroup.getGroupLeader().getAntData().gridY);
      //todo check attrition damage
      nextGroup.chooseGroupActions();
    }

    /*
    Ant nextAnt;
    AntData nextAntData;
    for (Integer id : antMap.keySet())
    {
      nextAnt = antMap.get(id);
      nextAntData = nextAnt.getAntData();
      mapManager.updateCellExploration(nextAntData.gridX, nextAntData.gridY);

      if (commData.gameTick % 5000 == 0 && nextAnt.getCurrentGoal() == Goal.EXPLORE) //If the ant is out exploring, check attrition damage to see if it should head to nest
      {
        nextAnt.setCheckAttritionDamage(true);
      }

      Goal currentGoal = antMap.get(id).getCurrentGoal();
      if(currentGoal == Goal.EXPLORE)
      {
      }
      else if(currentGoal == Goal.GOTOFOODSITE)
      {
      }
      else if(currentGoal == Goal.RETURNTONEST)
      {
      }

      AntAction action = chooseAction(commData, nextAntData);
      nextAntData.myAction = action;
    }
    */

    //Used for testing food gradient write/erase
    /*
    if(commData.gameTick%600==0)
    {
      System.out.println("GAME TICK = " + commData.gameTick);
      //mapManager.printFoodMap();
    }
    */
  }

  /*
  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntAction.AntActionType.STASIS);
    this.ant = antMap.get(ant.id);

    if (!this.ant.completedLastAction) //If it did not complete it's last action, resend
    {
      return this.ant.getAntData().myAction;
    }

    if (ant.ticksUntilNextAction > 0) return action;

    if (this.ant.exitNest(ant, action, data)) return action;

    //if (this.ant.attackEnemyAnt(ant, action)) return action;

    if (this.ant.isFollowingPath(ant, action)) return action;

    if (this.ant.goToNest(ant, action)) return action;

    if (this.ant.goToEnemyAnt(ant, action)) return action;

    if (this.ant.goToFood(ant, action)) return action;

    if (this.ant.goToGoodAnt(ant, action)) return action;

    if (this.ant.goExplore(ant, action)) return action;
    this.ant.getAntData().myAction = action;
    return action;
  }
  */
}
