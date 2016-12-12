package antworld.client;

import antworld.client.navigation.PathFinder;
import antworld.common.AntData;
import java.util.*;
import static antworld.client.Ant.mapManager;

/**
 * Manages the list of enemy ants
 * Created by John on 12/3/2016.
 */

public class EnemyManager extends Thread
{
  private ArrayList<EnemyObjective> enemyObjectives;
  private AntData[] enemyAntServerCopy;
  private PriorityQueue<EnemyObjective> unprocessedEnemies;
  private PathFinder pathFinder;
  private volatile boolean running = true;
  private volatile boolean readEnemySet = false;
  private HashMap<Integer, Ant> antMap;
  private final int ENEMYRESPONSEBUFFER = 10;  //How far away can an ant be to get assigned to target an enemy? Enemy distance to nest + buffer

  public EnemyManager(HashMap<Integer, Ant> antMap, PathFinder pathFinder)
  {
    this.antMap = antMap;
    this.pathFinder = pathFinder;       //Will I need to synchronize pathFinder?
    EnemyComparator enemyComparator = new EnemyComparator();
    unprocessedEnemies = new PriorityQueue<>(enemyComparator);
    enemyAntServerCopy = new AntData[0];  //Initialize an empty array to synchronize on the first time its pointer is switched
    enemyObjectives = new ArrayList<>();
  }

  public void setEnemySet(AntData[] newServerEnemySet)
  {
    synchronized (enemyAntServerCopy)  //Get a lock on enemy manager's copy
    {
      enemyAntServerCopy = newServerEnemySet; //Switch pointer
    }
    readEnemySet = true; //Mark new server enemy set to be read
  }

  //Reads an enemy set and updates the manager's own set of enemies
  private void readFoodSet(AntData[] enemyArray)
  {
    System.err.println("reading ENEMY set");
    AntData nextEnemy;
    EnemyObjective nextStoredEnemy;
    int enemyID;
    int storedEnemyID;

    for (int i=0; i<enemyArray.length; i++)  //Iterate over the food array from the server
    {
      nextEnemy = enemyArray[i];
      enemyID = nextEnemy.id;

      if(enemyObjectives.size() == 0)
      {
        addEnemyObjective(nextEnemy);   //Add the objective
        mapManager.updateEnemyAntGradient(nextEnemy.gridX, nextEnemy.gridY); //Create a diffusion gradient around the enemy
      }
      else
      {
        boolean newEnemy = true;

        for(Iterator<EnemyObjective> iterator = enemyObjectives.iterator(); iterator.hasNext();)
        {
          nextStoredEnemy = iterator.next();
          storedEnemyID = nextStoredEnemy.getEnemyID();
          System.err.println("nextStoredEnemy = " + storedEnemyID);

          if(nextStoredEnemy.completed) //If this enemy has been killed
          {
            if(!nextStoredEnemy.getTargeted())  //There are no ants targeting this enemy anymore - important??
            {
              System.err.println("REMOVED ENEMY: enemyData=" + nextStoredEnemy.getEnemyData().toString());
              iterator.remove();                          //Not removing gradient, just letting it fade away - matters?
            }
          }
          else  //If this enemy is still active and unengaged, try to assign an ant to engage it
          {
            if(!nextStoredEnemy.getTargeted() && !unprocessedEnemies.contains(nextStoredEnemy))
            {
              System.err.println("Enemy needs to be engaged! " + nextStoredEnemy.getEnemyData().toString());
              unprocessedEnemies.add(nextStoredEnemy);
            }
          }

          if(storedEnemyID == enemyID)  //If the IDs match, must be an enemy we've seen before
          {
            System.err.println("Found old enemy: " + nextStoredEnemy.getEnemyData().toString());
            nextStoredEnemy.updateEnemyData(nextEnemy); //Update existing site FoodData
            newEnemy = false;
          }
        }

        if(newEnemy)
        {
          addEnemyObjective(nextEnemy);   //Add the objective
          mapManager.updateEnemyAntGradient(nextEnemy.gridX, nextEnemy.gridY); //Create a diffusion gradient around the enemy
        }
      }
    }
  }

  private void addEnemyObjective(AntData enemyAnt)
  {
    EnemyObjective newObjective = new EnemyObjective(enemyAnt);
    enemyObjectives.add(newObjective);   //Add new objective to collection of food objectives
    unprocessedEnemies.add(newObjective);
  }

