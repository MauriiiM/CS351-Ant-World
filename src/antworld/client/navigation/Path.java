package antworld.client.navigation;

import java.util.ArrayList;

/**
 * Path is a route from one point to another defined by a sequence of coordinates derived from a sequence of Steps
 * Created by John on 11/22/2016.
 */
public class Path {

  private Coordinate start;
  private Coordinate end;
  private ArrayList<Coordinate> steps; //The sequence of coordinates between the starting and ending coordinates
  private boolean completed;
  private int travelCost;

  public Path(Coordinate start, Coordinate end)
  {
    completed = false;
    this.start = start;
    this.end = end;
    steps = new ArrayList<>();
    steps.add(start);   //Starting location has no travel cost
    travelCost = 0;
  }

  public Path(Coordinate start, Coordinate end, ArrayList<Coordinate> path, int travelCost)
  {
    completed = true;
    this.start = start;
    this.end = end;
    this.steps = path;
    this.travelCost = travelCost;
  }

  public void addStep(Coordinate nextStep)
  {
    System.out.println("Adding Step: (" + nextStep.getX() + "," + nextStep.getY() + ")");
    steps.add(nextStep);
    travelCost += nextStep.getTravelCost();
  }

  public boolean pathComplete()
  {
    Coordinate testCoord = steps.get(steps.size()-1);

    if(testCoord.getX() == end.getX() && testCoord.getY() == end.getY())
    {
      completed = true;
    }

    return completed;
  }

  public ArrayList<Coordinate> getPath()
  {
    return steps;
  }

  public int getTravelCost()
  {
    return travelCost;
  }

  public Coordinate getPathStart()
  {
    return this.start;
  }

  public Coordinate getPathEnd()
  {
    return this.end;
  }
}
