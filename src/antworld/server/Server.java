package antworld.server;

import java.net.ServerSocket;
import java.net.Socket;
import java.util.ArrayList;
import java.util.HashSet;

import antworld.common.AntData;
import antworld.common.CommData;
import antworld.common.Constants;
import antworld.common.FoodData;
import antworld.common.NestData;
import antworld.common.NestNameEnum;
import antworld.common.TeamNameEnum;
import antworld.server.Nest.NetworkStatus;

public class Server extends Thread
{
  public static final long TIMEOUT_MAX_MSEC_BETWEEN_RECV = 1000 * 60 * 10;
  public static final int TIMEOUT_READ = Constants.TIME_STEP_MSEC*50;
  public static final int TIMEOUT_CONNECT = 1000;
  
  private static final int SERVER_SOCKET_BACKLOG = 10;
  
  private ServerSocket serverSocket = null;
  private ServerToClientConnection[] clientConnectionList;
  private ArrayList<Nest> nestList;
  //private Logger log_prenest;
  
  private volatile ServerToClientConnection connectionBeingCreated = null;
  private AntWorld world;


  public Server(AntWorld world, ArrayList<Nest> nestList)
  {
    this.world = world;
    this.nestList = nestList;
    System.out.println("Server: Opening socket to listen for client connections....");
    try
    {
      serverSocket = new ServerSocket(Constants.PORT, SERVER_SOCKET_BACKLOG);
    }
    catch (Exception e)
    {
      System.err.println("Server: ***ERROR***: Opening socket failed.");
      e.printStackTrace();
      System.exit(-1);
    }
    System.out.println("Server: socket opened on port "+Constants.PORT);
    
    clientConnectionList = new ServerToClientConnection[nestList.size()]; 
//    for (int i=0; i<clientConnectionList.length; i++)
//    {
//      clientConnectionList[i] = new ServerToClientConnection(world, this, nestList.get(i)); 
//    }
    
    //log_prenest = new Logger("Server");
  }
  
  
  public void run()
  {
    while (true)
    {
      int sleepCount = 0;
      while (connectionBeingCreated != null) 
      {
        try {  Thread.sleep(1000); }
        catch (InterruptedException e) { }
        sleepCount++;
        if (sleepCount > 120)
        {
          connectionBeingCreated.closeSocket("***Server() connectionBeingCreated TIMEOUT***");
          connectionBeingCreated = null;
        }
      }
        
      Socket client = null;
      System.out.println("Server: waiting for client connection.....");
      try
      {
        client = serverSocket.accept();
        client.setSoTimeout(TIMEOUT_READ);
      }
      catch (Exception e)
      {
        System.err.println("Server ***ERROR***: Failed to connect to client.");
        try { client.close(); } catch (Exception e2) {}
      }
      
      try
      {
        long timeStartConnect = System.currentTimeMillis();
        connectionBeingCreated = new ServerToClientConnection(this, client);
        
        long timeDoneConnect = System.currentTimeMillis();
        if ((timeDoneConnect - timeStartConnect) > 10*1000) 
        {
          System.err.println("too slow connection time: " + (timeDoneConnect - timeStartConnect));
          client.close();
          connectionBeingCreated = null;
        }
        else
        {
          connectionBeingCreated.start();
        }
      }
      catch (Exception e)
      {
        System.err.println("Server ***ERROR***: Failed to connect to client.");
        closeClient(connectionBeingCreated);
      }
    }
  }
  
  public void doneEstablishingConnection(ServerToClientConnection myConnection) 
  { if (myConnection == connectionBeingCreated) connectionBeingCreated = null;
  }
  
  /*
  
  public int getNestIdxOfTeam(TeamNameEnum team)
  {
    if (team == null) return Nest.INVALID_NEST_ID; 

    for (int i=0; i < nestList.size(); i++)
    { 
      Nest myNest = nestList.get(i);
      if (myNest.team == team) return i;
    }
    
   return Nest.INVALID_NEST_ID; 
  }
  */
  
  
  public Nest getNest(NestNameEnum nestName)
  {
    int nestIdx = nestName.ordinal();
   
    return nestList.get(nestIdx);
  }

  
  public ServerToClientConnection getClient(NestNameEnum nestName)
  {
    int nestIdx = nestName.ordinal();
    return clientConnectionList[nestIdx];
  }
  public ServerToClientConnection getClient(int nestIdx)
  {
    return clientConnectionList[nestIdx];
  }
  
  
  
