package antworld.client;

import antworld.client.astar.MapReader;
import antworld.client.astar.Path;
import antworld.client.astar.PathFinder;
import antworld.common.*;

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
  static MapReader mapReader;
  static CommData data;
  Direction dir, lastDir;
  AntData antData;
  //  AntAction action;
  boolean hasPath = false;
  Path path;
  int pathStepCount;
  private boolean randomWalk = false;
  private int randomSteps = 0;
  private Goal currentGoal = Goal.EXPLORE;
  FoodObjective foodObjective = null;

  Ant(AntData ant)
  {
    this.antData = ant;
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
      //System.err.println("GOING BACK HOME");
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
      }
      else
      {
        System.err.println(centerX + "\ty=" + centerY);
        pathFinder.requestPath(this,ant.gridX, ant.gridY, centerX, centerY);
        action.direction = xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        action.type = AntAction.AntActionType.MOVE;
        pathStepCount ++;
      }
      return true;
    }
    return false;
  }

  /*
  boolean pickUpWater(AntData ant, AntAction action)
  {
    if (lastDir != null)
    {
      //MapCell targetCell = mapReader.getMapCell(ant.gridX + lastDir.deltaX(),ant.gridY + lastDir.deltaY());
      if (world[ant.gridX + lastDir.deltaX()][ant.gridY + lastDir.deltaY()].getLandType() == LandType.WATER)
      {
        action.type = AntAction.AntActionType.PICKUP;
        action.direction = lastDir;
        action.quantity = ant.antType.getCarryCapacity() - 1;
        //return true;
      }
    }
    return false;
  }

  boolean pickUpFood(AntData ant, AntAction action)
  {
    if (lastDir != null)
    {
      //MapCell targetCell = mapReader.getMapCell(ant.gridX + lastDir.deltaX(),ant.gridY + lastDir.deltaY());
      if (world[ant.gridX + lastDir.deltaX()][ant.gridY + lastDir.deltaY()].getFood() != null)
      {
        action.type = AntAction.AntActionType.PICKUP;
        action.direction = lastDir;
        action.quantity = ant.antType.getCarryCapacity() - 1;
        //return true;
      }
    }
    return false;
  }
  */

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
    System.err.println("NO DIRECTION!!!!!!!!!!!");
    return null;
  }

  //Samples two points in the given direction and returns the average exploration value
  //Maybe the average should also consider the cells directly around the ant instead of just around the radius of sight.
  private int getDirectionAverageValue(int x, int y, Direction direction)
  {
    int explorationValue = 0;

    switch(direction)
    {
      case NORTH:
        explorationValue += mapReader.getExplorationVal(x,y-29);
        explorationValue += mapReader.getExplorationVal(x,y-32);
        break;
      case NORTHEAST:
        explorationValue += mapReader.getExplorationVal(x+29,y-29);
        explorationValue += mapReader.getExplorationVal(x+32,y-32);
        break;
      case NORTHWEST:
        explorationValue += mapReader.getExplorationVal(x-29,y-29);
        explorationValue += mapReader.getExplorationVal(x-32,y-32);
        break;
      case SOUTH:
        explorationValue += mapReader.getExplorationVal(x,y+29);
        explorationValue += mapReader.getExplorationVal(x,y+32);
        break;
      case SOUTHEAST:
        explorationValue += mapReader.getExplorationVal(x+29,y+29);
        explorationValue += mapReader.getExplorationVal(x+32,y+32);
        break;
      case SOUTHWEST:
        explorationValue += mapReader.getExplorationVal(x-29,y+29);
        explorationValue += mapReader.getExplorationVal(x-32,y+32);
        break;
      case EAST:
        explorationValue += mapReader.getExplorationVal(x+29,y);
        explorationValue += mapReader.getExplorationVal(x+32,y);
        break;
      case WEST:
        explorationValue += mapReader.getExplorationVal(x-29,y);
        explorationValue += mapReader.getExplorationVal(x-32,y);
        break;
    }
    return explorationValue/2;
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

  //Called when an ant has reached the end of the path it was following.
  private void endPath()
  {
    hasPath = false;
    path = null;
  }

  /*--------------------HAVEN'T WORKED ON BELOW----------------------*/
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
    //If the ant can't see the food, it needs to A* to within 30 pixels of the food - we can always make a larger food gradient too

    if(hasPath) //Ideally this should be updated if another food source is discovered closer to the ant
    {
      //Keep following the path
    }
    else if(foodObjective != null)
    {

    }

    //System.err.println("GOAL = GOTOFOODSITE");
    return true;
  }

  boolean goToGoodAnt(AntData ant, AntAction action)
  {
    return false;
  }

  boolean goExplore(AntData ant, AntAction action)
  {

    if(currentGoal != Goal.EXPLORE)
    {
      return false;
    }

    Direction dir;
    System.out.println("AntID = " + ant.toStringShort());

    if(!randomWalk)
    {
      int exploreValNorth = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.NORTH);
      int bestDirection = exploreValNorth;
      dir = Direction.NORTH;

      int exploreValNE = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.NORTHEAST);
      if(exploreValNE > bestDirection)
      {
        bestDirection = exploreValNE;
        dir = Direction.NORTHEAST;
      }
      else if(exploreValNE == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.NORTHEAST;
          randomWalk = true;
        }
      }

      int exploreValNW = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.NORTHWEST);
      if(exploreValNW > bestDirection)
      {
        bestDirection = exploreValNW;
        dir = Direction.NORTHWEST;
      }
      else if(exploreValNW == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.NORTHWEST;
          randomWalk = true;
        }
      }

      int exploreValSouth = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.SOUTH);
      if(exploreValSouth > bestDirection)
      {
        bestDirection = exploreValSouth;
        dir = Direction.SOUTH;
      }
      else if(exploreValSouth == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.SOUTH;
          randomWalk = true;
        }
      }

      int exploreValSE = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.SOUTHEAST);
      if(exploreValSE > bestDirection)
      {
        bestDirection = exploreValSE;
        dir = Direction.SOUTHEAST;
      }
      else if(exploreValSE == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.SOUTHEAST;
          randomWalk = true;
        }
      }

      int exploreValSW = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.SOUTHWEST);
      if(exploreValSW > bestDirection)
      {
        bestDirection = exploreValSW;
        dir = Direction.SOUTHWEST;
      }
      else if(exploreValSW == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.SOUTHWEST;
          randomWalk = true;
        }
      }

      int exploreValEast = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.EAST);
      if(exploreValEast > bestDirection)
      {
        bestDirection = exploreValEast;
        dir = Direction.EAST;
      }
      else if(exploreValEast == bestDirection)
      {
        if(random.nextBoolean())
        {
          dir = Direction.EAST;
          randomWalk = true;
        }
      }

      int exploreValWest = getDirectionAverageValue(ant.gridX,ant.gridY,Direction.WEST);
      if(exploreValWest > bestDirection)
      {
        dir = Direction.WEST;
      }
      else if(exploreValWest == bestDirection) {
        if (random.nextBoolean()) {
          dir = Direction.WEST;
          randomWalk = true;
        }
      }
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
    //mapReader.addAntStep(ant.gridX,ant.gridY,randomWalk);   //Used for testing
    return true;
  }
}
