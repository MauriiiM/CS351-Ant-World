package antworld.client;

import antworld.client.navigation.*;
import antworld.common.*;

import java.util.ArrayList;
import java.util.Random;

/**
 * has precautions to prevent over grouping
 *
 * @todo add central group manager to NestMAnager
 */
public class AntGroup
{
  static final int ANTS_PER_GROUP = 2;//this will need tweeking

  static Random random = Constants.random;
  static PathFinder pathFinder;
  static int centerX, centerY;
  static MapManager mapManager;
  static NestManager nestManager;
  static FoodManager foodManager;
  private static int antsCarryingWater = 0;

  Direction lastDir;
  boolean hasPath = false;
  boolean followFoodGradient = false;
  boolean followOrdered = true;   //True initially, follow leader in orderly fasion until an objective is reached
  boolean followDisorded = false; //If an objective has been reach and completed, follow leader by dead reckoning relative direction
  Path path;
  Path waterPath;
  int pathStepCount;
  int h2oPathStepCount;
  boolean completedLastAction = false;
  private boolean randomWalk = false;
  private boolean checkAttritionDamage = false;
  private int randomSteps = 0;
  private boolean groupStuck = false;
  private int ticksStuckCount = 0;
  private Coordinate lastLeaderPosition;

  public final int ID;
  volatile boolean underground = true; //for the most part only add ant into groups when underground
  private AntAction[] lastAction; //Stores the last action of each ant in the group
  private AntAction[] nextAction; //Stores the next action of each ant in the group
  private ArrayList<Ant> group;
  private Ant groupLeader;
  private boolean fullGroup = false;
  private Goal groupGoal = Goal.EXPLORE;
  private Objective groupObjective;

  public AntGroup(int groupID, Ant leader)
  {
    this.ID = groupID;
    group = new ArrayList<>(ANTS_PER_GROUP);
    lastAction = new AntAction[ANTS_PER_GROUP];
    nextAction = new AntAction[ANTS_PER_GROUP];
    groupLeader = leader;
    groupLeader.setAntGroup(this);
    group.add(groupLeader);
    lastLeaderPosition = new Coordinate(groupLeader.getAntData().gridX, groupLeader.getAntData().gridY);
  }

  public Ant getGroupLeader()
  {
    return groupLeader;
  }

  public void setGroupLeader(Ant newLeader)
  {
    //todo probably need more book keeping than this to switch leader!!
    groupLeader = newLeader;
    lastLeaderPosition = new Coordinate(groupLeader.getAntData().gridX, groupLeader.getAntData().gridY);
  }

  public Goal getGroupGoal()
  {
    return groupGoal;
  }

  public void setGroupGoal(Goal newGoal)
  {
    groupGoal = newGoal;
    for (int i = 0; i < group.size(); i++)
    {
      group.get(i).setCurrentGoal(groupGoal);
    }
  }

  public Objective getGroupObjective()
  {
    return groupObjective;
  }

  public void setGroupObjective(Objective newObjective)
  {
    groupObjective = newObjective;
    for (int i = 0; i < group.size(); i++)
    {
      group.get(i).setCurrentObjective(groupObjective);
    }
  }

  public boolean isFull()
  {
    return fullGroup;
  }

  //Returns true if the group leader is underground
  public boolean isUnderground()
  {
    return groupLeader.getAntData().underground;
  }

  public void addAntToGroup(Ant ant)
  {
    if (!fullGroup)
    {
      group.add(ant);
      ant.setAntGroup(this);
      if (group.size() == ANTS_PER_GROUP)
      {
        fullGroup = true;
      }
    }
  }

  //todo: handle removing leader from group, shifting group indexes down so that the first n indexes are full.
  void removeAntFromGroup(Ant ant)
  {
    group.remove(ant);
    ant.setAntGroup(null);
    fullGroup = false;
  }

  public void setCheckAttritionDamage(boolean state)
  {
    checkAttritionDamage = state;
  }

