package antworld.client;

import antworld.common.AntData;
import antworld.common.Direction;

/**
 * An enemy objective has an enemy ant that has been targeted to fight
 * Created by John on 12/9/2016.
 */
public class EnemyObjective extends Objective
{
  private AntData enemyData;
  private int enemyID;
  private int enemyHealth;
  private Direction enemyHeading;
  private boolean targeted = false; //True if there is an ant that is assigned to go attack this enemy
  private AntGroup allocatedGroup;
  private boolean stillVisible = true;

  public EnemyObjective(AntData enemyAnt)
  {
    this.objectiveX = enemyAnt.gridX;
    this.objectiveY = enemyAnt.gridY;
    this.enemyID = enemyAnt.id;
    this.enemyHealth = enemyAnt.health;
    this.enemyData = enemyAnt;
    this.enemyHeading = null;
  }

  public boolean isStillVisible()
  {
    return stillVisible;
  }

  public void setStillVisible(boolean state)
  {
    stillVisible = state;
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
    objectiveX = newEnemyData.gridX;
    objectiveY = newEnemyData.gridY;
    enemyHealth = newEnemyData.health;
    enemyData = newEnemyData;

    if(enemyHealth < 0)
    {
      //allocatedGroup.setGroupObjective(null);
      allocatedGroup.setGroupGoal(Goal.EXPLORE);
      targeted = false;
      completed = true;
    }
  }

  public void unallocateGroup()
  {
    //Might need to give the ant a new objective or goal or something??
    if(allocatedGroup != null)
    {
      allocatedGroup.setGroupGoal(Goal.EXPLORE);
    }
    allocatedGroup = null;
    targeted = false;
  }

  public void allocateGroup(AntGroup group)
  {
    allocatedGroup = group;
    group.setGroupObjective(this);
    targeted = true;
  }
}
