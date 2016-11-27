package antworld.client;

import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.net.Socket;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Random;

import antworld.client.astar.MapReader;
import antworld.client.astar.PathFinder;
import antworld.common.*;
import antworld.common.AntAction.AntActionType;

public class Client
{
  private static final boolean DEBUG = true;
  private final TeamNameEnum myTeam;
  private static final long password = 962740848319L;//Each team has been assigned a random password.
  private ObjectInputStream inputStream = null;
  private ObjectOutputStream outputStream = null;
  private boolean isConnected = false;
  private NestNameEnum myNestName = null;
  private MapReader mapReader;
  private Socket clientSocket;
  private HashMap<AntData, Ant> dataObjectmap;
  private Ant ant;
  private MapCell[][] world;

  private Client(String host, int portNumber, TeamNameEnum team)
  {
    mapReader = new MapReader("resources/AntTestWorld1.png");
    Ant.world = mapReader.getWorld();
    world = mapReader.getWorld();
    Ant.pathFinder = new PathFinder(Ant.world, mapReader.getMapWidth(), mapReader.getMapHeight());
    myTeam = team;
    dataObjectmap = new HashMap<>();
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
    for (AntData ant : data.myAntList)
    {
      dataObjectmap.put(ant, new Ant(ant));
    }
    while (true)
    {
      try
      {
        if (DEBUG) System.out.println("Client: chooseActions: " + myNestName);
        chooseActionsOfAllAnts(data);
        CommData sendData = data.packageForSendToServer();

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
    this.ant = dataObjectmap.get(ant);

    if(data.foodSet.size() >0)
    {
      System.err.println("FOUND SOME FOOD!!!!");
      Iterator<FoodData> iterator = data.foodSet.iterator();
      int foodX;
      int foodY;
      String foodData;
      while(iterator.hasNext())
      {
        foodX = iterator.next().gridX;
        foodY = iterator.next().gridY;
        foodData = iterator.next().toString();
        System.out.println("Found Food @ (" + foodX + "," + foodY + ") : " + foodData);
        mapReader.updateCellFoodProximity(foodX,foodY);
      }
      //int foodX = data.foodSet.get(0);
      //mapReader.updateCellFoodProximity();
    }

    if (ant.ticksUntilNextAction > 0) return action;

    if (this.ant.exitNest(ant, action)) return action;

    if (this.ant.attackEnemyAnt(ant, action)) return action;

    if (this.ant.goToNest(ant, action)) return action;

    if (this.ant.lastDir != null)
    {
      if (this.ant.pickUpFood(ant, action)) return action;

      if (this.ant.pickUpWater(ant, action)) return action;
    }

    if (this.ant.goToEnemyAnt(ant, action)) return action;

    if (this.ant.goToFood(ant, action)) return action;

    if (this.ant.goToGoodAnt(ant, action)) return action;

    if (this.ant.goExplore(ant, action)) return action;

    return action;
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
    new Client(serverHost, Constants.PORT, team);
  }
}