  //Decides which ants to allocate to an enemy objective
  private void allocateAntsToEnemy(EnemyObjective enemyObjective)
  {
    int enemyX = enemyObjective.objectiveX;
    int enemyY = enemyObjective.objectiveY;
    AntComparator comparator = new AntComparator(enemyX, enemyY);
    PriorityQueue<Ant> antQueue = new PriorityQueue<>(comparator);
    int maxEnemyResponseDistance = NestManager.calculateDistance(enemyX,enemyY,NestManager.NESTX,NestManager.NESTY) + ENEMYRESPONSEBUFFER;

    for (Integer antData : antMap.keySet())
    {
      Ant ant = antMap.get(antData);                                                 //Need to make sure that the ant is not underground either!
      if(ant.getCurrentGoal() == Goal.EXPLORE && !ant.getAntData().underground) //If the ant was not already returning to the nest for a reason/ant is available to go collect food
      {
        antQueue.add(ant);
      }
    }

    while(antQueue.size() > 0 && unprocessedEnemies.size() > 0)
    {
      Ant nextAnt = antQueue.poll();  //Add ant to foodObjective
      AntData nextAntData = nextAnt.getAntData();
      int distanceToFood = NestManager.calculateDistance(enemyX,enemyY,nextAntData.gridX,nextAntData.gridY);
      if(distanceToFood <= maxEnemyResponseDistance)   //If an ant is within the range of the food objective
      {
        //todo Add a way for an ant to compare enemies
        if(nextAnt.getCurrentGoal() != Goal.ATTACK)
        {
          allocateAnt(nextAnt,enemyObjective);  //At the moment just allocates one ant to one enemy
          unprocessedEnemies.remove(enemyObjective);
        }
      }
    }
  }

  private void allocateAnt(Ant ant, EnemyObjective enemyObjective)
  {
    ant.setCurrentGoal(Goal.ATTACK);
    enemyObjective.allocateAnt(ant);      //Allocate ant to new food objective
    AntData antData = ant.getAntData();
    System.err.println("Allocating ant to enemy: " + antData.toString() + " : enemy = " + enemyObjective.getEnemyData().toString());
  }

  @Override
  public void run()
  {
    while(running)
    {
      if(readEnemySet)   //If there's a new enemySet to read, process it.
      {
        synchronized (enemyAntServerCopy)  //Maintain a lock on the food set it's reading
        {
          readFoodSet(enemyAntServerCopy);

          if (unprocessedEnemies.size() > 0) {

            //For testing
            EnemyObjective[] array = new EnemyObjective[unprocessedEnemies.size()];
            unprocessedEnemies.toArray(array);
            for(int i = 0; i < array.length; i++)
            {
              System.err.println("Enemy Queue: \t " + array[i].getEnemyData().toString());
            }

            EnemyObjective nextEnemyObjective = unprocessedEnemies.peek(); //Peek instead of poll in case the food objective can't be processed
            allocateAntsToEnemy(nextEnemyObjective);   //Allocate ants to the new food objective
          }

          readEnemySet = false;  //Done processing food set
        }
      }
    }
  }


  private class EnemyComparator implements Comparator<EnemyObjective>
  {
    //***********************************
    //The comparator compares food sites according to their quantity
    //This method returns zero if the objects are equal.
    //It returns a negative value if food site 1 has more food than food site 2. Otherwise, a positive value is returned.
    //***********************************
    @Override
    public int compare(EnemyObjective enemy1, EnemyObjective enemy2)
    {
      //compare health
      int enemy1Health = enemy1.getEnemyHealth();
      int enemy2Health = enemy2.getEnemyHealth();
      //compare distance in future!!

      if(enemy1Health < enemy2Health)
      {
        return -1;
      }
      else if(enemy1Health > enemy2Health)
      {
        return 1;
      }
      return 0;
    }
  }

  //Compares ants based on their proximity to a food site
  private class AntComparator implements Comparator<Ant>
  {
    private int enemyX;
    private int enemyY;
    private AntComparator(int enemyX, int enemyY)
    {
      this.enemyX = enemyX;
      this.enemyY = enemyY;
    }
    //***********************************
    //The comparator compares ants based on their proximity to a food site
    //This method returns zero if the objects are equal.
    //It returns a negative value if ant1 is closer than ant2. Otherwise, a positive value is returned.
    //***********************************
    @Override
    public int compare(Ant ant1, Ant ant2) {

      int ant1Distance = NestManager.calculateDistance(ant1.getAntData().gridX, ant1.getAntData().gridY, enemyX, enemyY);
      int ant2Distance = NestManager.calculateDistance(ant2.getAntData().gridX, ant2.getAntData().gridY, enemyX, enemyY);

      if(ant1Distance < ant2Distance)
      {
        return -1;
      }
      else if(ant1Distance > ant2Distance)
      {
        return 1;
      }
      return 0;
    }
  }
}
