package antworld.client;

import antworld.client.navigation.MapManager;
import antworld.client.navigation.NearestWaterPaths;
import antworld.client.navigation.Path;
import antworld.client.navigation.PathFinder;
import antworld.common.*;

import java.util.Random;

/**
 * Created by mauricio on 11/23/16.
 */
public class Ant
{
  static Random random = Constants.random;
  static PathFinder pathFinder;
  static MapManager mapManager;
  static int centerX, centerY;
  private static int waterCarryCap = 0;
  private static int necessaryWater = 200;
  private static Path waterPath;

  Direction lastDir;
  private AntData antData;
  public final int antID;
  boolean hasPath = false;
  boolean followFoodGradient = false;
  Path path;
  int pathStepCount;
  int h2oPathStepCount; //same as above but waterPath is backwards so this starts at waterpath.size
  private boolean randomWalk = false;
  boolean completedLastAction = false;
  private boolean checkAttritionDamage = false;
  private int randomSteps = 0;
  private Goal currentGoal = Goal.EXPLORE;
  FoodObjective foodObjective = null;


  Ant(AntData ant)
  {
    this.antData = ant;
    this.antID = ant.id;
  }

  public void setAntData(AntData newData)
  {
    this.antData = newData;

    //Verify antData from server matches old antData indicating that the last action was completed.
    if (newData.myAction.type == this.antData.myAction.type)
    {
      this.antData = newData;
      completedLastAction = true;
    }
    else
    {
      System.err.println("Ant: " + antData.toString() + "antData action= " + antData.myAction.type + " newData = " + newData.myAction.type);
      System.err.println("\tMISSED A STEP!!!");
      //System.exit(3);
      newData.myAction = this.antData.myAction;
      this.antData = newData;
      completedLastAction = false;
    }

  }

  public AntData getAntData()
  {
    return this.antData;
  }

  public void setCheckAttritionDamage(boolean state)
  {
    checkAttritionDamage = state;
  }

  //only called when ant's in the nest and will be true
  boolean dropFood(AntData ant, AntAction action)
  {
    action.type = AntAction.AntActionType.DROP;
    action.quantity = ant.carryUnits;
    return true;
  }

  boolean healSelf(AntAction action)
  {
    action.type = AntAction.AntActionType.HEAL;
    return true;
  }

  AntAction.AntActionType enterNest()
  {
    endPath();
    return AntAction.AntActionType.ENTER_NEST;
  }

  //Ants always exit nest at a random edge of the nest
  boolean exitNest(AntData ant, AntAction action, CommData data)
  {
    if (ant.underground)
    {
      if (ant.carryUnits > 0)
      {
        return dropFood(ant, action);
      }

      if (ant.health < 20)
      {
        return healSelf(action);
      }

      if (data.foodStockPile[0] < (necessaryWater = data.myAntList.size() /* *2*/) && waterCarryCap < necessaryWater)
      {
        if (!hasPath)
        {
          if (waterPath == null) //this will set the local water path
          {
            for (int i = 0; i < NearestWaterPaths.allWaterPaths.length; i++)
            {
              if (NearestWaterPaths.allWaterPaths[i][0] == centerX && NearestWaterPaths.allWaterPaths[i][1] == centerY)
              {
                waterPath = pathFinder.findPath(NearestWaterPaths.allWaterPaths[i][2], NearestWaterPaths.allWaterPaths[i][3], centerX, centerY);
                h2oPathStepCount = waterPath.getPath().size() - 1;
              }
            }
          }
          path = waterPath;
          waterCarryCap += ant.antType.getCarryCapacity();
          System.err.println(waterCarryCap);
          hasPath = true;
//          currentGoal = Goal.COLLECTWATER;
          action.type = AntAction.AntActionType.EXIT_NEST;
          action.x = path.getPath().get(h2oPathStepCount).getX();
          action.y = path.getPath().get(h2oPathStepCount).getY();
          h2oPathStepCount--;
          return true;
        }
      }

      int exitX = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      int deltaX = Math.abs(centerX - exitX);
      int exitY;
      int deltaY = 20 - deltaX;
      if (random.nextBoolean())
      {
        exitY = centerY + deltaY;
      }
      else
      {
        exitY = centerY - deltaY;
      }

      action.type = AntAction.AntActionType.EXIT_NEST;
      action.x = exitX;
      action.y = exitY;
      return true;
    }
    return false;
  }

