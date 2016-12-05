package antworld.client;

import antworld.client.navigation.MapCell;
import antworld.client.navigation.MapManager;
import antworld.client.navigation.Path;
import antworld.client.navigation.PathFinder;
import antworld.common.*;

import java.util.ArrayList;
import java.util.Random;

/**
 * Created by mauricio on 11/23/16.
 */
public class Ant
{
  static Random random = Constants.random;
  static PathFinder pathFinder;
  static int centerX, centerY;
  static int antsUnderground;
  static MapCell[][] world;
  static MapManager mapManager;
  static CommData data;
  Direction dir, lastDir;
  private AntData antData;
  public final int antID;
  //  AntAction action;
  boolean hasPath = false;
  boolean followFoodGradient = false;
  Path path;
  int pathStepCount;
  private boolean randomWalk = false;
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
  }

  public AntData getAntData()
  {
    return this.antData;
  }

  //only called when ant's in the nest and will be true
  boolean dropFood(AntData ant, AntAction action)
  {
    action.type = AntAction.AntActionType.DROP;
    action.quantity = ant.carryUnits;
    return true;
  }

  AntAction.AntActionType enterNest()
  {
    endPath();
    return AntAction.AntActionType.ENTER_NEST;
  }

  boolean exitNest(AntData ant, AntAction action)
  {
    if (ant.underground)
    {
      if (ant.carryUnits > 0)
      {
        return dropFood(ant, action);
      }
      action.type = AntAction.AntActionType.EXIT_NEST;
      action.x = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      action.y = centerY - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      return true;
    }
    return false;
  }

  /**
   * @todo currently going all the way to center of nest, what's the math to go just to the nest?
   */
  boolean goToNest(AntData ant, AntAction action)
  {
    if (currentGoal == Goal.RETURNTONEST)
    {
      if(hasPath && pathStepCount < path.getPath().size()-1)
      {
        action.type = AntAction.AntActionType.MOVE;
        action.direction = xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        pathStepCount ++;
      }
      else if (hasPath && pathStepCount == path.getPath().size()-1)
      {
        action.type = enterNest();
        endPath();
        currentGoal = Goal.EXPLORE;
        foodObjective.unallocateAnt(this);
      }
      else
      {
        findPathToNest(action);
      }
      return true;
    }
    return false;
  }

  private void findPathToNest(AntAction action)
  {
    Path pathToNest = foodObjective.getPathToNest();
    int distanceToPathStartX = Math.abs(antData.gridX - pathToNest.getPathStart().getX());
    int distanceToPathStartY = Math.abs(antData.gridY - pathToNest.getPathStart().getY());

    if( distanceToPathStartX <=1 && distanceToPathStartY <= 1)  //Ant is adjacent to path, start to follow it
    {
      setPath(pathToNest);
    }
    else    //Follow path diffusion gradient
    {
      action.type = AntAction.AntActionType.MOVE;
      action.direction = getBestDirectionToPath(antData.gridX,antData.gridY);
    }
  }

  private void pickUpFood(AntAction action)
  {
    int foodX = foodObjective.getObjectiveX();
    int foodY = foodObjective.getObjectiveY();
    int antX = antData.gridX;
    int antY = antData.gridY;
    Direction foodDirection = xyCoordinateToDirection(foodX,foodY,antX,antY);

    action.type = AntAction.AntActionType.PICKUP;
    action.direction = foodDirection;
    action.quantity = antData.antType.getCarryCapacity() - 1;
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
    System.err.println("NO DIRECTION!! nextX= " + nextX + " nextY= " + nextY + " antX=" + antX + " antY=" + antY);
    return null;
  }

  //Samples two points in the given direction and returns the average exploration value
  //Maybe the average should also consider the cells directly around the ant instead of just around the radius of sight.
  private int getAverageExploreVal(int x, int y, Direction direction)
  {
    int explorationValue = 0;

    switch(direction)
    {
      case NORTH:
        explorationValue += mapManager.getExplorationVal(x,y-29);
        explorationValue += mapManager.getExplorationVal(x,y-1);
        explorationValue += mapManager.getExplorationVal(x,y-32);
        break;
      case NORTHEAST:
        explorationValue += mapManager.getExplorationVal(x+29,y-29);
        explorationValue += mapManager.getExplorationVal(x+1,y-1);
        explorationValue += mapManager.getExplorationVal(x+32,y-32);
        break;
      case NORTHWEST:
        explorationValue += mapManager.getExplorationVal(x-29,y-29);
        explorationValue += mapManager.getExplorationVal(x-1,y-1);
        explorationValue += mapManager.getExplorationVal(x-32,y-32);
        break;
      case SOUTH:
        explorationValue += mapManager.getExplorationVal(x,y+29);
        explorationValue += mapManager.getExplorationVal(x,y+1);
        explorationValue += mapManager.getExplorationVal(x,y+32);
        break;
      case SOUTHEAST:
        explorationValue += mapManager.getExplorationVal(x+29,y+29);
        explorationValue += mapManager.getExplorationVal(x+1,y+1);
        explorationValue += mapManager.getExplorationVal(x+32,y+32);
        break;
      case SOUTHWEST:
        explorationValue += mapManager.getExplorationVal(x-29,y+29);
        explorationValue += mapManager.getExplorationVal(x-1,y+1);
        explorationValue += mapManager.getExplorationVal(x-32,y+32);
        break;
      case EAST:
        explorationValue += mapManager.getExplorationVal(x+29,y);
        explorationValue += mapManager.getExplorationVal(x+1,y);
        explorationValue += mapManager.getExplorationVal(x+32,y);
        break;
      case WEST:
        explorationValue += mapManager.getExplorationVal(x-29,y);
        explorationValue += mapManager.getExplorationVal(x-1,y);
        explorationValue += mapManager.getExplorationVal(x-32,y);
        break;
    }
    return explorationValue/3;
  }

  private Direction getBestDirectionToExplore(int x, int y)
  {
    int exploreValNorth = getAverageExploreVal(x,y,Direction.NORTH);
    int bestValSoFar = exploreValNorth;
    Direction bestDirection = Direction.NORTH;

    int exploreValNE = getAverageExploreVal(x,y,Direction.NORTHEAST);
    if(exploreValNE > bestValSoFar)
    {
      bestValSoFar = exploreValNE;
      bestDirection = Direction.NORTHEAST;
    }
    else if(exploreValNE == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.NORTHEAST;
        randomWalk = true;
      }
    }

    int exploreValNW = getAverageExploreVal(x,y,Direction.NORTHWEST);
    if(exploreValNW > bestValSoFar)
    {
      bestValSoFar = exploreValNW;
      bestDirection = Direction.NORTHWEST;
    }
    else if(exploreValNW == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.NORTHWEST;
        randomWalk = true;
      }
    }

    int exploreValSouth = getAverageExploreVal(x,y,Direction.SOUTH);
    if(exploreValSouth > bestValSoFar)
    {
      bestValSoFar = exploreValSouth;
      bestDirection = Direction.SOUTH;
    }
    else if(exploreValSouth == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.SOUTH;
        randomWalk = true;
      }
    }

    int exploreValSE = getAverageExploreVal(x,y,Direction.SOUTHEAST);
    if(exploreValSE > bestValSoFar)
    {
      bestValSoFar = exploreValSE;
      bestDirection = Direction.SOUTHEAST;
    }
    else if(exploreValSE == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.SOUTHEAST;
        randomWalk = true;
      }
    }

    int exploreValSW = getAverageExploreVal(x,y,Direction.SOUTHWEST);
    if(exploreValSW > bestValSoFar)
    {
      bestValSoFar = exploreValSW;
      bestDirection = Direction.SOUTHWEST;
    }
    else if(exploreValSW == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.SOUTHWEST;
        randomWalk = true;
      }
    }

    int exploreValEast = getAverageExploreVal(x,y,Direction.EAST);
    if(exploreValEast > bestValSoFar)
    {
      bestValSoFar = exploreValEast;
      bestDirection = Direction.EAST;
    }
    else if(exploreValEast == bestValSoFar)
    {
      if(random.nextBoolean())
      {
        bestDirection = Direction.EAST;
        randomWalk = true;
      }
    }

    int exploreValWest = getAverageExploreVal(x,y,Direction.WEST);
    if(exploreValWest > bestValSoFar)
    {
      bestDirection = Direction.WEST;
    }
    else if(exploreValWest == bestValSoFar) {
      if (random.nextBoolean()) {
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

    switch(direction)
    {
      case NORTH:
        foodGradientVal += mapManager.getFoodProximityVal(x,y-1);
        break;
      case NORTHEAST:
        foodGradientVal += mapManager.getFoodProximityVal(x+1,y-1);
        break;
      case NORTHWEST:
        foodGradientVal += mapManager.getFoodProximityVal(x-1,y-1);
        break;
      case SOUTH:
        foodGradientVal += mapManager.getFoodProximityVal(x,y+1);
        break;
      case SOUTHEAST:
        foodGradientVal += mapManager.getFoodProximityVal(x+1,y+1);
        break;
      case SOUTHWEST:
        foodGradientVal += mapManager.getFoodProximityVal(x-1,y+1);
        break;
      case EAST:
        foodGradientVal += mapManager.getFoodProximityVal(x+1,y);
        break;
      case WEST:
        foodGradientVal += mapManager.getFoodProximityVal(x-1,y);
        break;
    }
    return foodGradientVal;
  }

  private Direction getBestDirectionToFood(int x, int y)
  {
    int foodValNorth = getFoodGradientVal(x,y,Direction.NORTH);
    int bestValSoFar = foodValNorth;
    Direction bestDirection = Direction.NORTH;

    int foodValNE = getFoodGradientVal(x,y,Direction.NORTHEAST);
    if(foodValNE > bestValSoFar)
    {
      bestValSoFar = foodValNE;
      bestDirection = Direction.NORTHEAST;
    }

    int foodValNW = getFoodGradientVal(x,y,Direction.NORTHWEST);
    if(foodValNW > bestValSoFar)
    {
      bestValSoFar = foodValNW;
      bestDirection = Direction.NORTHWEST;
    }

    int foodValSouth = getFoodGradientVal(x,y,Direction.SOUTH);
    if(foodValSouth > bestValSoFar)
    {
      bestValSoFar = foodValSouth;
      bestDirection = Direction.SOUTH;
    }

    int foodValSE = getFoodGradientVal(x,y,Direction.SOUTHEAST);
    if(foodValSE > bestValSoFar)
    {
      bestValSoFar = foodValSE;
      bestDirection = Direction.SOUTHEAST;
    }

    int foodValSW = getFoodGradientVal(x,y,Direction.SOUTHWEST);
    if(foodValSW > bestValSoFar)
    {
      bestValSoFar = foodValSW;
      bestDirection = Direction.SOUTHWEST;
    }

    int foodValEast = getFoodGradientVal(x,y,Direction.EAST);
    if(foodValEast > bestValSoFar)
    {
      bestValSoFar = foodValEast;
      bestDirection = Direction.EAST;
    }

    int foodValWest = getFoodGradientVal(x,y,Direction.WEST);
    if(foodValWest > bestValSoFar)
    {
      bestDirection = Direction.WEST;
    }

    return bestDirection;
  }

  private int getPathGradientVal(int x, int y, Direction direction)
  {
    int pathGradientVal = 0;

    switch(direction)
    {
      case NORTH:
        pathGradientVal += mapManager.getPathProximityVal(x,y-1);
        break;
      case NORTHEAST:
        pathGradientVal += mapManager.getPathProximityVal(x+1,y-1);
        break;
      case NORTHWEST:
        pathGradientVal += mapManager.getPathProximityVal(x-1,y-1);
        break;
      case SOUTH:
        pathGradientVal += mapManager.getPathProximityVal(x,y+1);
        break;
      case SOUTHEAST:
        pathGradientVal += mapManager.getPathProximityVal(x+1,y+1);
        break;
      case SOUTHWEST:
        pathGradientVal += mapManager.getPathProximityVal(x-1,y+1);
        break;
      case EAST:
        pathGradientVal += mapManager.getPathProximityVal(x+1,y);
        break;
      case WEST:
        pathGradientVal += mapManager.getPathProximityVal(x-1,y);
        break;
    }
    return pathGradientVal;
  }

  private Direction getBestDirectionToPath(int x, int y)
  {
    int pathValNorth = getPathGradientVal(x,y,Direction.NORTH);
    int bestValSoFar = pathValNorth;
    Direction bestDirection = Direction.NORTH;

    int pathValNE = getPathGradientVal(x,y,Direction.NORTHEAST);
    if(pathValNE > bestValSoFar)
    {
      bestValSoFar = pathValNE;
      bestDirection = Direction.NORTHEAST;
    }

    int pathValNW = getPathGradientVal(x,y,Direction.NORTHWEST);
    if(pathValNW > bestValSoFar)
    {
      bestValSoFar = pathValNW;
      bestDirection = Direction.NORTHWEST;
    }

    int pathValSouth = getPathGradientVal(x,y,Direction.SOUTH);
    if(pathValSouth > bestValSoFar)
    {
      bestValSoFar = pathValSouth;
      bestDirection = Direction.SOUTH;
    }

    int pathValSE = getPathGradientVal(x,y,Direction.SOUTHEAST);
    if(pathValSE > bestValSoFar)
    {
      bestValSoFar = pathValSE;
      bestDirection = Direction.SOUTHEAST;
    }

    int pathValSW = getPathGradientVal(x,y,Direction.SOUTHWEST);
    if(pathValSW > bestValSoFar)
    {
      bestValSoFar = pathValSW;
      bestDirection = Direction.SOUTHWEST;
    }

    int pathValEast = getPathGradientVal(x,y,Direction.EAST);
    if(pathValEast > bestValSoFar)
    {
      bestValSoFar = pathValEast;
      bestDirection = Direction.EAST;
    }

    int pathValWest = getPathGradientVal(x,y,Direction.WEST);
    if(pathValWest > bestValSoFar)
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
    if(currentGoal != Goal.GOTOFOODSITE)
    {
      return false;
    }

    if(hasPath) //If the ant has a path, follow it
    {
      if(pathStepCount < path.getPath().size()-1) //If the ant has not reached the end of the path
      {
        action.type = AntAction.AntActionType.MOVE;
        action.direction = xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        pathStepCount ++;

      }
      else if (hasPath && pathStepCount == path.getPath().size()-1) //Ant has finished following the path
      {
        endPath();  //Get rid of the old path
        followFoodGradient = true;
      }
    }
    else if(foodObjective != null)
    {
      followFoodGradient = true;
    }

    if(followFoodGradient)
    {
      int distanceToFoodX = Math.abs(antData.gridX - foodObjective.getObjectiveX());
      int distanceToFoodY = Math.abs(antData.gridY - foodObjective.getObjectiveY());

      if( distanceToFoodX <=1 && distanceToFoodY <= 1)  //Ant is adjacent to food, pick it up
      {
        followFoodGradient = false;
        pickUpFood(action);
        return true;
      }
      else    //Follow food diffusion gradient
      {
        action.type = AntAction.AntActionType.MOVE;
        action.direction = getBestDirectionToFood(antData.gridX,antData.gridY);
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

    if(currentGoal != Goal.EXPLORE)
    {
      return false;
    }

    Direction dir;

    if(!randomWalk)
    {
      dir = getBestDirectionToExplore(antData.gridX,antData.gridY);
    }
    else  //If randomly walking in a direction, keep going for 5 paces
    {
      randomSteps++;

      if(randomSteps>=5)
      {
        randomWalk = false;
        randomSteps =0;
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
