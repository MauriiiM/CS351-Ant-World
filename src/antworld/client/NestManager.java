package antworld.client;

import antworld.client.navigation.MapManager;
import antworld.client.navigation.PathFinder;
import antworld.common.AntAction;
import antworld.common.AntData;
import antworld.common.CommData;
import antworld.common.FoodData;
import antworld.server.Cell;

import java.util.ArrayList;
import java.util.HashMap;

/**
 * Manages the ant nest and is the point of contact between the client and the AI
 * Created by John on 12/3/2016.
 */
public class NestManager
{
  private Client client;
  private final String mapFilePath; //"resources/AntTestWorldDiffusion.png"
  private MapManager mapManager;
  private PathFinder pathFinder;
  private FoodManager foodManager;
  private AntManager antManager;
  private HashMap<Integer, Ant> antMap;  //Must use ID as the key because antData is constantly changing
  private Ant ant;
  private Cell[][] geoMap;
  private static final int MINFOODRESPONSE = 200; //If an ant is within 200 cells of a food site, it will navigate to the food site


  public NestManager(Client client, String mapFilePath)
  {
    this.client = client;
    this.mapFilePath = mapFilePath;
    mapManager = new MapManager(mapFilePath);
    geoMap = mapManager.getGeoMap();
    pathFinder = new PathFinder(geoMap, mapManager.getMapWidth(), mapManager.getMapHeight());
    antMap = new HashMap<>();
    foodManager = new FoodManager(antMap,pathFinder);
    foodManager.start();
    Ant.mapManager = mapManager;
    Ant.pathFinder = pathFinder;

  }

  public void updateCommData(CommData commData)
  {

  }

  //Updates every Ant's reference to it's updated AntData variable sent from the server
  private void updateAntMapData(ArrayList<AntData> antList)
  {
    Ant nextAnt;
    for(AntData newData : antList)
    {
      nextAnt = antMap.get(newData.id);
      nextAnt.setAntData(newData);
    }
  }

  /**
   * Used by client to initialize the antMap before the main game loop
   * @param commData
   */
  public void initializeAntMap(CommData commData)
  {
    for (AntData ant : commData.myAntList)
    {
      antMap.put(ant.id, new Ant(ant));
    }
  }

  /**
   * Called by client: Sends commData to nest manager to modify before client sends it back to the server
   * @param commData
   */
  public void chooseActionsOfAllAnts(CommData commData)
  {
    updateAntMapData(commData.myAntList);
    mapManager.regenerateExplorationVals();  //LATER: Should be called on seperate thread or something?
    FoodData[] foodArray = commData.foodSet.toArray(new FoodData[commData.foodSet.size()]); //Create a FoodData array for the food manager to read. This keeps the foodset thread safe.
    foodManager.readFoodSet(foodArray);

    AntData nextAntData;
    for (Integer id : antMap.keySet())
    {
      nextAntData = antMap.get(id).getAntData();
      mapManager.updateCellExploration(nextAntData.gridX,nextAntData.gridY);
      AntAction action = chooseAction(commData, nextAntData);
      nextAntData.myAction = action;
    }
  }

  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntAction.AntActionType.STASIS);
    this.ant = antMap.get(ant.id);

    if (ant.ticksUntilNextAction > 0) return action;

    if (this.ant.exitNest(ant, action)) return action;

    if (this.ant.attackEnemyAnt(ant, action)) return action;

    if (this.ant.goToNest(ant, action)) return action;

    if (this.ant.lastDir != null)
    {
      //if (this.ant.pickUpFood(ant, action)) return action;

      //if (this.ant.pickUpWater(ant, action)) return action;
    }

    if (this.ant.goToEnemyAnt(ant, action)) return action;

    if (this.ant.goToFood(ant, action)) return action;

    if (this.ant.goToGoodAnt(ant, action)) return action;

    if (this.ant.goExplore(ant, action)) return action;

    return action;
  }
}
