package antworld.client.astar;

import antworld.common.Direction;
import antworld.common.LandType;
import antworld.server.Cell;
import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.PriorityQueue;

/**
 * PathFinder implements the A* algorithm to find the shortest path between two points
 * Created by John on 11/22/2016.
 */
public class PathFinder {

  private Coordinate start;
  private Coordinate end;
  private final int ELEVATIONCOST = 2;
  private StepComparator stepComparator;
  private PriorityQueue stepQueue;
  private final int WORLDWIDTH = 5000;  //Replace with world.getWorldWidth later
  private final int WORLDHEIGHT = 2500;
  private boolean[][] visited;
  private Cell[][] world;
  private int mapWidth;
  private int mapHeight;

  public PathFinder(Cell[][] world, int mapWidth, int mapHeight)
  {
    this.world = world;
    this.mapWidth = mapWidth;
    this.mapHeight = mapHeight;
    //this.elevationCost = elevationCost;
    stepComparator = new StepComparator();
    stepQueue = new PriorityQueue(512, stepComparator);
  }

  private void drawPath(Path path)
  {
    System.out.println("Drawing Path:");
    BufferedImage pathMap = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
    //Color pathColor = new Color(255,0,0,1);
    ArrayList<Coordinate> steps = path.getPath();
    int nextX;
    int nextY;

    for (int i = 0; i < steps.size(); i++)
    {
      nextX = steps.get(i).getX();
      nextY = steps.get(i).getY();

      if(i%10 == 0)
      {
        pathMap.setRGB(nextX, nextY, Color.YELLOW.getRGB());
      }
      else if(i%2 == 0)
      {
        pathMap.setRGB(nextX, nextY, Color.BLUE.getRGB());
      }
      else
      {
        pathMap.setRGB(nextX, nextY, Color.RED.getRGB());

      }
    }
    try
    {
      ImageIO.write(pathMap,"PNG",new File("c:\\Users\\John\\Desktop\\antWorldTest\\pathMapTest2.PNG"));
    } catch (IOException ie){
      ie.printStackTrace();
    }
  }

  //Finds a path from the destination back to the origin
  public Path findPath(int x2, int y2, int x1, int y1)
  {
    visited = new boolean[WORLDWIDTH][WORLDHEIGHT];
    boolean foundPath = false;
    boolean pathBuilt = false;
    this.start = new Coordinate(x1,y1,0);
    this.end = new Coordinate(x2, y2);
    Step firstStep = new Step(start,null,end);
    visited[x1][y1] = true;
    stepQueue.add(firstStep);
    Step nextStep = firstStep;
    ArrayList<Coordinate> steps = new ArrayList<>();
    int pathTravelCost;

    while(!foundPath)
    {
      nextStep = (Step) stepQueue.poll();
      if(nextStep.getLocation().getX() == x2 && nextStep.getLocation().getY() == y2)
      {
        foundPath = true;
        continue;
      }
      else
      {
        addNeighbors(nextStep);
      }
    }

    Step lastStep = nextStep;
    pathTravelCost=lastStep.getTravelCostSoFar();
    System.out.println("Path travelCost = " + pathTravelCost);
    steps.add(lastStep.getLocation());
    while(!pathBuilt)
    {
      Step previousStep = lastStep.getLastStep();
      if(previousStep != null)
      {
        steps.add(previousStep.getLocation());
      }
      else
      {
        pathBuilt = true;
      }
      lastStep=previousStep;
    }

    Path path = new Path(start, end, steps, pathTravelCost);

    return path;
  }

  private void addNeighbors(Step currentStep)
  {
    int currentX = currentStep.getLocation().getX();
    int currentY = currentStep.getLocation().getY();
    int nextX = 0;
    int nextY = 0;

    for(int i=0; i<8;i++)
    {
      switch(i)
      {
        case 0:
          nextX = currentX + Direction.NORTH.deltaX();
          nextY = currentY + Direction.NORTH.deltaY();
          break;
        case 1:
          nextX = currentX + Direction.NORTHEAST.deltaX();
          nextY = currentY + Direction.NORTHEAST.deltaY();
          break;
        case 2:
          nextX = currentX + Direction.NORTHWEST.deltaX();
          nextY = currentY + Direction.NORTHWEST.deltaY();
          break;
        case 3:
          nextX = currentX + Direction.EAST.deltaX();
          nextY = currentY + Direction.EAST.deltaY();
          break;
        case 4:
          nextX = currentX + Direction.WEST.deltaX();
          nextY = currentY + Direction.WEST.deltaY();
          break;
        case 5:
          nextX = currentX + Direction.SOUTH.deltaX();
          nextY = currentY + Direction.SOUTH.deltaY();
          break;
        case 6:
          nextX = currentX + Direction.SOUTHEAST.deltaX();
          nextY = currentY + Direction.SOUTHEAST.deltaY();
          break;
        case 7:
          nextX = currentX + Direction.SOUTHWEST.deltaX();
          nextY = currentY + Direction.SOUTHWEST.deltaY();
          break;
      }

      if(nextX < 0 || nextY < 0)  //If x or y are negative: invalid coordinate
      {
        continue;
      }

      Cell nextCell = world[nextX][nextY];

      if(nextCell.getLandType() == LandType.WATER)
      {
        continue;
      }

      if(visited[nextX][nextY])  //The coordinate has already been visited
      {
        continue;
      }
      else    //Add the new coordinate to the priority queue
      {
        Coordinate currentCoord = currentStep.getLocation();
        Coordinate nextCoord;
        int currentElevation = world[currentCoord.getX()][currentCoord.getY()].getHeight();
        int nextElevation = world[nextX][nextY].getHeight();

        if(nextElevation > currentElevation)  //If the next step in frontier has a higher elevation, raise travel cost
        {
          nextCoord = new Coordinate(nextX,nextY,ELEVATIONCOST);
        }
        else
        {
          nextCoord = new Coordinate(nextX,nextY);
        }

        Step nextStep = new Step(nextCoord,currentStep,end);
        visited[nextX][nextY] = true;
        stepQueue.add(nextStep);
      }
    }
  }

  public static void main(String[] args)
  {
    MapReader mapReader = new MapReader("resources/AntTestWorld11.png");
    PathFinder pathFinder = new PathFinder(mapReader.getWorld(), mapReader.getMapWidth(), mapReader.getMapWidth());
    Path testPath = pathFinder.findPath(62,263,135,196); //Origin (224,162) Target (139,162)
    pathFinder.drawPath(testPath);
  }

  private class StepComparator implements Comparator<Step>
  {
    //***********************************
    //The comparator sorts the paths based on the A* heuristic (travelCost(so far) + distanceToEnd)
    //This method returns zero if the objects are equal.
    //It returns a positive value if path1 is faster than path2. Otherwise, a negative value is returned.
    //***********************************
    @Override
    public int compare(Step step1, Step step2) {

      int step1Distance = step1.getTravelCostSoFar() + step1.getDistanceToEnd();
      int step2Distance = step2.getTravelCostSoFar() + step2.getDistanceToEnd();

      if(step1Distance < step2Distance)
      {
        return -1;
      }

      if(step1Distance > step2Distance)
      {
        return 1;
      }
      return 0;
    }
  }
}
