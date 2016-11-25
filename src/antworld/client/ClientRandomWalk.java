package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.Random;

import antworld.client.astar.MapReader;
import antworld.client.astar.PathFinder;
import antworld.common.*;
import antworld.common.AntAction.AntActionType;
import antworld.server.Cell;

public class ClientRandomWalk
{
  private static final boolean DEBUG = true;
  private final TeamNameEnum myTeam;
  private static final long password = 962740848319L;//Each team has been assigned a random password.
  private ObjectInputStream inputStream = null;
  private ObjectOutputStream outputStream = null;
  private boolean isConnected = false;
  private NestNameEnum myNestName = null;
  private int centerX, centerY;
  private static PathFinder pathFinder;
  private MapReader mapReader;
  private Cell[][] world;
  private Direction lastDir;

  private Socket clientSocket;


  // A random number generator is created in Constants. Use it.
  // Do not create a new generator every time you want a random number nor
  // even in every class were you want a generator.
  private static Random random = Constants.random;


  public ClientRandomWalk(String host, int portNumber, TeamNameEnum team)
  {
    mapReader = new MapReader("resources/AntTestWorld1.png");
    world = mapReader.getWorld();
//    pathFinder = new PathFinder(world, 60,80 );
    myTeam = team;
    System.out.println("Starting " + team + " on " + host + ":" + portNumber + " at "
        + System.currentTimeMillis());

    isConnected = openConnection(host, portNumber);
    if (!isConnected) System.exit(0);
    CommData data = obtainNest();

    mainGameLoop(data);
    closeAll();
  }

  private boolean openConnection(String host, int portNumber)
  {
    try
    {
      clientSocket = new Socket(host, portNumber);
    }
    catch (UnknownHostException e)
    {
      System.err.println("ClientRandomWalk Error: Unknown Host " + host);
      e.printStackTrace();
      return false;
    }
    catch (IOException e)
    {
      System.err.println("ClientRandomWalk Error: Could not open connection to " + host + " on port " + portNumber);
      e.printStackTrace();
      return false;
    }

    try
    {
      outputStream = new ObjectOutputStream(clientSocket.getOutputStream());
      inputStream = new ObjectInputStream(clientSocket.getInputStream());
    }
    catch (IOException e)
    {
      System.err.println("ClientRandomWalk Error: Could not open i/o streams");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public void closeAll()
  {
    System.out.println("ClientRandomWalk.closeAll()");
    {
      try
      {
        if (outputStream != null) outputStream.close();
        if (inputStream != null) inputStream.close();
        clientSocket.close();
      }
      catch (IOException e)
      {
        System.err.println("ClientRandomWalk Error: Could not close");
        e.printStackTrace();
      }
    }
  }

  /**
   * This method is called ONCE after the socket has been opened.
   * The server assigns a nest to this client with an initial ant population.
   *
   * @return a reusable CommData structure populated by the server.
   */
  private CommData obtainNest()
  {
    CommData data = new CommData(myTeam);
    data.password = password;

    if (sendCommData(data))
    {
      try
      {
        if (DEBUG) System.out.println("ClientRandomWalk: listening to socket....");
        data = (CommData) inputStream.readObject();
        if (DEBUG)
          System.out.println("ClientRandomWalk: received <<<<<<<<<" + inputStream.available() + "<...\n" + data);

        if (data.errorMsg != null)
        {
          System.err.println("ClientRandomWalk***ERROR***: " + data.errorMsg);
          System.exit(0);
        }
      }
      catch (IOException e)
      {
        System.err.println("ClientRandomWalk***ERROR***: client read failed");
        e.printStackTrace();
        System.exit(0);
      }
      catch (ClassNotFoundException e)
      {
        System.err.println("ClientRandomWalk***ERROR***: client sent incorrect common format");
      }
    }
    if (data.myTeam != myTeam)
    {
      System.err.println("ClientRandomWalk***ERROR***: Server returned wrong team name: " + data.myTeam);
      System.exit(0);
    }
    if (data.myNest == null)
    {
      System.err.println("ClientRandomWalk***ERROR***: Server returned NULL nest");
      System.exit(0);
    }

    myNestName = data.myNest;
    centerX = data.nestData[myNestName.ordinal()].centerX;
    centerY = data.nestData[myNestName.ordinal()].centerY;
    System.out.println("ClientRandomWalk: ==== Nest Assigned ===>: " + myNestName);
    return data;
  }

  public void mainGameLoop(CommData data)
  {
    while (true)
    {
      try
      {
        if (DEBUG) System.out.println("ClientRandomWalk: chooseActions: " + myNestName);
        chooseActionsOfAllAnts(data);
        CommData sendData = data.packageForSendToServer();

        System.out.println("ClientRandomWalk: Sending>>>>>>>: " + sendData);
        outputStream.writeObject(sendData);
        outputStream.flush();
        outputStream.reset();

        if (DEBUG) System.out.println("ClientRandomWalk: listening to socket....");
        CommData receivedData = (CommData) inputStream.readObject();
        if (DEBUG)
          System.out.println("ClientRandomWalk: received <<<<<<<<<" + inputStream.available() + "<...\n" + receivedData);
        data = receivedData;

        if ((myNestName == null) || (data.myTeam != myTeam))
        {
          System.err.println("ClientRandomWalk: !!!!ERROR!!!! " + myNestName);
        }
      }
      catch (IOException e)
      {
        System.err.println("ClientRandomWalk***ERROR***: client read failed");
        e.printStackTrace();
        System.exit(0);

      }
      catch (ClassNotFoundException e)
      {
        System.err.println("ServerToClientConnection***ERROR***: client sent incorrect common format");
        e.printStackTrace();
        System.exit(0);
      }
    }
  }

  private boolean sendCommData(CommData data)
  {
    CommData sendData = data.packageForSendToServer();
    try
    {
      if (DEBUG) System.out.println("ClientRandomWalk.sendCommData(" + sendData + ")");
      outputStream.writeObject(sendData);
      outputStream.flush();
      outputStream.reset();
    }
    catch (IOException e)
    {
      System.err.println("ClientRandomWalk***ERROR***: client read failed");
      e.printStackTrace();
      System.exit(0);
    }
    return true;
  }

  private void chooseActionsOfAllAnts(CommData commData)
  {
    for (AntData ant : commData.myAntList)
    {
      AntAction action = chooseAction(commData, ant);
      ant.myAction = action;
    }
  }

  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntActionType.STASIS);

    if (ant.ticksUntilNextAction > 0) return action;

    if (exitNest(ant, action)) return action;

    if (attackAdjacent(ant, action)) return action;

    if (goHomeIfCarryingOrHurt(ant, action)) return action;

    if (lastDir != null)
    {
      if (pickUpFoodAdjacent(ant, action)) return action;

      if (pickUpWater(ant, action)) return action;
    }

    if (goToEnemyAnt(ant, action)) return action;

    if (goToFood(ant, action)) return action;

    if (goToGoodAnt(ant, action)) return action;

    if (goExplore(ant, action)) return action;

    return action;
  }