  public synchronized Nest assignNest(ServerToClientConnection myClientListener)
  {
    TeamNameEnum team = myClientListener.getTeamName();
    if (team == null) return null;
    Nest assignedNest = world.getNest(team);

    if (assignedNest != null)
    {
      myClientListener.getCommData().errorMsg = "Team " + team +
        " already has established nest: "+ assignedNest.nestName;
      System.err.println("Server() **ERROR** " + myClientListener.getCommData().errorMsg);
      return null;
      //if (nest.getNetworkStatus() == NetworkStatus.CONNECTED)
      //{
      //  closeClient(nest.nestName);
        
       //myClientListener.getCommData().errorMsg = "Already connected: " + myClientListener.getNestName()+", team="+team;
          
        //System.err.println("Server() **ERROR** "+myClientListener.getCommData().errorMsg);
        //return null;
      //}
    }

      
    //if (nest.team != TeamNameEnum.NEARLY_BRAINLESS_BOTS)
    //{
     // myClientListener.getCommData().errorMsg = "Already connected: " + myClientListener.getNestName()+", team="+requestNest.team;
    //  System.err.println("Server() **ERROR** "+myClientListener.getCommData().errorMsg);
    //  return null;
   // }

    //int teamsExistingNestId = getNestIdxOfTeam(team);
    //System.out.println("Server.requestNestIdx() team="+team +", teamsExistingNestId="+teamsExistingNestId);
    
    int largestMinDistance = 0;
    ArrayList<FoodSpawnSite> foodSpawnSites = world.getFoodSpawnList();
    for (Nest nest : nestList)
    {
      if (nest.team != TeamNameEnum.NEARLY_BRAINLESS_BOTS) continue;
      int minDistance = Integer.MAX_VALUE;
      for (FoodSpawnSite spawnSite : foodSpawnSites)
      {
        int dx = nest.getCenterX() - spawnSite.getLocationX();
        int dy = nest.getCenterY() - spawnSite.getLocationY();
        int distance = Math.abs(dx) + Math.abs(dy);
        if (distance < minDistance) minDistance = distance;
      }

      if (minDistance > largestMinDistance)
      {
        largestMinDistance = minDistance;
        assignedNest = nest;
      }
    }

    int nestIdx = assignedNest.nestName.ordinal();
    clientConnectionList[nestIdx] = myClientListener;
    assignedNest.setTeam(team);
    assignedNest.spawnInitialAnts(world, team);
    return assignedNest;
  }
  

  
  public void closeClient(NestNameEnum nestName)
  {
    int nestIdx = nestName.ordinal();
    Nest nest = nestList.get(nestIdx);
    if (nest.getNetworkStatus() == NetworkStatus.CONNECTED)
    { nest.setNetworkStatus(NetworkStatus.DISCONNECTED);
    }
    if (clientConnectionList[nestIdx] != null) 
    { 
      try { clientConnectionList[nestIdx].closeSocket("Server.closeClient("+nestName+"): Disconnect"); } 
      catch (Exception e) { }
      clientConnectionList[nestIdx] = null;
    }
  }


  private void closeClient(ServerToClientConnection myClientListener)
  {
    if (myClientListener != null)
    { 
      NestNameEnum nestName = myClientListener.getNestName();
      if (nestName != null)
      { 
    	    int nestIdx = nestName.ordinal();
    	    Nest nest = nestList.get(nestIdx);
    	    if (nest.getNetworkStatus() == NetworkStatus.CONNECTED)
    	    { nest.setNetworkStatus(NetworkStatus.DISCONNECTED);
    	    }  
    	  
        clientConnectionList[nestName.ordinal()] = null;
      }
      
      try { myClientListener.closeSocket("Server.closeClient() Disconnect"); } catch (Exception e) { }
    }
  }
  
  
  
  public static CommData deepCopyCommData(CommData source)
  {
     CommData data = new CommData(source.myTeam);

     data.myNest = source.myNest;
     data.wallClockMilliSec = source.wallClockMilliSec;
     data.gameTick = source.gameTick;

     data.password = source.password;
     data.errorMsg = source.errorMsg;


     data.myAntList = new ArrayList<AntData>();
     for (AntData ant : source.myAntList)
     {
       data.myAntList.add(new AntData(ant));
     }
     
     data.requestNestData = source.requestNestData;
     data.returnToNestOnDisconnect = source.returnToNestOnDisconnect;

     if (source.nestData != null)
     {
       data.nestData = new NestData[source.nestData.length];
       for (int i = 0; i<source.nestData.length; i++)
       {
         data.nestData[i] = Nest.deepCopyNestData(source.nestData[i]);
       }
     }
     
     if (source.foodStockPile != null)
     {
       data.foodStockPile = new int[source.foodStockPile.length];
       for (int i = 0; i<source.foodStockPile.length; i++)
       {
         data.foodStockPile[i] = source.foodStockPile[i];
       }
     }
     
     
     if (source.enemyAntSet != null)
     {
       data.enemyAntSet = new HashSet<AntData>();
       for (AntData ant : source.enemyAntSet)
       {
         data.enemyAntSet.add(new AntData(ant));
       }
     }
     
     
     if (source.foodSet != null)
     {
       data.foodSet = new HashSet<FoodData>();
       for (FoodData food : source.foodSet)
       {
         data.foodSet.add(deepCopyFoodData(food));
       }
     }

     return data;
  }
 
  public static FoodData deepCopyFoodData(FoodData src)
  {
    return new FoodData(src.foodType, src.gridX, src.gridY, src.getCount());
  }


}
