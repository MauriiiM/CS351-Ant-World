package antworld.client;

import antworld.client.astar.MapReader;
import antworld.client.astar.Path;
import antworld.client.astar.PathFinder;
import antworld.common.*;
import antworld.server.Cell;

import java.util.Iterator;
import java.util.Random;

/**
 * Each servers ant data is mapped to an Ant object
 * Created by mauricio on 11/23/16.
 */
public class Ant
{
  static Random random = Constants.random;
  static PathFinder pathFinder;
  static int centerX, centerY;
  static MapCell[][] world;
  static MapReader mapReader;
  static CommData data;
  Direction dir, lastDir;
  AntData ant;
  //  AntAction action;
  boolean hasPath = false;
  Path path;
  private boolean randomWalk = false;
  private int randomSteps = 0;

  Ant(AntData ant)
  {
    this.ant = ant;
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
    path = null;
    hasPath = false;
    return AntAction.AntActionType.ENTER_NEST;
  }

  AntAction chooseAction(CommData data, AntData ant, MapReader mapReader)
  {
    AntAction action = new AntAction(AntAction.AntActionType.STASIS);

    if (data.foodSet.size() > 0)
    {
      FoodData nextFood;
      int foodX;
      int foodY;
      String foodData;
      for (Iterator<FoodData> i = data.foodSet.iterator(); i.hasNext(); )
      {
        nextFood = i.next();
        foodX = nextFood.gridX;
        foodY = nextFood.gridY;
        foodData = nextFood.toString();
        System.out.println("Found Food @ (" + foodX + "," + foodY + ") : " + foodData);
        mapReader.updateCellFoodProximity(foodX, foodY);
      }
    }

    if (ant.ticksUntilNextAction > 0) return action;

    if (exitNest(ant, action)) return action;

    if (attackEnemyAnt(ant, action)) return action;

    if (goToNest(ant, action)) return action;

    if (lastDir != null)
    {
      if (pickUpFood(ant, action)) return action;

      if (pickUpWater(ant, action)) return action;
    }

    if (goToEnemyAnt(ant, action)) return action;

    if (goToFood(ant, action)) return action;

    if (goToGoodAnt(ant, action)) return action;

    if (goExplore(ant, action)) return action;

    return action;
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

  //ants do go slower because they are carrying material
  boolean goToNest(AntData ant, AntAction action)
  {
    if (hasPath || ant.carryUnits == ant.antType.getCarryCapacity())
    {
      if (hasPath && path.getPath().size() > 0)
      {
        if (world[ant.gridX][ant.gridY].getLandType() == LandType.NEST)
        {
          action.type = enterNest();
          return true;
        }
        action.type = AntAction.AntActionType.MOVE;
        action.direction = xyCoordinateToDirection(path.getPath().get(0).getX(), path.getPath().get(0).getY(), ant.gridX, ant.gridY);
        path.getPath().remove(0);
      }
      else
      {
        System.err.println(centerX + "\ty=" + centerY);
        path = pathFinder.findPath(ant.gridX, ant.gridY, centerX, centerY);
        hasPath = true;
        action.direction = xyCoordinateToDirection(path.getPath().get(0).getX(), path.getPath().get(0).getY(), ant.gridX, ant.gridY);
        action.type = AntAction.AntActionType.MOVE;
        path.getPath().remove(0);
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
        return true;
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
    return false;
  }

  boolean goToGoodAnt(AntData ant, AntAction action)
  {
    return false;
  }

  boolean goExplore(AntData ant, AntAction action)
  {
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
    mapReader.addAntStep(ant.gridX,ant.gridY,randomWalk);   //Used for testing
    return true;
  }
}