  //=============================================================================
  // This method sets the given action to EXIT_NEST if and only if the given
  // ant is underground.
  // Returns true if an action was set. Otherwise returns false
  //=============================================================================
  private boolean exitNest(AntData ant, AntAction action)
  {
    if (ant.underground)
    {
      action.type = AntActionType.EXIT_NEST;
      action.x = centerX - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      action.y = centerY - (Constants.NEST_RADIUS - 1) + random.nextInt(2 * (Constants.NEST_RADIUS - 1));
      return true;
    }
    return false;
  }

  private boolean attackAdjacent(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean pickUpFoodAdjacent(AntData ant, AntAction action)
  {
    return false;
  }

  /**
   * @todo make astar paint a path back home with the Rgb value to find a path once and not every turn
   */
  private boolean goHomeIfCarryingOrHurt(AntData ant, AntAction action)
  {
    if (ant.carryUnits == ant.antType.getCarryCapacity())
    {
      System.err.println("GOING BACK HOME");
//      pathFinder.findPath(centerX + Constants.NEST_RADIUS - 1, centerY + Constants.NEST_RADIUS - 1, ant.gridX, ant.gridY);
    }
    return false;
  }

  private boolean pickUpWater(AntData ant, AntAction action)
  {
    if (world[ant.gridX + lastDir.deltaX()][ant.gridY + lastDir.deltaY()].getLandType() == LandType.WATER)
    {
      System.err.println("water!!!!!");
      action.type = AntActionType.PICKUP;
      action.direction = lastDir;
      action.quantity = ant.antType.getCarryCapacity() - 1;
      return true;
    }
    return false;
  }

  private boolean goToEnemyAnt(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean goToFood(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean goToGoodAnt(AntData ant, AntAction action)
  {
    return false;
  }

  private boolean goExplore(AntData ant, AntAction action)
  {
    Direction dir;

    if (ant.carryType == FoodType.WATER)
    {
      dir = Direction.WEST;
    } else
    {
      dir = Direction.EAST;
    }
    action.type = AntActionType.MOVE;
    action.direction = dir;
    lastDir = dir;
    return true;
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

  /**
   * The last argument is taken as the host name.
   * The default host is localhost.
   * Also supports an optional option for the teamname.
   * The default teamname is TeamNameEnum.RANDOM_WALKERS.
   *
   * @param args Array of command-line arguments.
   */
  public static void main(String[] args)
  {
    String serverHost = "localhost";
    if (args.length > 0) serverHost = args[args.length - 1];

    TeamNameEnum team;
    if (DEBUG) team = TeamNameEnum.John_Mauricio;
    else if (args.length > 1)
    {
      team = TeamNameEnum.getTeamByString(args[0]);
    }
    new ClientRandomWalk(serverHost, Constants.PORT, team);
  }
}
