package antworld.client;

import antworld.common.AntData;

/**
 * An enemy objective has an enemy ant that has been targeted to fight
 * Created by John on 12/9/2016.
 */
public class EnemyObjective extends Objective
{
  private AntData enemyData;
  private int enemyID;
  private int enemyHealth;
  private boolean targeted = false; //True if there is an ant that is assigned to go attack this enemy
  private Ant allocatedAnt;

  public EnemyObjective(AntData enemyAnt)
  {
    this.objectiveX = enemyAnt.gridX;
    this.objectiveY = enemyAnt.gridY;
    this.enemyID = enemyAnt.id;
    this.enemyHealth = enemyAnt.health;
    this.enemyData = enemyAnt;
  }

  public int getEnemyID()
  {
    return enemyID;
  }

  public int getEnemyHealth()
  {
    return enemyHealth;
  }

  public boolean getTargeted()
  {
    return targeted;
  }

  public AntData getEnemyData()
  {
    return enemyData;
  }

  public void updateEnemyData(AntData newEnemyData)
  {
    System.err.println("updatingEnemyData: " + newEnemyData.toString() + " : isAlive = " + newEnemyData.alive);
    objectiveX = newEnemyData.gridX;
    objectiveY = newEnemyData.gridY;
    enemyHealth = newEnemyData.health;
    enemyData = newEnemyData;

    if(enemyHealth <= 0)
    {
      System.err.println("KILLED AN ENEMY!");
      completed = true;
    }
  }

  public void unallocateAnt()
  {
    //Might need to give the ant a new objective or goal or something??
    allocatedAnt = null;
    targeted = false;
  }

  public void allocateAnt(Ant ant)
  {
    allocatedAnt = ant;
    ant.setCurrentObjective(this);
    targeted = true;
  }

}
