package antworld.client.navigation;

/**
 * A coordinate is an X,Y location on the map defined by int x and int y
 * Created by John on 11/22/2016.
 */
public class Coordinate {

  private int x;
  private int y;
  private int travelCost;

  public Coordinate(int x, int y)
  {
    this.x = x;
    this.y = y;
    travelCost = 1;
  }

  public Coordinate(int x, int y, int travelCost)
  {
    this.x = x;
    this.y = y;
    this.travelCost = travelCost;
  }

  public int getX()
  {
    return this.x;
  }

  public void setX(int newX)
  {
    this.x = newX;
  }

  public int getY()
  {
    return this.y;
  }

  public void setY(int newY)
  {
    this.y = newY;
  }

  public int getTravelCost()
  {
    return travelCost;
  }
}
