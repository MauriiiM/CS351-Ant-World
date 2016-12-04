package antworld.client.navigation;

import antworld.common.Util;

/**
 * A step is single movement from an original coordinate to a target location
 * Paths are composed of a sequence of steps
 * Created by John on 11/23/2016.
 */

public class Step
{
  private Coordinate location;
  private Step lastStep;
  private int distanceToEnd;
  private int travelCost;

  public Step(Coordinate location, Step lastStep, Coordinate target)
  {
    this.location = location;
    this.lastStep = lastStep;
    distanceToEnd = Util.manhattanDistance(location.getX(), location.getY(), target.getX(), target.getY());
    if(lastStep != null)
    {
      travelCost = lastStep.getTravelCostSoFar();
    }

    travelCost += location.getTravelCost();
  }

  public Coordinate getLocation()
  {
    return location;
  }

  public int getDistanceToEnd()
  {
    return distanceToEnd;
  }

  public int getTravelCostSoFar()
  {
    return travelCost;
  }

  public Step getLastStep()
  {
    return lastStep;
  }
}