  private void findPathToNest(AntAction action)
  {
    Path pathToNest = foodObjective.getPathToNest();
    int distanceToPathStartX = Math.abs(antData.gridX - pathToNest.getPathStart().getX());
    int distanceToPathStartY = Math.abs(antData.gridY - pathToNest.getPathStart().getY());

    if (distanceToPathStartX <= 1 && distanceToPathStartY <= 1)  //Ant is adjacent to path, start to follow it
    {
      setPath(pathToNest);
      foodObjective.unallocateAnt(this);  //Unallocate ant
      foodObjective = null; //Reset food objective
    }
    else    //Follow path diffusion gradient
    {
      action.type = AntAction.AntActionType.MOVE;
      action.direction = getBestDirectionToPath(antData.gridX, antData.gridY);
    }
  }

  boolean goToNest(AntData ant, AntAction action)
  {
    if (currentGoal == Goal.RETURNTONEST)
    {
      if (hasPath && pathStepCount < path.getPath().size() - 1)
      {
        int deltaX = Math.abs(centerX - antData.gridX);
        int deltaY = Math.abs(centerY - antData.gridY);
        if ((deltaX + deltaY) <= 20)  //If the ant is less than 20 cells away from the center of the nest, it must be on the nest.
        {
          action.type = enterNest();
          endPath();
          currentGoal = Goal.EXPLORE;
          return true;
        }

        action.type = AntAction.AntActionType.MOVE;
        action.direction = xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        pathStepCount++;
      }
      else if (hasPath && pathStepCount == path.getPath().size() - 1)
      {
        action.type = enterNest();
        endPath();
        currentGoal = Goal.EXPLORE;
      }
      else if (!hasPath && antData.carryUnits > 0)  //If the ant doesn't have a path yet, but is carrying food
      {
        findPathToNest(action);
      }
      else if (!hasPath && antData.carryUnits == 0)  //If the ant doesn't have a path, but it needs to go home
      {
        //System.err.println("Ant: " + antData.toString() + " REQUESTING PATH HOME!");
        NestManager.pathFinder.requestAntPath(this, antData.gridX, antData.gridY, centerX, centerY);
      }
      return true;
    }
    return false;
  }

  boolean isFollowingPath(AntData ant, AntAction action)
  {
    if (hasPath)
    {
      action.type = AntAction.AntActionType.MOVE;
      if (path == waterPath && ant.carryUnits == 0)//going for water
      {
        action.direction = xyCoordinateToDirection(path.getPath().get(h2oPathStepCount).getX(), path.getPath().get(h2oPathStepCount).getY(), ant.gridX, ant.gridY);
        System.err.println("ant @(" + antData.gridX + ", " + ant.gridY + ")" + "\npath @(" + path.getPath().get(h2oPathStepCount).getX() + ", " + path.getPath().get(h2oPathStepCount).getY() + ")");
        if (h2oPathStepCount == 0)
        {
          System.err.println("PICKUP WATER");
          action.type = AntAction.AntActionType.PICKUP;
          action.quantity = ant.antType.getCarryCapacity() - 1;
          return true;
        }
        h2oPathStepCount--;
      }
      else if (ant.carryUnits > 0 || path != waterPath)
      {
        currentGoal = Goal.RETURNTONEST;
        goToNest(ant, action);
      }
//      action.quantity = ant.antType.getCarryCapacity() - 1;
      return true;
    }
    return false;
  }

  private void pickUpFood(AntAction action)
  {
    int foodX = foodObjective.getObjectiveX();
    int foodY = foodObjective.getObjectiveY();
    int antX = antData.gridX;
    int antY = antData.gridY;
    Direction foodDirection = xyCoordinateToDirection(foodX, foodY, antX, antY);

    action.type = AntAction.AntActionType.PICKUP;
    action.direction = foodDirection;
    action.quantity = antData.antType.getCarryCapacity() - 1;
    foodObjective.reduceFoodLeft(antData.antType.getCarryCapacity() - 1);
    System.err.println("Ant: " + antData.toString() + " : PICKING UP FOOD : foodLeft = " + foodObjective.getFoodLeft());
    setCurrentGoal(Goal.RETURNTONEST);
  }

  private Direction goOppositeDirection(Direction dir)
  {
    switch (dir)
    {
      case NORTH:
        return Direction.SOUTH;
      case SOUTH:
        return Direction.NORTH;
      case WEST:
        return Direction.EAST;
      case EAST:
        return Direction.WEST;
      case NORTHWEST:
        return Direction.SOUTHEAST;
      case NORTHEAST:
        return Direction.SOUTHWEST;
      case SOUTHWEST:
        return Direction.NORTHEAST;
      case SOUTHEAST:
        return Direction.NORTHWEST;
    }
    return dir;
  }

