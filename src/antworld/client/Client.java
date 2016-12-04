package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;

import antworld.client.navigation.MapManager;
import antworld.client.navigation.PathFinder;
import antworld.common.*;
import antworld.common.AntAction.AntActionType;
import antworld.server.Cell;

public class Client
{
  private static final boolean DEBUG = false;
  private final String mapFilePath = "resources/AntTestWorldDiffusion.png";
  private final TeamNameEnum myTeam;
  private static final long password = 962740848319L;//Each team has been assigned a random password.
  private ObjectInputStream inputStream = null;
  private ObjectOutputStream outputStream = null;
  private boolean isConnected = false;
  private NestNameEnum myNestName = null;
  //private MapManager mapManager;
  private Socket clientSocket;
  //private HashMap<Integer, Ant> dataObjectmap;  //Must use ID as the key because antData is constantly changing
  //private Ant ant;
  //private Cell[][] geoMap;
  //private FoodManager foodManager;
  //private PathFinder pathfinder;
  private NestManager nestManager;


  private Client(String host, int portNumber, TeamNameEnum team)
  {
    nestManager = new NestManager(this, mapFilePath);
    //mapManager = new MapManager("resources/AntTestWorldDiffusion.png");
    //geoMap = mapManager.getGeoMap();
    //pathfinder = new PathFinder(geoMap, mapManager.getMapWidth(), mapManager.getMapHeight());
    //Ant.mapManager = mapManager;
    //Ant.pathFinder = pathfinder;
    myTeam = team;
    //dataObjectmap = new HashMap<>();
    //foodManager = new FoodManager(dataObjectmap,pathfinder);
    //foodManager.start();
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
      System.err.println("Client Error: Unknown Host " + host);
      e.printStackTrace();
      return false;
    }
    catch (IOException e)
    {
      System.err.println("Client Error: Could not open connection to " + host + " on port " + portNumber);
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
      System.err.println("Client Error: Could not open i/o streams");
      e.printStackTrace();
      return false;
    }
    return true;
  }

  public void closeAll()
  {
    System.out.println("Client.closeAll()");
    {
      try
      {
        if (outputStream != null) outputStream.close();
        if (inputStream != null) inputStream.close();
        clientSocket.close();
      }
      catch (IOException e)
      {
        System.err.println("Client Error: Could not close");
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
        if (DEBUG) System.out.println("Client: listening to socket....");
        data = (CommData) inputStream.readObject();
        if (DEBUG)
          System.out.println("Client: received <<<<<<<<<" + inputStream.available() + "<...\n" + data);

        if (data.errorMsg != null)
        {
          System.err.println("Client***ERROR***: " + data.errorMsg);
          System.exit(0);
        }
      }
      catch (IOException e)
      {
        System.err.println("Client***ERROR***: client read failed");
        e.printStackTrace();
        System.exit(0);
      }
      catch (ClassNotFoundException e)
      {
        System.err.println("Client***ERROR***: client sent incorrect common format");
      }
    }
    if (data.myTeam != myTeam)
    {
      System.err.println("Client***ERROR***: Server returned wrong team name: " + data.myTeam);
      System.exit(0);
    }
    if (data.myNest == null)
    {
      System.err.println("Client***ERROR***: Server returned NULL nest");
      System.exit(0);
    }

    myNestName = data.myNest;
    Ant.centerX = data.nestData[myNestName.ordinal()].centerX;
    Ant.centerY = data.nestData[myNestName.ordinal()].centerY;
    System.out.println("Client: ==== Nest Assigned ===>: " + myNestName);
    return data;
  }

  /**
   * @param data
   * @todo find a way to add new ants to hashmap, currently just adds first 100, that should also solve when they die
   */
  public void mainGameLoop(CommData data)
  {
    /*
    for (AntData ant : data.myAntList)
    {
      dataObjectmap.put(ant, new Ant(ant));
    }
    */

    nestManager.initializeAntMap(data);

    while (true)
    {
      try
      {
        if (DEBUG) System.out.println("Client: chooseActions: " + myNestName);
        nestManager.chooseActionsOfAllAnts(data);   //Send the commData to the nest manager to work with
        CommData sendData = data.packageForSendToServer();  //Send the commData back to the server onces the nest manager is done

        System.out.println("Client: Sending>>>>>>>: " + sendData);
        outputStream.writeObject(sendData);
        outputStream.flush();
        outputStream.reset();

        if (DEBUG) System.out.println("Client: listening to socket....");
        CommData receivedData = (CommData) inputStream.readObject();
        if (DEBUG)
          System.out.println("Client: received <<<<<<<<<" + inputStream.available() + "<...\n" + receivedData);
        data = receivedData;

        if ((myNestName == null) || (data.myTeam != myTeam))
        {
          System.err.println("Client: !!!!ERROR!!!! " + myNestName);
        }
      }
      catch (IOException e)
      {
        System.err.println("Client***ERROR***: client read failed");
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
      if (DEBUG) System.out.println("Client.sendCommData(" + sendData + ")");
      outputStream.writeObject(sendData);
      outputStream.flush();
      outputStream.reset();
    }
    catch (IOException e)
    {
      System.err.println("Client***ERROR***: client read failed");
      e.printStackTrace();
      System.exit(0);
    }
    return true;
  }

  /*
  private void chooseActionsOfAllAnts(CommData commData)
  {
    mapManager.regenerateExplorationVals();  //LATER: Should be called on seperate thread or something?
    foodManager.readFoodSet(commData.foodSet);

    for (AntData ant : commData.myAntList)
    {
      mapManager.updateCellExploration(ant.gridX,ant.gridY);
      AntAction action = chooseAction(commData, ant);
      ant.myAction = action;
    }
  }

  private AntAction chooseAction(CommData data, AntData ant)
  {
    AntAction action = new AntAction(AntActionType.STASIS);
    this.ant = dataObjectmap.get(ant);

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
  */

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
    //if (DEBUG) team = TeamNameEnum.John_Mauricio;
    if (true) team = TeamNameEnum.John_Mauricio;
    else if (args.length > 1)
    {
      team = TeamNameEnum.getTeamByString(args[0]);
    }
    new Client(serverHost, Constants.PORT, team);
  }
}
