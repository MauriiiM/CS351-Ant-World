package antworld.client;

/**
 * An objective has a location and a completion status
 * It will also probably have some sort of diffusion gradient around it
 * Created by John on 12/9/2016.
 */
public abstract class Objective
{

  boolean completed = false;
  int objectiveX = 0;
  int objectiveY = 0;


  public int getObjectiveX()
  {
    return objectiveX;
  }

  public int getObjectiveY()
  {
    return objectiveY;
  }
}