  private Direction xyCoordinateToDirection(int nextX, int nextY, int antX, int antY)
  {
    if (nextX == antX && nextY < antY) return Direction.NORTH;
    if (nextX > antX && nextY < antY) return Direction.NORTHEAST;
    if (nextX > antX && nextY == antY) return Direction.EAST;
    if (nextX > antX && nextY > antY) return Direction.SOUTHEAST;
    if (nextX == antX && nextY > antY) return Direction.SOUTH;
    if (nextX < antX && nextY > antY) return Direction.SOUTHWEST;
    if (nextX < antX && nextY == antY) return Direction.WEST;
    if (nextX < antX && nextY < antY) return Direction.NORTHWEST;
    //System.err.println("NO DIRECTION!! nextX= " + nextX + " nextY= " + nextY + " antX=" + antX + " antY=" + antY);
    return null;
  }

  //Samples two points in the given direction and returns the average exploration value
  //Maybe the average should also consider the cells directly around the ant instead of just around the radius of sight.
  private int getAverageExploreVal(int x, int y, Direction direction)
  {
    int explorationValue = 0;

    switch (direction)
    {
      case NORTH:
        explorationValue += mapManager.getExplorationVal(x, y - 29);
        explorationValue += mapManager.getExplorationVal(x, y - 1);
        explorationValue += mapManager.getExplorationVal(x, y - 32);
        break;
      case NORTHEAST:
        explorationValue += mapManager.getExplorationVal(x + 29, y - 29);
        explorationValue += mapManager.getExplorationVal(x + 1, y - 1);
        explorationValue += mapManager.getExplorationVal(x + 32, y - 32);
        break;
      case NORTHWEST:
        explorationValue += mapManager.getExplorationVal(x - 29, y - 29);
        explorationValue += mapManager.getExplorationVal(x - 1, y - 1);
        explorationValue += mapManager.getExplorationVal(x - 32, y - 32);
        break;
      case SOUTH:
        explorationValue += mapManager.getExplorationVal(x, y + 29);
        explorationValue += mapManager.getExplorationVal(x, y + 1);
        explorationValue += mapManager.getExplorationVal(x, y + 32);
        break;
      case SOUTHEAST:
        explorationValue += mapManager.getExplorationVal(x + 29, y + 29);
        explorationValue += mapManager.getExplorationVal(x + 1, y + 1);
        explorationValue += mapManager.getExplorationVal(x + 32, y + 32);
        break;
      case SOUTHWEST:
        explorationValue += mapManager.getExplorationVal(x - 29, y + 29);
        explorationValue += mapManager.getExplorationVal(x - 1, y + 1);
        explorationValue += mapManager.getExplorationVal(x - 32, y + 32);
        break;
      case EAST:
        explorationValue += mapManager.getExplorationVal(x + 29, y);
        explorationValue += mapManager.getExplorationVal(x + 1, y);
        explorationValue += mapManager.getExplorationVal(x + 32, y);
        break;
      case WEST:
        explorationValue += mapManager.getExplorationVal(x - 29, y);
        explorationValue += mapManager.getExplorationVal(x - 1, y);
        explorationValue += mapManager.getExplorationVal(x - 32, y);
        break;
    }
    return explorationValue / 3;
  }

  private Direction getBestDirectionToExplore(int x, int y)
  {
    int exploreValNorth = getAverageExploreVal(x, y, Direction.NORTH);
    int bestValSoFar = exploreValNorth;
    Direction bestDirection = Direction.NORTH;

    int exploreValNE = getAverageExploreVal(x, y, Direction.NORTHEAST);
    if (exploreValNE > bestValSoFar)
    {
      bestValSoFar = exploreValNE;
      bestDirection = Direction.NORTHEAST;
    }
    else if (exploreValNE == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.NORTHEAST;
        randomWalk = true;
      }
    }

