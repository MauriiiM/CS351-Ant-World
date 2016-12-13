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
  private HashMap<Integer, AntGroup> groupMap;
  private final int ENEMYRESPONSEBUFFER = 10;  //How far away can an ant be to get assigned to target an enemy? Enemy distance to nest + buffer

  public EnemyManager(HashMap<Integer, AntGroup> groupMap, PathFinder pathFinder)
  {
    this.groupMap = groupMap;
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
    //System.err.println("Updated enemy set...");
    readEnemySet = true; //Mark new server enemy set to be read
  }

  //Reads an enemy set and updates the manager's own set of enemies
  private void readEnemySet(AntData[] enemyArray)
  {
    //System.err.println("reading ENEMY set");
    AntData nextEnemy;
    EnemyObjective nextStoredEnemy;
    int enemyID;
    int storedEnemyID;

    //Reset visibility for all enemy objectives. If they are still visible it will be set back to true
    for(Iterator<EnemyObjective> iterator = enemyObjectives.iterator(); iterator.hasNext();) {
      nextStoredEnemy = iterator.next();
      nextStoredEnemy.setStillVisible(false);
    }


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
          //System.err.println("nextStoredEnemy = " + storedEnemyID);

          if(storedEnemyID == enemyID)  //If the IDs match, must be an enemy we've seen before
          {
            System.err.println("Found old enemy: " + nextStoredEnemy.getEnemyData().toString());
            nextStoredEnemy.updateEnemyData(nextEnemy); //Update existing site FoodData
            System.err.println("\t new data: " + nextStoredEnemy.getEnemyData().toString());
            nextStoredEnemy.setStillVisible(true);
            newEnemy = false;
          }

          if(nextStoredEnemy.completed || !nextStoredEnemy.isStillVisible()) //If this enemy has been killed
          {
            System.err.println("REMOVED ENEMY: enemyData=" + nextStoredEnemy.getEnemyData().toString());
            nextStoredEnemy.unallocateGroup();
            iterator.remove();                          //Not removing gradient, just letting it fade away - matters?
          }
          else  //If this enemy is still active and unengaged, try to assign an ant to engage it
          {
            if(!nextStoredEnemy.getTargeted() && !unprocessedEnemies.contains(nextStoredEnemy))
            {
              System.err.println("Enemy needs to be engaged! " + nextStoredEnemy.getEnemyData().toString());
              unprocessedEnemies.add(nextStoredEnemy);
            }
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

  //Decides which group to allocate to an enemy objective
  private void allocateGroupToEnemy(EnemyObjective enemyObjective)
  {
    int enemyX = enemyObjective.objectiveX;
    int enemyY = enemyObjective.objectiveY;
    GroupComparator comparator = new GroupComparator(enemyX, enemyY);
    PriorityQueue<AntGroup> groupQueue = new PriorityQueue<>(comparator);
    int maxEnemyResponseDistance = NestManager.calculateDistance(enemyX,enemyY,NestManager.NESTX,NestManager.NESTY) + ENEMYRESPONSEBUFFER;

    for (Integer id : groupMap.keySet())
    {
      AntGroup group = groupMap.get(id);                                                 //Need to make sure that the group is not underground either!
      if(!group.getGroupLeader().getAntData().underground){
        if(group.getGroupGoal() == Goal.EXPLORE || group.getGroupGoal() == Goal.ATTACK) //If the group was not already returning to the nest for a reason/group is available to fight
        {
          groupQueue.add(group);
        }
      }
    }

    while(groupQueue.size() > 0 && unprocessedEnemies.size() > 0)
    {
      AntGroup nextGroup = groupQueue.poll();  //Add ant to foodObjective
      AntData groupLeaderData = nextGroup.getGroupLeader().getAntData();
      int distanceToFood = NestManager.calculateDistance(enemyX,enemyY,groupLeaderData.gridX,groupLeaderData.gridY);
      if(distanceToFood <= maxEnemyResponseDistance)   //If an ant is within the range of the food objective
      {
        if(nextGroup.getGroupGoal() != Goal.ATTACK)
        {
          allocateGroup(nextGroup,enemyObjective);  //At the moment just allocates one ant to one enemy
          unprocessedEnemies.remove(enemyObjective);
        }
        else if(nextGroup.getGroupGoal() == Goal.ATTACK)
        {
          if(nextGroup.getGroupObjective() == null)
          {
            allocateGroup(nextGroup,enemyObjective);  //At the moment just allocates one ant to one enemy
            unprocessedEnemies.remove(enemyObjective);
          }
          else
          {
            EnemyObjective currentObjective = (EnemyObjective) nextGroup.getGroupObjective();
            if(currentObjective != compareEnemyObjectives(nextGroup,currentObjective,enemyObjective))
            {
              System.err.println("ROUTING TO BETTER ENEMY");
              currentObjective.unallocateGroup();
              allocateGroup(nextGroup,enemyObjective);  //At the moment just allocates one ant to one enemy
              unprocessedEnemies.remove(enemyObjective);
            }
          }
        }
      }
    }
  }

  private void allocateGroup(AntGroup group, EnemyObjective enemyObjective)
  {
    group.setGroupGoal(Goal.ATTACK);
    enemyObjective.allocateGroup(group);      //Allocate ant to new food objective
    System.err.println("Allocating Group " + group.ID + " to enemy: " + enemyObjective.getEnemyData().toString());
  }

  private EnemyObjective compareEnemyObjectives(AntGroup group, EnemyObjective currentEnemy, EnemyObjective otherEnemy)
  {
    int groupX = group.getGroupLeader().getAntData().gridX;
    int groupY = group.getGroupLeader().getAntData().gridY;
    int currentEnemyX = currentEnemy.getObjectiveX();
    int currentEnemyY = currentEnemy.getObjectiveY();
    int otherEnemyX = otherEnemy.getObjectiveX();
    int otherEnemyY = otherEnemy.getObjectiveY();
    int currentEnemyDistance = NestManager.calculateDistance(groupX,groupY,currentEnemyX,currentEnemyY);
    int otherEnemyDistance = NestManager.calculateDistance(groupX,groupY,otherEnemyX,otherEnemyY);

    if(currentEnemyDistance <= otherEnemyDistance) //Current enemy objective is still closer or just as close
    {
      return currentEnemy;
    }
    else
    {
      return otherEnemy;
    }
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
          readEnemySet(enemyAntServerCopy);

          if (unprocessedEnemies.size() > 0) {

            //For testing
            EnemyObjective[] array = new EnemyObjective[unprocessedEnemies.size()];
            unprocessedEnemies.toArray(array);
            for(int i = 0; i < array.length; i++)
            {
              //System.err.println("Enemy Queue: \t " + array[i].getEnemyData().toString());
            }

            EnemyObjective nextEnemyObjective = unprocessedEnemies.peek(); //Peek instead of poll in case the food objective can't be processed
            allocateGroupToEnemy(nextEnemyObjective);   //Allocate ants to the new food objective
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
  private class GroupComparator implements Comparator<AntGroup>
  {
    private int enemyX;
    private int enemyY;
    private GroupComparator(int enemyX, int enemyY)
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
    public int compare(AntGroup group1, AntGroup group2) {

      int group1Distance = NestManager.calculateDistance(group1.getGroupLeader().getAntData().gridX, group1.getGroupLeader().getAntData().gridY, enemyX, enemyY);
      int group2Distance = NestManager.calculateDistance(group2.getGroupLeader().getAntData().gridX, group2.getGroupLeader().getAntData().gridY, enemyX, enemyY);

      if(group1Distance < group2Distance)
      {
        return -1;
      }
      else if(group1Distance > group2Distance)
      {
        return 1;
      }
      return 0;
    }
  }
}