  private Direction findUnoccupiedDirection(int x, int y)
  {
    boolean foundNewDirection = false;
    Direction newDirection = Direction.NORTH;
    if (!foundNewDirection && !mapManager.getOccupied(x, y - 1))
    {
      newDirection = Direction.NORTH;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x, y + 1))
    {
      newDirection = Direction.SOUTH;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x + 1, y))
    {
      newDirection = Direction.EAST;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x - 1, y))
    {
      newDirection = Direction.WEST;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x, y - 1))
    {
      newDirection = Direction.NORTH;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x + 1, y - 1))
    {
      newDirection = Direction.NORTHEAST;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x - 1, y - 1))
    {
      newDirection = Direction.NORTHWEST;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x + 1, y + 1))
    {
      newDirection = Direction.SOUTHEAST;
      foundNewDirection = true;
    }

    if (!foundNewDirection && !mapManager.getOccupied(x - 1, y + 1))
    {
      newDirection = Direction.SOUTHWEST;
    }

    return newDirection;
  }

  private boolean resetGroup()
  {
    boolean groupReady = true;   //If the entire group is underground, time to send them back out to follow orders to next objective
    for (int i = 0; i < group.size(); i++)
    {
      Ant nextAnt = group.get(i);
      AntData nextData = nextAnt.getAntData();
      if (!nextData.underground || nextAnt.getCurrentGoal() != Goal.EXPLORE || nextData.carryUnits > 0)
      {
        groupReady = false;
      }
    }

    if (groupReady)
    {
      for (int i = 0; i < group.size(); i++)
      {
        Ant nextAnt = group.get(i);
        nextAnt.setLastAction(null);
        nextAnt.setNextAction(null);
      }

      groupGoal = Goal.EXPLORE;
    }

    return groupReady;
  }

  public void chooseGroupActions()
  {
    if (resetGroup())
    {
      followOrdered = true;
    }

    if (followOrdered)
    {
      chooseAntOrders();
    }
    else
    {
      chooseAntActions();
    }
  }

  private void updateLeaderPosition()
  {
    int groupLeaderX = groupLeader.getAntData().gridX;
    int groupLeaderY = groupLeader.getAntData().gridY;
    if (groupLeaderX == lastLeaderPosition.getX() && groupLeaderY == lastLeaderPosition.getY())
    {
      ticksStuckCount++;
      //System.err.println("Leader in same position! ticks stuck= " + ticksStuckCount);
      if (ticksStuckCount > 30)
      {
        groupStuck = true;
      }

      if (ticksStuckCount > 60)
      {
        System.err.println("HAIL MARY: RETURN TO NEST");
        setGroupGoal(Goal.RETURNTONEST);
      }

      if (groupStuck)
      {
//        System.err.println("GROUP " + ID + " STUCK!!!!!!!!!!");
        Direction newDirection = findUnoccupiedDirection(groupLeaderX, groupLeaderY);
        if (groupLeader.getNextAction() != null)
        {
          groupLeader.getNextAction().direction = newDirection;
        }

        groupLeader.getAntData().myAction.direction = newDirection;
        //groupStuck = false;
      }
    }
    else
    {
      if (groupStuck)
      {
        groupStuck = false;
      }
      ticksStuckCount = 0;
      lastLeaderPosition.setX(groupLeaderX);
      lastLeaderPosition.setY(groupLeaderY);
    }
  }

  //Used when ants are choosing their own actions to obtain a nearby objective
  public void chooseAntActions()
  {

    updateLeaderPosition();

    for (int i = 0; i < group.size(); i++)
    {
      Ant nextAnt = group.get(i);
      AntData nextAntData = nextAnt.getAntData();
      if (nextAnt.completedLastAction)
      {
        nextAntData.myAction = nextAnt.chooseAntAction();
//        System.err.println("\t\t CHOSE ACTION: " + nextAntData.toString());
//        System.err.println("\t\t goal=: " + nextAnt.getCurrentGoal().toString() + " HasPath= " + nextAnt.hasPath + " pathstepCount= " + nextAnt.pathStepCount);
//        System.err.println("\t\t following gradient = " + nextAnt.followFoodGradient);
      }
    }
  }

  //Used when ants are following the leaders every move - mostly for moving ants in a group to a location or as they explore
  public void chooseAntOrders()
  {

//    System.err.println("Group: " + ID + " ChooseAntOrders..................Goal = " + groupGoal.toString());

    updateLeaderPosition();

    int longestWait = 0;
    int nextWaitTime;
    int antToWaitFor = 0;
    for (int i = 0; i < group.size(); i++)
    {
      Ant nextAnt = group.get(i);
      nextWaitTime = nextAnt.getAntData().ticksUntilNextAction;

      if (longestWait == 0 && nextWaitTime > 0)
      {
        longestWait = nextWaitTime;
        antToWaitFor = i;
      }
    }

    boolean antLag = false;
    int firstLag = 0;
    //System.out.println("Verifying Last Action....................");
    for (int i = antToWaitFor; i < group.size(); i++)
    {
      Ant nextAnt = group.get(i);
      AntData nextAntData = nextAnt.getAntData();
      //System.out.println("\t i=" + i + " : nextAntData=" + nextAntData.toString());
      //System.out.println("\t\t ticksUntilNextAction=" + nextAntData.ticksUntilNextAction);


      if (nextAnt.getNextAction() != null && nextAntData.myAction.type == nextAnt.getNextAction().type)
      {
        nextAnt.completedLastAction = true;   //Completed action
        nextAnt.setLastAction(nextAnt.getNextAction()); //Store completed action for next ant to copy
        //nextAnt.setNextAction(null);
        //nextAntData.myAction.type = AntAction.AntActionType.STASIS;
      }
      else if (nextAnt.getNextAction() != null)
      {
        if (!antLag)
        {
          firstLag = i;
        }
        //System.err.println("\t\tAnt Lagging! " + nextAntData.toString());
        antLag = true; //An ant is lagging behind, have group wait until all ants have completed their action
        nextAntData.myAction = nextAnt.getNextAction();   //Action not complete, try to perform next action again
      }

      /*
      if(nextAnt.getNextAction() != null)
      {
        System.out.println("\t\t nextAction = " + nextAnt.getNextAction().type + " : lastActionCompleted= " + nextAnt.completedLastAction);
        System.out.println("\t\t nextActionData = " + nextAntData.myAction.type);
      }
      else
      {
        System.out.println("\t\t nextAction = null! : lastActionCompleted= " + nextAnt.completedLastAction);
      }
      */
    }

    if (antLag)
    {
      if (!(firstLag == 1 && groupLeader.getAntData().carryUnits > 0))
      {
        //System.err.println("\t Ant Lag! First lag=" + firstLag);
        for (int i = firstLag - 1; i >= 0; i--)
        {
          Ant antToStall = group.get(firstLag - 1);
          //System.err.println("\tstalling ant: " + antToStall.getAntData().toString());
          antToStall.getAntData().myAction.type = AntAction.AntActionType.STASIS;
        }
        return;
      }
    }

    //System.err.println("Group: " + ID + " Actions verified........" + groupGoal.toString());

    for (int i = 0; i < group.size(); i++)
    {
      Ant nextAnt = group.get(i);
      AntData nextAntData = nextAnt.getAntData();

      if (i == 0 && nextAntData.ticksUntilNextAction <= 1 && (nextAnt.completedLastAction || nextAnt.getNextAction() == null)) //First ant will always be the group leader
      {
        AntAction nextAction = chooseAntAction(groupLeader);
        nextAntData.myAction = nextAction;
        nextAnt.setNextAction(nextAction);
        nextAnt.completedLastAction = false;
      }
      else if (nextAntData.ticksUntilNextAction <= 1)
      {
        if (nextAnt.completedLastAction || nextAnt.getNextAction() == null)
        {
          Ant previousAnt = group.get(i - 1);
          AntAction previousAntAction = previousAnt.getLastAction();
          if (previousAntAction != null)
          {
            nextAntData.myAction = previousAntAction;
            nextAnt.setNextAction(previousAntAction);
            nextAnt.completedLastAction = false;
          }
        }
      }

      //System.err.println("\t i=" + i + " : nextAntData=" + nextAntData.toString());
      //System.err.println("\t\t ticksUntilNextAction=" + nextAntData.ticksUntilNextAction);
      if (nextAnt.getNextAction() != null)
      {
        //System.err.println("\t\t nextAction = " + nextAnt.getNextAction().toString() + " : lastActionCompleted= " + nextAnt.completedLastAction);
      }
      else
      {
        //System.err.println("\t\t lastAction = null! : lastActionCompleted= " + nextAnt.completedLastAction);
      }
    }
  }

  private AntAction chooseAntAction(Ant ant)
  {
    //System.out.println("chooseAntAction()...");
    AntAction antAction = new AntAction(AntAction.AntActionType.STASIS);
    AntData antData = ant.getAntData();

    if (antData.ticksUntilNextAction > 1) return antAction;

    if (exitNest(antData, antAction)) return antAction;

    //if (this.ant.attackEnemyAnt(ant, action)) return action;

    if (goToNest(antData, antAction)) return antAction;

    if (hasWaterPath(antData, antAction)) return antAction;

    if (goToEnemyAnt(antData, antAction)) return antAction;

    if (goToFood(antData, antAction)) return antAction;

    if (goToGoodAnt(antData, antAction)) return antAction;

    if (goExplore(antData, antAction)) return antAction;

    return antAction;
  }

  private void freeAntsFromOrders(Goal goal, Objective objective)
  {
//    System.err.println("FREE ANTS FROM ORDERS");
    /*
    for(int i= 0; i<group.size();i++)
    {
      Ant nextAnt = group.get(i);
      nextAnt.setCurrentGoal(goal);
      nextAnt.setCurrentObjective(objective);
    }
    */
    followOrdered = false;
  }

  void updateAntsCarryingWater(int amountCarrying)
  {
    antsCarryingWater += amountCarrying;
  }

  //only called when ant's in the nest and will be true
  /*
  boolean dropFood(AntData ant, AntAction action)
  {
    action.type = AntAction.AntActionType.DROP;
    action.quantity = ant.carryUnits;
    return true;
  }
  */

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
  boolean exitNest(AntData ant, AntAction action)
  {
    //System.err.println("Exit Nest from AntGroup");
    if (ant.underground)
    {
      if (ant.carryUnits > 0)
      {
        //return dropFood(ant, action);
      }

      if (ant.health < 20)
      {
        return healSelf(action);
      }

//      if (nestManager.getCommData().foodStockPile[0] < (nestManager.getCommData().myAntList.size() * 2) && antsCarryingWater <  nestManager.getCommData().myAntList.size() * 2)//if nest needs water
//      {
//        System.err.println("NEED WATERRRRRRRRRRRRRRRR");
//        if (!hasPath)
//        {
//          System.err.println("DOESNT HAVE PATH");
//          if (waterPath == null) //this will set the local water path
//          {
//            waterPath = pathFinder.findPath(NearestWaterPaths.valueOf(NestManager.NEST_NAME).waterX(), NearestWaterPaths.valueOf(NestManager.NEST_NAME).waterY(), centerX, centerY);
//            h2oPathStepCount = waterPath.getPath().size() - 1;
//          }
//          path = waterPath;
//          antsCarryingWater += ant.antType.getCarryCapacity();
//          hasPath = true;
//          groupGoal = Goal.COLLECT_WATER;
//          action.type = AntAction.AntActionType.EXIT_NEST;
//          action.x = path.getPath().get(h2oPathStepCount).getX();
//          action.y = path.getPath().get(h2oPathStepCount).getY();
//          h2oPathStepCount--;
//          return true;
//        }
//      }

      int exitX = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      int deltaX = Math.abs(centerX - exitX);
      int exitY;
      int deltaY = 20 - deltaX;
//      System.err.println("\t Center : (" + centerX + "," + centerY + ")");
//      System.err.println("\t Delta X=  " + deltaX);
      if (random.nextBoolean())
      {
        exitY = centerY + deltaY;
      }
      else
      {
        exitY = centerY - deltaY;
      }
      underground = false;
      action.type = AntAction.AntActionType.EXIT_NEST;
      action.x = exitX;
      action.y = exitY;
//      System.err.println("\t\t Exit : (" + exitX + "," + exitY + ")");
      return true;
    }
    return false;
  }

  boolean goToNest(AntData ant, AntAction action)
  {
    if (groupGoal == Goal.RETURNTONEST)
    {
      System.err.println("GROUP TRYING TO RETURN TO NEST");

//      if (path == waterPath) pathStepCount += group.size();
      if (hasPath && pathStepCount < path.getPath().size() - 1)
      {
        int deltaX = Math.abs(centerX - ant.gridX);
        int deltaY = Math.abs(centerY - ant.gridY);
        if ((deltaX + deltaY) <= 20)  //If the ant is less than 20 cells away from the center of the nest, it must be on the nest.
        {
          action.type = enterNest();
          endPath();
          groupGoal = Goal.EXPLORE;
          return true;
        }

        action.type = AntAction.AntActionType.MOVE;
        action.direction = Directions.xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        if (ant == groupLeader.getAntData())
        {
          pathStepCount++;
        }

      }
      else if (hasPath && pathStepCount == path.getPath().size() - 1)
      {
        System.err.println("CODE MADE IT TO SECOND ENTER NEST");
        System.exit(7);
        action.type = enterNest();
        endPath();
        groupGoal = Goal.EXPLORE;
      }
      else if (!hasPath && ant.carryUnits > 0)  //If the ant doesn't have a path yet, but is carrying food
      {
        findPathFromFoodToNest(ant, action);
      }
      else if (!hasPath && ant.carryUnits == 0)  //If the ant doesn't have a path, but it needs to go home
      {
        //System.err.println("Ant: " + antData.toString() + " REQUESTING PATH HOME!");
        NestManager.pathFinder.requestGroupPath(this, ant.gridX, ant.gridY, centerX, centerY);
      }
      return true;
    }
    return false;
  }

  private void findPathFromFoodToNest(AntData ant, AntAction action)
  {
    FoodObjective foodObjective = null;
    if (groupObjective instanceof FoodObjective)
    {
      foodObjective = (FoodObjective) groupObjective;
    }
    else
    {
      System.exit(3); //Something has gone wrong.
    }
    Path pathToNest = foodObjective.getPathToNest();
    int distanceToPathStartX = Math.abs(ant.gridX - pathToNest.getPathStart().getX());
    int distanceToPathStartY = Math.abs(ant.gridY - pathToNest.getPathStart().getY());

    if (distanceToPathStartX <= 1 && distanceToPathStartY <= 1)  //Ant is adjacent to path, start to follow it
    {
      setPath(pathToNest);
      foodObjective.unallocateGroup(this);  //Unallocate ant
      foodObjective = null; //Reset food objective
    }
    else    //Follow path diffusion gradient
    {
      action.type = AntAction.AntActionType.MOVE;
      action.direction = getBestDirectionToPath(ant.gridX, ant.gridY);
    }
  }

  private void pickUpFood(AntData ant, AntAction action)
  {
    int foodX = groupObjective.getObjectiveX();
    int foodY = groupObjective.getObjectiveY();
    int antX = ant.gridX;
    int antY = ant.gridY;
    Direction foodDirection = Directions.xyCoordinateToDirection(foodX, foodY, antX, antY);

    action.type = AntAction.AntActionType.PICKUP;
    action.direction = foodDirection;
    action.quantity = ant.antType.getCarryCapacity() - 1;
    FoodObjective foodObjective = (FoodObjective) groupObjective;
    foodObjective.reduceFoodLeft(ant.antType.getCarryCapacity() - 1);

    if (ant == groupLeader.getAntData())
    {
//      System.err.println("Group: " + ID + " : Ant: " + ant.toString() + " : PICKING UP FOOD : foodLeft = " + foodObjective.getFoodLeft());
      setGroupGoal(Goal.RETURNTONEST);
    }
  }

  boolean hasWaterPath(AntData ant, AntAction action)
  {
    if (groupGoal == Goal.COLLECT_WATER)
    {
      action.type = AntAction.AntActionType.MOVE;
      if (path == waterPath && ant.carryUnits == 0)//going for water
      {
        action.direction = Directions.xyCoordinateToDirection(path.getPath().get(h2oPathStepCount).getX(), path.getPath().get(h2oPathStepCount).getY(), ant.gridX, ant.gridY);
        System.err.println("ant @(" + ant.gridX + ", " + ant.gridY + ")" + "\npath @(" + path.getPath().get(h2oPathStepCount).getX() + ", " + path.getPath().get(h2oPathStepCount).getY() + ")");
        if (h2oPathStepCount == 0)
        {
          System.err.println("PICKUP WATER");
          groupGoal = Goal.RETURNTONEST;
          action.type = AntAction.AntActionType.PICKUP;
          action.quantity = ant.antType.getCarryCapacity() - 1;
          return true;
        }
        h2oPathStepCount--;
        //safe check to see if ant actually moved and is where he's supposed to be on path
        if (ant.gridX == path.getPath().get(h2oPathStepCount).getX() && ant.gridY == path.getPath().get(h2oPathStepCount).getY())
          h2oPathStepCount--;
      }
      else if (hasPath && pathStepCount == 3 * path.getPath().size() / 4) //Ant has finished following the path
      {
        endPath();  //Get rid of the old path
        followFoodGradient = true;
      }
      //      action.quantity = ant.antType.getCarryCapacity() - 1;
      return true;
    }
    return false;
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
    Direction oppositeDirection;
    Direction bestDirection;

    if (lastDir != null)
    {
      oppositeDirection = Directions.getOppositeDirection(lastDir);
    }
    else
    {
      oppositeDirection = Directions.xyCoordinateToDirection(centerX, centerY, groupLeader.getAntData().gridX, groupLeader.getAntData().gridY);
    }

    if (oppositeDirection != Direction.NORTH)
    {
      bestDirection = Direction.NORTH;
    }
    else
    {
      bestDirection = Direction.EAST;
    }


    if (oppositeDirection != Direction.NORTHEAST && verifyHeading(x, y, Direction.NORTHEAST))
    {
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
    }

    if (oppositeDirection != Direction.NORTHWEST && verifyHeading(x, y, Direction.NORTHWEST))
    {
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
    }

    if (oppositeDirection != Direction.SOUTH && verifyHeading(x, y, Direction.SOUTH))
    {
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
    }

    if (oppositeDirection != Direction.SOUTHEAST && verifyHeading(x, y, Direction.SOUTHEAST))
    {
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
    }

    if (oppositeDirection != Direction.SOUTHWEST && verifyHeading(x, y, Direction.SOUTHWEST))
    {
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
    }

    if (oppositeDirection != Direction.EAST && verifyHeading(x, y, Direction.EAST))
    {
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
    }

    if (oppositeDirection != Direction.WEST && verifyHeading(x, y, Direction.WEST))
    {
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
    Direction heading = Directions.xyCoordinateToDirection(groupObjective.getObjectiveX(), groupObjective.getObjectiveY(), x, y);
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

    if (bestValSoFar == 0) //If no direction is good, get a general heading and go in that direction
    {
      //System.err.println("DEAD RECKONING...");
      //bestDirection =Directions.xyCoordinateToDirection(foodObjective.getObjectiveX(),foodObjective.getObjectiveY(),x,y);
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

  private int getAverageEnemyGradientVal(int x, int y, Direction direction)
  {
    int enemyGradientVal = 0;

    switch (direction)
    {
      case NORTH:
        enemyGradientVal += mapManager.getEnemyProximityVal(x, y - 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x, y - 29);
        break;
      case NORTHEAST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 1, y - 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 29, y - 29);
        break;
      case NORTHWEST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 1, y - 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 29, y - 29);
        break;
      case SOUTH:
        enemyGradientVal += mapManager.getEnemyProximityVal(x, y + 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x, y + 29);
        break;
      case SOUTHEAST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 1, y + 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 29, y + 29);
        break;
      case SOUTHWEST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 1, y + 1);
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 29, y + 29);
        break;
      case EAST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 1, y);
        enemyGradientVal += mapManager.getEnemyProximityVal(x + 29, y);
        break;
      case WEST:
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 1, y);
        enemyGradientVal += mapManager.getEnemyProximityVal(x - 29, y);
        break;
    }
    return enemyGradientVal / 2;
  }

  private boolean verifyHeading(int x, int y, Direction heading)
  {
    boolean validMove = false;
    LandType cellType;
    boolean occupied;

    switch (heading)
    {
      case NORTH:
        cellType = mapManager.getLandType(x, y - 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x, y - 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case NORTHEAST:
        cellType = mapManager.getLandType(x + 1, y - 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x + 1, y - 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case NORTHWEST:
        cellType = mapManager.getLandType(x - 1, y - 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x - 1, y - 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case SOUTH:
        cellType = mapManager.getLandType(x, y + 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x, y + 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case SOUTHEAST:
        cellType = mapManager.getLandType(x + 1, y + 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x + 1, y + 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case SOUTHWEST:
        cellType = mapManager.getLandType(x - 1, y + 1);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x - 1, y + 1);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case EAST:
        cellType = mapManager.getLandType(x + 1, y);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x + 1, y);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
      case WEST:
        cellType = mapManager.getLandType(x - 1, y);
        if (cellType != null && cellType != LandType.WATER && cellType != LandType.NEST)
        {
          occupied = mapManager.getOccupied(x - 1, y);
          if (!occupied)
          {
            validMove = true;
          }
        }
        break;
    }

    return validMove;
  }

  private Direction getBestDirectionToEnemy(int x, int y)
  {
    Direction heading = Directions.xyCoordinateToDirection(groupObjective.getObjectiveX(), groupObjective.getObjectiveY(), x, y);
    int bestValSoFar;

    //verify that heading is a legal move (not into water/into occupied cell)
    if (verifyHeading(x, y, heading))
    {
      bestValSoFar = 0;
    }
    else
    {
      bestValSoFar = -10;
    }
    Direction bestDirection = heading;

    if (heading == Direction.NORTH || heading == Direction.NORTHEAST || heading == Direction.NORTHWEST)
    {
      if (!mapManager.getOccupied(x, y - 1))
      {
        int enemyValNorth = getAverageEnemyGradientVal(x, y, Direction.NORTH);
        if (enemyValNorth > bestValSoFar)
        {
          bestValSoFar = enemyValNorth;
          bestDirection = Direction.NORTH;
        }
      }
    }

    if (heading == Direction.NORTH || heading == Direction.NORTHEAST || heading == Direction.EAST)
    {
      if (!mapManager.getOccupied(x + 1, y - 1))
      {
        int enemyValNE = getAverageEnemyGradientVal(x, y, Direction.NORTHEAST);
        if (enemyValNE > bestValSoFar)
        {
          bestValSoFar = enemyValNE;
          bestDirection = Direction.NORTHEAST;
        }
      }
    }

    if (heading == Direction.NORTH || heading == Direction.NORTHWEST || heading == Direction.WEST)
    {
      if (!mapManager.getOccupied(x - 1, y - 1))
      {
        int enemyValNW = getAverageEnemyGradientVal(x, y, Direction.NORTHWEST);
        if (enemyValNW > bestValSoFar)
        {
          bestValSoFar = enemyValNW;
          bestDirection = Direction.NORTHWEST;
        }
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHEAST || heading == Direction.SOUTHWEST)
    {
      if (!mapManager.getOccupied(x, y + 1))
      {
        int enemyValSouth = getAverageEnemyGradientVal(x, y, Direction.SOUTH);
        if (enemyValSouth > bestValSoFar)
        {
          bestValSoFar = enemyValSouth;
          bestDirection = Direction.SOUTH;
        }
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHEAST || heading == Direction.EAST)
    {
      if (!mapManager.getOccupied(x + 1, y + 1))
      {
        int enemyValSE = getAverageEnemyGradientVal(x, y, Direction.SOUTHEAST);
        if (enemyValSE > bestValSoFar)
        {
          bestValSoFar = enemyValSE;
          bestDirection = Direction.SOUTHEAST;
        }
      }
    }

    if (heading == Direction.SOUTH || heading == Direction.SOUTHWEST || heading == Direction.WEST)
    {
      if (!mapManager.getOccupied(x - 1, y + 1))
      {
        int enemyValSW = getAverageEnemyGradientVal(x, y, Direction.SOUTHWEST);
        if (enemyValSW > bestValSoFar)
        {
          bestValSoFar = enemyValSW;
          bestDirection = Direction.SOUTHWEST;
        }
      }
    }

    if (heading == Direction.EAST || heading == Direction.NORTHEAST || heading == Direction.SOUTHEAST)
    {
      if (!mapManager.getOccupied(x + 1, y))
      {
        int enemyValEast = getAverageEnemyGradientVal(x, y, Direction.EAST);
        if (enemyValEast > bestValSoFar)
        {
          bestValSoFar = enemyValEast;
          bestDirection = Direction.EAST;
        }
      }
    }

    if (heading == Direction.WEST || heading == Direction.NORTHWEST || heading == Direction.NORTHEAST)
    {
      if (!mapManager.getOccupied(x - 1, y))
      {
        int enemyValWest = getAverageEnemyGradientVal(x, y, Direction.WEST);
        if (enemyValWest > bestValSoFar)
        {
          bestDirection = Direction.WEST;
        }
      }
    }
    return bestDirection;
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

  private void attackEnemyAnt(AntData ant, AntAction action)
  {
    int enemyX = groupObjective.getObjectiveX();
    int enemyY = groupObjective.getObjectiveY();
    int antX = ant.gridX;
    int antY = ant.gridY;
    Direction enemyDirection = Directions.xyCoordinateToDirection(enemyX, enemyY, antX, antY);

    action.type = AntAction.AntActionType.ATTACK;
    action.direction = enemyDirection;

    EnemyObjective enemyObjective = (EnemyObjective) groupObjective;
  }

  boolean goToEnemyAnt(AntData ant, AntAction action)
  {
    if (groupGoal != Goal.ATTACK)
    {
      return false;
    }

//    System.err.println("GoToEnemy Ant: " + ant.toString());

    int distanceToEnemyX = Math.abs(ant.gridX - groupObjective.getObjectiveX());
    int distanceToEnemyY = Math.abs(ant.gridY - groupObjective.getObjectiveY());

    if (distanceToEnemyX <= 5 && distanceToEnemyY <= 5)  //Ant is adjacent to food, pick it up
    {
      //attackEnemyAnt(ant,action);
      freeAntsFromOrders(groupGoal, groupObjective);
      return true;
    }
    else    //Follow enemy diffusion gradient
    {
      action.type = AntAction.AntActionType.MOVE;
      action.direction = getBestDirectionToEnemy(ant.gridX, ant.gridY);
    }

    return true;
  }

  boolean goToFood(AntData ant, AntAction action)
  {
    if (groupGoal != Goal.GOTOFOODSITE)
    {
      return false;
    }
//    System.err.println("GoToFood Ant: " + ant.toString() + " hasPath = " + hasPath + " : followGradient= " + followFoodGradient);
    if (hasPath) //If the ant has a path, follow it
    {
      if (pathStepCount < path.getPath().size() - 1) //If the ant has not reached the end of the path
      {
//        System.err.println("Ant: " + ant.toString() + " FOLLOWING PATH");
        action.type = AntAction.AntActionType.MOVE;
        action.direction = Directions.xyCoordinateToDirection(path.getPath().get(pathStepCount).getX(), path.getPath().get(pathStepCount).getY(), ant.gridX, ant.gridY);
        pathStepCount++;

      }
      else if (hasPath && pathStepCount == path.getPath().size() - 1) //Ant has finished following the path
      {
        endPath();  //Get rid of the old path
        followFoodGradient = true;
      }
    }
    else if (groupObjective != null)
    {
      followFoodGradient = true;
    }

    if (followFoodGradient)
    {

      int distanceToFoodX = Math.abs(ant.gridX - groupObjective.getObjectiveX());
      int distanceToFoodY = Math.abs(ant.gridY - groupObjective.getObjectiveY());

//      System.err.println("Ant: " + ant.toString() + " FOLLOWING GRADIENT : distToFoodX = " + distanceToFoodX + " : distToFoodY = " + distanceToFoodY);

      if (distanceToFoodX <= 5 && distanceToFoodY <= 5)  //Ant is adjacent to food, pick it up
      {
        freeAntsFromOrders(Goal.GOTOFOODSITE, groupObjective);
        //followFoodGradient = false;
        //pickUpFood(ant,action);
        return true;
      }
      else    //Follow food diffusion gradient
      {
        action.type = AntAction.AntActionType.MOVE;
        action.direction = getBestDirectionToFood(ant.gridX, ant.gridY);
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

    if (groupGoal != Goal.EXPLORE)
    {
      System.err.println("GROUP GOAL IS NOT EXPLORE");
      return false;
    }

    //todo: pull this out and have the group do it for all ants
    /*
    if(checkAttritionDamage)  //Called once every 5,000 frames (when an ant should have lost 1 health of attrition damage)
    {
      int distanceToNest = NestManager.calculateDistance(ant.gridX,ant.gridY,centerX,centerY);
      int ticksToGetHome = distanceToNest*2;  //Not accounting for elevation
      int approxTripAttrition = ticksToGetHome/5000; //5000 ticks per unit of attrition damage

      if(ant.health - approxTripAttrition <= 8) //If the ant can make it home with 8 or less health, it should head back to the nest
      {
        groupGoal = Goal.RETURNTONEST;
      }

      checkAttritionDamage = false;
    }
    */

    Direction dir;

    if (!randomWalk)
    {
      dir = getBestDirectionToExplore(ant.gridX, ant.gridY);
//      System.err.println("Group" + ID + " best direction to explore = " + dir.toString());
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