    int exploreValNW = getAverageExploreVal(x, y, Direction.NORTHWEST);
    if (exploreValNW > bestValSoFar)
    {
      bestValSoFar = exploreValNW;
      bestDirection = Direction.NORTHWEST;
    }
    else if (exploreValNW == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.NORTHWEST;
        randomWalk = true;
      }
    }

    int exploreValSouth = getAverageExploreVal(x, y, Direction.SOUTH);
    if (exploreValSouth > bestValSoFar)
    {
      bestValSoFar = exploreValSouth;
      bestDirection = Direction.SOUTH;
    }
    else if (exploreValSouth == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.SOUTH;
        randomWalk = true;
      }
    }

    int exploreValSE = getAverageExploreVal(x, y, Direction.SOUTHEAST);
    if (exploreValSE > bestValSoFar)
    {
      bestValSoFar = exploreValSE;
      bestDirection = Direction.SOUTHEAST;
    }
    else if (exploreValSE == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.SOUTHEAST;
        randomWalk = true;
      }
    }

    int exploreValSW = getAverageExploreVal(x, y, Direction.SOUTHWEST);
    if (exploreValSW > bestValSoFar)
    {
      bestValSoFar = exploreValSW;
      bestDirection = Direction.SOUTHWEST;
    }
    else if (exploreValSW == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.SOUTHWEST;
        randomWalk = true;
      }
    }

    int exploreValEast = getAverageExploreVal(x, y, Direction.EAST);
    if (exploreValEast > bestValSoFar)
    {
      bestValSoFar = exploreValEast;
      bestDirection = Direction.EAST;
    }
    else if (exploreValEast == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.EAST;
        randomWalk = true;
      }
    }

    int exploreValWest = getAverageExploreVal(x, y, Direction.WEST);
    if (exploreValWest > bestValSoFar)
    {
      bestDirection = Direction.WEST;
    }
    else if (exploreValWest == bestValSoFar)
    {
      if (random.nextBoolean())
      {
        bestDirection = Direction.WEST;
        randomWalk = true;
      }
    }

    return bestDirection;
  }

  //Returns the food gradient val of the neighboring cell in a given direction
  private int getFoodGradientVal(int x, int y, Direction direction)
  {
    int foodGradientVal = 0;

    switch (direction)
    {
      case NORTH:
        foodGradientVal += mapManager.getFoodProximityVal(x, y - 1);
        break;
      case NORTHEAST:
        foodGradientVal += mapManager.getFoodProximityVal(x + 1, y - 1);
        break;
      case NORTHWEST:
        foodGradientVal += mapManager.getFoodProximityVal(x - 1, y - 1);
        break;
      case SOUTH:
        foodGradientVal += mapManager.getFoodProximityVal(x, y + 1);
        break;
      case SOUTHEAST:
        foodGradientVal += mapManager.getFoodProximityVal(x + 1, y + 1);
        break;
      case SOUTHWEST:
        foodGradientVal += mapManager.getFoodProximityVal(x - 1, y + 1);
        break;
      case EAST:
        foodGradientVal += mapManager.getFoodProximityVal(x + 1, y);
        break;
      case WEST:
        foodGradientVal += mapManager.getFoodProximityVal(x - 1, y);
        break;
    }
    return foodGradientVal;
  }

  private Direction getBestDirectionToFood(int x, int y)
  {
    Direction heading = xyCoordinateToDirection(foodObjective.getObjectiveX(), foodObjective.getObjectiveY(), x, y);
    int bestValSoFar = 0;
    Direction bestDirection = heading;

    if (heading == Direction.NORTH || heading == Direction.NORTHEAST || heading == Direction.NORTHWEST)
    {
      int foodValNorth = getFoodGradientVal(x, y, Direction.NORTH);
      if (foodValNorth > bestValSoFar)
      {
        bestValSoFar = foodValNorth;
        bestDirection = Direction.NORTH;
      }
    }

    if (heading == Direction.NORTH || heading == Direction.NORTHEAST || heading == Direction.EAST)
    {
      int foodValNE = getFoodGradientVal(x, y, Direction.NORTHEAST);
      if (foodValNE > bestValSoFar)
      {
        bestValSoFar = foodValNE;
        bestDirection = Direction.NORTHEAST;
      }
    }

    if (heading == Direction.NORTH || heading == Direction.NORTHWEST || heading == Direction.WEST)
    {
      int foodValNW = getFoodGradientVal(x, y, Direction.NORTHWEST);
      if (foodValNW > bestValSoFar)
      {
        bestValSoFar = foodValNW;
        bestDirection = Direction.NORTHWEST;
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHEAST || heading == Direction.SOUTHWEST)
    {
      int foodValSouth = getFoodGradientVal(x, y, Direction.SOUTH);
      if (foodValSouth > bestValSoFar)
      {
        bestValSoFar = foodValSouth;
        bestDirection = Direction.SOUTH;
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHEAST || heading == Direction.EAST)
    {
      int foodValSE = getFoodGradientVal(x, y, Direction.SOUTHEAST);
      if (foodValSE > bestValSoFar)
      {
        bestValSoFar = foodValSE;
        bestDirection = Direction.SOUTHEAST;
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHWEST || heading == Direction.WEST)
    {
      int foodValSW = getFoodGradientVal(x, y, Direction.SOUTHWEST);
      if (foodValSW > bestValSoFar)
      {
        bestValSoFar = foodValSW;
        bestDirection = Direction.SOUTHWEST;
      }
    }

    if (heading == Direction.EAST || heading == Direction.NORTHEAST || heading == Direction.SOUTHEAST)
    {
      int foodValEast = getFoodGradientVal(x, y, Direction.EAST);
      if (foodValEast > bestValSoFar)
      {
        bestValSoFar = foodValEast;
        bestDirection = Direction.EAST;
      }
    }

    if (heading == Direction.WEST || heading == Direction.NORTHWEST || heading == Direction.NORTHEAST)
    {
      int foodValWest = getFoodGradientVal(x, y, Direction.WEST);
      if (foodValWest > bestValSoFar)
      {
        bestValSoFar = foodValWest;
        bestDirection = Direction.WEST;
      }
    }

    //System.err.println("Ant: " + antData.toString() + " : bestValSoFar = " + bestValSoFar);
    if (bestValSoFar == 0) //If no direction is good, get a general heading and go in that direction
    {
      //System.err.println("DEAD RECKONING...");
      //bestDirection = xyCoordinateToDirection(foodObjective.getObjectiveX(),foodObjective.getObjectiveY(),x,y);
      //System.exit(3);
    }
    //lastFoodGradientVal = bestValSoFar;
    return bestDirection;
  }

  private int getPathGradientVal(int x, int y, Direction direction)
  {
    int pathGradientVal = 0;

    switch (direction)
    {
      case NORTH:
        pathGradientVal += mapManager.getPathProximityVal(x, y - 1);
        break;
      case NORTHEAST:
        pathGradientVal += mapManager.getPathProximityVal(x + 1, y - 1);
        break;
      case NORTHWEST:
        pathGradientVal += mapManager.getPathProximityVal(x - 1, y - 1);
        break;
      case SOUTH:
        pathGradientVal += mapManager.getPathProximityVal(x, y + 1);
        break;
      case SOUTHEAST:
        pathGradientVal += mapManager.getPathProximityVal(x + 1, y + 1);
        break;
      case SOUTHWEST:
        pathGradientVal += mapManager.getPathProximityVal(x - 1, y + 1);
        break;
      case EAST:
        pathGradientVal += mapManager.getPathProximityVal(x + 1, y);
        break;
      case WEST:
        pathGradientVal += mapManager.getPathProximityVal(x - 1, y);
        break;
    }
    return pathGradientVal;
  }

  private Direction getBestDirectionToPath(int x, int y)
  {
    int pathValNorth = getPathGradientVal(x, y, Direction.NORTH);
    int bestValSoFar = pathValNorth;
    Direction bestDirection = Direction.NORTH;

    int pathValNE = getPathGradientVal(x, y, Direction.NORTHEAST);
    if (pathValNE > bestValSoFar)
    {
      bestValSoFar = pathValNE;
      bestDirection = Direction.NORTHEAST;
    }

    int pathValNW = getPathGradientVal(x, y, Direction.NORTHWEST);
    if (pathValNW > bestValSoFar)
    {
      bestValSoFar = pathValNW;
      bestDirection = Direction.NORTHWEST;
    }

    int pathValSouth = getPathGradientVal(x, y, Direction.SOUTH);
    if (pathValSouth > bestValSoFar)
    {
      bestValSoFar = pathValSouth;
      bestDirection = Direction.SOUTH;
    }

    int pathValSE = getPathGradientVal(x, y, Direction.SOUTHEAST);
    if (pathValSE > bestValSoFar)
    {
      bestValSoFar = pathValSE;
      bestDirection = Direction.SOUTHEAST;
    }

    int pathValSW = getPathGradientVal(x, y, Direction.SOUTHWEST);
    if (pathValSW > bestValSoFar)
    {
      bestValSoFar = pathValSW;
      bestDirection = Direction.SOUTHWEST;
    }

    int pathValEast = getPathGradientVal(x, y, Direction.EAST);
    if (pathValEast > bestValSoFar)
    {
      bestValSoFar = pathValEast;
      bestDirection = Direction.EAST;
    }

    int pathValWest = getPathGradientVal(x, y, Direction.WEST);
    if (pathValWest > bestValSoFar)
    {
      bestDirection = Direction.WEST;
    }

    return bestDirection;
  }

  public Goal getCurrentGoal()
  {
    return this.currentGoal;
  }

  public void setCurrentGoal(Goal newGoal)
  {
    this.currentGoal = newGoal;
  }

  public FoodObjective getFoodObjective()
  {
    return foodObjective;
  }

  public void setFoodObjective(FoodObjective newObjective)
  {
    this.foodObjective = newObjective;
  }

  public void setPath(Path newPath)
  {
    hasPath = true;
    pathStepCount = 0;
    path = newPath;
  }

  //Called when an ant has reached the end of the path it was following. Or when an ant should have its path reset
  public void endPath()
  {
    hasPath = false;
    path = null;
  }

  boolean attackEnemyAnt(AntData ant, AntAction action)
  {
    return false;
  }

  boolean goToEnemyAnt(AntData ant, AntAction action)
  {
    return false;
  }

  boolean goToFood(AntData ant, AntAction action)
  {
    if (currentGoal != Goal.GOTOFOODSITE)
    {
      return false;
    }
    //System.err.println("GoToFood Ant: " + antData.toString() + " hasPath = " + hasPath + " : followGradient= " + followFoodGradient);
    if (hasPath) //If the ant has a path, follow it
    {
      if (pathStepCount < path.getPath().size() - 1) //If the ant has not reached the end of the path
      {
        //System.err.println("Ant: " + antData.toString() + " FOLLOWING PATH");
        action.type = AntAction.AntActionType.MOVE;
        action.direction = xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        pathStepCount++;

      }
      else if (hasPath && pathStepCount == path.getPath().size() - 1) //Ant has finished following the path
      {
        endPath();  //Get rid of the old path
        followFoodGradient = true;
      }
    }
    else if (foodObjective != null)
    {
      followFoodGradient = true;
    }

    if (followFoodGradient)
    {
      //System.err.println("Ant: " + antData.toString() + " FOLLOWING GRADIENT");
      int distanceToFoodX = Math.abs(antData.gridX - foodObjective.getObjectiveX());
      int distanceToFoodY = Math.abs(antData.gridY - foodObjective.getObjectiveY());

      if (distanceToFoodX <= 1 && distanceToFoodY <= 1)  //Ant is adjacent to food, pick it up
      {
        followFoodGradient = false;
        pickUpFood(action);
        return true;
      }
      else    //Follow food diffusion gradient
      {
        action.type = AntAction.AntActionType.MOVE;
        action.direction = getBestDirectionToFood(antData.gridX, antData.gridY);
      }
    }

    return true;
  }

  boolean goToGoodAnt(AntData ant, AntAction action)
  {
    return false;
  }

  /**
   * todo sometimes the ants get stuck against the coast...investigate what's going on
   */
  boolean goExplore(AntData ant, AntAction action)
  {

    if (currentGoal != Goal.EXPLORE)
    {
      return false;
    }

    if (checkAttritionDamage)  //Called once every 5,000 frames (when an ant should have lost 1 health of attrition damage)
    {
      int distanceToNest = NestManager.calculateDistance(antData.gridX, antData.gridY, centerX, centerY);
      int ticksToGetHome = distanceToNest * 2;  //Not accounting for elevation
      int approxTripAttrition = ticksToGetHome / 5000; //5000 ticks per unit of attrition damage

      if (antData.health - approxTripAttrition <= 8) //If the ant can make it home with 8 or less health, it should head back to the nest
      {
        currentGoal = Goal.RETURNTONEST;
      }

      checkAttritionDamage = false;
    }

    Direction dir;

    if (!randomWalk)
    {
      dir = getBestDirectionToExplore(antData.gridX, antData.gridY);
    }
    else  //If randomly walking in a direction, keep going for 5 paces
    {
      randomSteps++;

      if (randomSteps >= 5)
      {
        randomWalk = false;
        randomSteps = 0;
      }
      dir = lastDir;
    }

    action.type = AntAction.AntActionType.MOVE;
    action.direction = dir;
    lastDir = dir;
    //mapManager.addAntStep(ant.gridX,ant.gridY,randomWalk);   //Used for testing
    return true;
  }
}
