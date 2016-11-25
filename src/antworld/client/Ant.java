package antworld.client;

import antworld.client.astar.Path;
import antworld.client.astar.PathFinder;
import antworld.common.*;
import antworld.server.Cell;

import java.util.Random;

/**
 * Created by mauricio on 11/23/16.
 */
public abstract class Ant
{
  static PathFinder pathFinder;
  static int antsUnderground;
  static Cell[][] world;
  Direction dir, lastDir;
  int centerX, centerY;
//  AntAction action;
  boolean hasPath;
  Path path;

  // A random number generator is created in Constants. Use it.
  // Do not create a new generator every time you want a random number nor
  // even in every class were you want a generator.
  static Random random = Constants.random;


  boolean attackEnemyAnt(AntData ant, AntAction action)
  {
    return false;
  }

  //=============================================================================
  // This method sets the given action to EXIT_NEST if and only if the given
  // ant is underground.
  // Returns true if an action was set. Otherwise returns false
  //=============================================================================
  protected boolean exitNest(AntData ant, AntAction action)
  {
    if (ant.underground)
    {
      action.type = AntAction.AntActionType.EXIT_NEST;
      action.x = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      action.y = centerY - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      return true;
    }
    return false;
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

  protected boolean pickUpWater(AntData ant, AntAction action)
  {
    if (lastDir != null)
    {
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

  protected boolean pickUpFood(AntData ant, AntAction action)
  {
    if (lastDir != null)
    {
      if (world[ant.gridX + lastDir.deltaX()][ant.gridY + lastDir.deltaY()].getFood() != null)
      {
        action.type = AntAction.AntActionType.PICKUP;
        action.direction = lastDir;
        action.quantity = ant.antType.getCarryCapacity() - 1;
        return true;
      }
    }
    return false;
  }

  /*--------------------HAVEN'T WORKED ON BELOW----------------------*/
  /**
   * @todo make astar paint a path back home with the Rgb value to find a path once and not every turn
   */
  protected boolean goToNest(AntData ant, AntAction action)
  {
    if (ant.carryUnits == ant.antType.getCarryCapacity())
    {
      System.err.println("GOING BACK HOME");
//      pathFinder.findPath(centerX + Constants.NEST_RADIUS - 1, centerY + Constants.NEST_RADIUS - 1, ant.gridX, ant.gridY);
    }
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

    if (ant.carryType == FoodType.WATER)
    {
      dir = Direction.WEST;
    } else
    {
      dir = Direction.EAST;
    }
    action.type = AntAction.AntActionType.MOVE;
    action.direction = dir;
    lastDir = dir;
    return true;
  }
}
