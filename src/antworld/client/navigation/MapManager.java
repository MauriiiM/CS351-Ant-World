package antworld.client.navigation;

import antworld.common.LandType;
import antworld.server.Cell;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedList;

import static antworld.client.navigation.GradientType.EXPLORE;
import static antworld.client.navigation.GradientType.FOOD;
import static antworld.client.navigation.GradientType.PATH;

/**
 * Reads a map and creates an array of Cells to represent the world
 * The server creates a Cell array, this will create a copy for the client
 * Used for terrain/elevation checking when path finding.
 * Created by John on 11/23/2016.
 */
public class MapManager
{
  private String imagePath = null;
  private BufferedImage map = null;
  private MapCell[][] world;  //This is used by map reader to store information
  private Cell[][] geoMap;  //This is used by pathfinder to read geographical information
  private int mapWidth;
  private int mapHeight;
  private static final int DIFFUSIONCOEFFICIENT = 30;  //Lowering the value will reduce diffusion rate
  private static final int EXPLORATIONRADIUS = 30;
  private static final int EXPLORATIONVALUE = 1000;
  private static final int EXPLORATIONREGENVAL = 15;
  private static final int FOODRADIUS = 30;
  private static final int FOODVALUE = 1000;
  private static final int PATHSTARTRADIUS = 5;
  private static final int PATHSTARTVALUE = 1000;
  private static final int ENEMYRADIUS = 10;
  private static final int ENEMYVALUE = 500;
  private static final int ENEMYFADEVAL = 30;
  private DiffusionGradient explorationGradient;
  private DiffusionGradient foodGradient;
  private DiffusionGradient waterGradient;
  private DiffusionGradient pathStartGradient;
  private DiffusionGradient enemyGradient;
  private DiffusionGradient enemyNestGradient;//@todo need to find which nest is closest to which to initialize this
  private HashSet<MapCell> exploredRecently;
  private LinkedList<Coordinate> occupiedRecently;
  private HashSet<MapCell> hostileRecently;

  ArrayList<AntStep> antSteps = new ArrayList<>();

  public MapManager(String mapPath)
  {
    this.imagePath = mapPath;
    map = loadMap(imagePath);
    readMap(map);
    exploredRecently = new HashSet<>();
    occupiedRecently = new LinkedList<>();
    hostileRecently = new HashSet<>();
    explorationGradient = new DiffusionGradient(EXPLORATIONRADIUS, EXPLORATIONVALUE, DIFFUSIONCOEFFICIENT);
    foodGradient = new DiffusionGradient(FOODRADIUS, FOODVALUE, DIFFUSIONCOEFFICIENT);
    waterGradient = new DiffusionGradient(FOODRADIUS, FOODVALUE, DIFFUSIONCOEFFICIENT);
    pathStartGradient = new DiffusionGradient(PATHSTARTRADIUS, PATHSTARTVALUE, DIFFUSIONCOEFFICIENT);
    enemyNestGradient = new DiffusionGradient(30, 1000, DIFFUSIONCOEFFICIENT);
    enemyGradient = new DiffusionGradient(ENEMYRADIUS, ENEMYVALUE, DIFFUSIONCOEFFICIENT);
    //drawMap();  //Used for testing
  }

  private BufferedImage loadMap(String imagePath)
  {
    BufferedImage map = null;

    try
    {
      URL fileURL = new URL("file:" + imagePath);
      map = ImageIO.read(fileURL);
    }
    catch (IOException e)
    {
      System.out.println("Cannot Open image: " + imagePath);
      e.printStackTrace();
      System.exit(0);
    }

    return map;
  }

  private void readMap(BufferedImage map)
  {
    mapWidth = map.getWidth();
    mapHeight = map.getHeight();
    world = new MapCell[mapWidth][mapHeight];
    geoMap = new Cell[mapWidth][mapHeight];
    for (int x = 0; x < mapWidth; x++)
    {
      for (int y = 0; y < mapHeight; y++)
      {
        int rgb = (map.getRGB(x, y) & 0x00FFFFFF);
        LandType landType = LandType.GRASS;
        int height = 0;
        if (rgb == 0x0)
        {
          landType = LandType.NEST;
        }
        if (rgb == 0xF0E68C)
        {
          landType = LandType.NEST;
        }
        else if (rgb == 0x1E90FF)
        {
          landType = LandType.WATER;
        }
        else
        {
          int g = (rgb & 0x0000FF00) >> 8;

          height = g - 55;
        }
        // System.out.println("("+x+","+y+") rgb="+rgb +
        // ", landType="+landType
        // +" height="+height);
        world[x][y] = new MapCell(landType, height, x, y);
        geoMap[x][y] = new Cell(landType, height, x, y);
      }
    }
  }

  public Cell[][] getGeoMap()
  {
    return geoMap;
  }

  public int getMapWidth()
  {
    return mapWidth;
  }

  public int getMapHeight()
  {
    return mapHeight;
  }

  public void printFoodMap()
  {
    int nextVal;
    synchronized (world)
    {
      System.out.println("FOOD MAP:*****************************************************************************************************************************");
      for (int j = 0; j < mapHeight; j++)
      {
        for (int cellLine = 0; cellLine < 4; cellLine++)
        {
          for (int i = 0; i < mapWidth; i++)
          {

            if (world[i][j].getLandType() != LandType.WATER)
            {
              if (cellLine == 0) //Print food val
              {
                nextVal = world[i][j].getFoodProximityVal();
                if (nextVal == 0)
                {
                  if (world[i][j].getOccupied())
                  {
                    System.out.print("*@@*|");
                  }
                  else
                  {
                    System.out.print("...0|");
                  }

                }
                else if (nextVal < 10)  //1 digit number
                {
                  System.out.print("..." + nextVal + "|");
                }
                else if (nextVal < 100) //2 digit number
                {
                  System.out.print(".." + nextVal + "|");
                }
                else if (nextVal < 1000) //3 digit number
                {
                  System.out.print("." + nextVal + "|");
                }
                else //4 digit number
                {
                  System.out.print(nextVal + "|");
                }
              }
              else if (cellLine == 1) // print X coord
              {
                nextVal = i;
                if (nextVal == 0)
                {
                  System.out.print("...0|");
                }
                else if (nextVal < 10)  //1 digit number
                {
                  System.out.print("..." + nextVal + "|");
                }
                else if (nextVal < 100) //2 digit number
                {
                  System.out.print(".." + nextVal + "|");
                }
                else if (nextVal < 1000) //3 digit number
                {
                  System.out.print("." + nextVal + "|");
                }
                else //4 digit number
                {
                  System.out.print(nextVal + "|");
                }
              }
              else if (cellLine == 2) // print y coord
              {
                nextVal = j;
                if (nextVal == 0)
                {
                  System.out.print("...0|");
                }
                else if (nextVal < 10)  //1 digit number
                {
                  System.out.print("..." + nextVal + "|");
                }
                else if (nextVal < 100) //2 digit number
                {
                  System.out.print(".." + nextVal + "|");
                }
                else if (nextVal < 1000) //3 digit number
                {
                  System.out.print("." + nextVal + "|");
                }
                else //4 digit number
                {
                  System.out.print(nextVal + "|");
                }
              }
              else if (cellLine == 3)  //Print bottom
              {
                System.out.print("....|");
              }
            }
            else
            {
              System.out.print("~~~~|");
            }
          }
          System.out.println("");
        }
      }
      System.out.println("END MAP:*****************************************************************************************************************************");
    }
  }

  public void fadeEnemyProximityGradient()
  {
    synchronized (world)
    {

      for (Iterator<MapCell> i = hostileRecently.iterator(); i.hasNext(); )
      {
        MapCell nextCell = i.next();
        int currentVal = nextCell.getEnemyVal();
        if (currentVal > 0) //1000-15 = 985
        {
          currentVal -= ENEMYFADEVAL;
          if (currentVal < 0)
          {
            currentVal = 0;
          }
          nextCell.setEnemyVal(currentVal);
        }
        else  //Else cell has regenerated, remove it.
        {
          i.remove();
        }
      }
    }
  }

  public void updateEnemyAntGradient(int enemyX, int enemyY)
  {
    writeGradientToMap(enemyX, enemyY, ENEMYRADIUS, enemyGradient.getGradient(), GradientType.ENEMY);
  }

  public void createPathStartGradient(int pathStartX, int pathStartY)
  {
    writeGradientToMap(pathStartX, pathStartY, PATHSTARTRADIUS, pathStartGradient.getGradient(), GradientType.PATH);
  }

  public void createWaterProximityGradient(int waterX, int waterY)
  {
    writeGradientToMap(waterX, waterY, FOODRADIUS, waterGradient.getGradient(), GradientType.FOOD);
  }

  public void removePathProximityGradient(int x, int y)
  {
    eraseGradientFromMap(x, y, PATHSTARTRADIUS, GradientType.PATH);
  }

  public void updateCellFoodProximity(int foodX, int foodY)
  {
    writeGradientToMap(foodX, foodY, FOODRADIUS, foodGradient.getGradient(), GradientType.FOOD);
  }

  public void removeFoodProximityGradient(int foodX, int foodY)
  {
    eraseGradientFromMap(foodX, foodY, FOODRADIUS, GradientType.FOOD);
  }

  public void updateCellExploration(int explorerX, int explorerY)
  {
    writeGradientToMap(explorerX, explorerY, EXPLORATIONRADIUS, explorationGradient.getGradient(), GradientType.EXPLORE);
  }

  public int getFoodProximityVal(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return 0;
    }

    synchronized (world)
    {
      return world[x][y].getFoodProximityVal();
    }
  }

  public int getExplorationVal(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return 0;
    }

    synchronized (world)
    {
      return world[x][y].getExplorationVal();
    }
  }

  public int getPathProximityVal(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return 0;
    }

    synchronized (world)
    {
      return world[x][y].getPathVal();
    }
  }

  public int getEnemyProximityVal(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return 0;
    }

    synchronized (world)
    {
      return world[x][y].getEnemyVal();
    }
  }

  public LandType getLandType(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return null;
    }

    synchronized (world)
    {
      return world[x][y].getLandType();
    }
  }

  public boolean getOccupied(int x, int y)
  {
    if (x < 0 || x >= mapWidth || y < 0 || y >= mapHeight)
    {
      return true;  //Return true if out of bounds so that nothing tries to go there.
    }

    synchronized (world)
    {
      return world[x][y].getOccupied();
    }
  }

  //Regenerates the exploration value of MapCells that have been explored recently
  public void regenerateExplorationVals()   //Call every frame
  {
    if (exploredRecently.size() > 0)
    {
      synchronized (world)
      {

        for (Iterator<MapCell> i = exploredRecently.iterator(); i.hasNext(); )
        {
          MapCell nextCell = i.next();
          int currentVal = nextCell.getExplorationVal();
          if (currentVal < (EXPLORATIONVALUE - EXPLORATIONREGENVAL)) //1000-15 = 985
          {
            nextCell.setExplorationVal(currentVal + EXPLORATIONREGENVAL);
          }
          else  //Else cell has regenerated, remove it.
          {
            i.remove();
          }
        }
      }
    }
  }

  public void updateCellOccupied(LinkedList<Coordinate> antLocations)
  {
    resetOccupiedCells(); //Set the cells occupied last tick to false
    synchronized (world)
    {
      for (Coordinate nextCoord : antLocations)
      {
        world[nextCoord.getX()][nextCoord.getY()].setOccupied(true);
      }
    }
    occupiedRecently = antLocations;  //Give occupiedRecently a reference to the new list of antLocations
  }

  private void resetOccupiedCells()
  {
    if (occupiedRecently.size() > 0)
    {
      synchronized (world)
      {
        for (Coordinate nextCoord : occupiedRecently)
        {
          world[nextCoord.getX()][nextCoord.getY()].setOccupied(false);
        }
      }
    }
  }

  /**
   * Writes the diffusion gradient to the map by updating cells with a new food proximity value
   *
   * @param x               - target x coord of center of gradient
   * @param y               - target y coord of center of gradient
   * @param radius          - the radius of the diffusion gradient
   * @param diffusionValues - the array of diffusion values
   */
  private void writeGradientToMap(int x, int y, int radius, int[][] diffusionValues, GradientType type)
  {
    synchronized (world)
    {
      int diameter = (radius * 2) + 1;
      int nextX;
      int nextY;
      int nextVal;

      for (int i = 0; i < diameter; i++)
      {

        if (i < radius)
        {
          nextX = x - (radius - i);
        }
        else if (i > radius)
        {
          nextX = x + (i - radius);
        }
        else
        {
          nextX = x;
        }

        for (int j = 0; j < diameter; j++)
        {


          if (j < radius)
          {
            nextY = y - (radius - j);
          }
          else if (j > radius)
          {
            nextY = y + (j - radius);
          }
          else
          {
            nextY = y;
          }

          if (nextX < 0 || nextX >= mapWidth || nextY < 0 || nextY >= mapHeight)
          {
            continue;
          }

          nextVal = diffusionValues[i][j];

          if (world[nextX][nextY].getLandType() != LandType.WATER) //If this is not a water cell
          {
            switch (type)
            {
              case FOOD:
                int currentVal = world[nextX][nextY].getFoodProximityVal(); //This prevents gradients from overlapping when food sites are close
                if (nextVal > currentVal)  //Only update this cell if the next gradient value is higher than the cells current value
                {
                  world[nextX][nextY].setFoodProximityVal(nextVal);
                }
                break;
              case EXPLORE:
                exploredRecently.add(world[nextX][nextY]);  //Add the new cell to exploredRecently to regenerate its exploration over time
                nextVal *= EXPLORE.polarity();
                nextVal = world[nextX][nextY].getExplorationVal() + nextVal;
                if (nextVal < 0)
                {
                  nextVal = 0;
                }
                world[nextX][nextY].setExplorationVal(nextVal);
                break;
              case PATH:
                world[nextX][nextY].setPathVal(nextVal);
                break;
              case ENEMY:
                int currentEnemyVal = world[nextX][nextY].getEnemyVal();
                if (nextVal > currentEnemyVal)
                {
                  hostileRecently.add(world[nextX][nextY]);
                  world[nextX][nextY].setEnemyVal(nextVal);
                }
                break;
            }
          }
        }
      }
    }
  }

  private void eraseGradientFromMap(int x, int y, int radius, GradientType type)
  {
    synchronized (world)
    {
      int diameter = (radius * 2) + 1;
      int nextX;
      int nextY;

      for (int i = 0; i < diameter; i++)
      {

        if (i < radius)
        {
          nextX = x - (radius - i);
        }
        else if (i > radius)
        {
          nextX = x + (i - radius);
        }
        else
        {
          nextX = x;
        }

        for (int j = 0; j < diameter; j++)
        {


          if (j < radius)
          {
            nextY = y - (radius - j);
          }
          else if (j > radius)
          {
            nextY = y + (j - radius);
          }
          else
          {
            nextY = y;
          }

          if (nextX < 0 || nextX >= mapWidth || nextY < 0 || nextY >= mapHeight)
          {
            continue;
          }

          if (world[nextX][nextY].getLandType() != LandType.WATER) //If this is not a water cell
          {
            switch (type)
            {
              case FOOD:
                world[nextX][nextY].setFoodProximityVal(0);
                break;
              case EXPLORE:
                world[nextX][nextY].setExplorationVal(0);
                break;
              case PATH:
                world[nextX][nextY].setPathVal(0);
                break;
            }
          }
        }
      }
    }
  }

  //Used for testing exploration
  private class AntStep
  {
    int x;
    int y;
    boolean random;

    public AntStep(int x, int y, boolean random)
    {
      this.x = x;
      this.y = y;
      this.random = random;
    }
  }

  //Used for testing exploration
  public void addAntStep(int x, int y, boolean random)
  {
    AntStep newStep = new AntStep(x, y, random);
    antSteps.add(newStep);
    if (antSteps.size() > 6000)
    {
      drawAntPath();
    }
  }

  //Used for testing, can be deleted later.
  private void drawAntPath()
  {
    System.out.println("Drawing Ant Path Map:");
    BufferedImage pathMap = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
    AntStep nextStep;
    Color random = new Color(255, 0, 0, 255);
    Color explore = new Color(0, 0, 255, 255);

    for (int i = 0; i < antSteps.size(); i++)
    {
      nextStep = antSteps.get(i);

      if (nextStep.random)
      {
        pathMap.setRGB(nextStep.x, nextStep.y, random.getRGB());
      }
      else
      {
        pathMap.setRGB(nextStep.x, nextStep.y, explore.getRGB());
      }
    }
    try
    {
      ImageIO.write(pathMap, "PNG", new File("c:\\Users\\John\\Desktop\\antWorldTest\\antPathMap4.PNG"));
    }
    catch (IOException ie)
    {
      ie.printStackTrace();
    }
    System.exit(2);
  }

  //Used for testing, can be deleted later.
  private void drawMap()
  {
    System.out.println("Drawing Map:");
    BufferedImage gradientMap = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
    Color nextColor;
    LandType nextLandType;
    Color grass = new Color(0, 255, 0, 255);
    Color water = new Color(0, 0, 255, 255);
    Color nest = new Color(255, 255, 0, 255);

    for (int i = 0; i < mapWidth; i++)
    {
      for (int j = 0; j < mapHeight; j++)
      {
        nextLandType = world[i][j].getLandType();
        switch (nextLandType)
        {
          case WATER:
            gradientMap.setRGB(i, j, water.getRGB());
            break;
          case NEST:
            gradientMap.setRGB(i, j, nest.getRGB());
            break;
          case GRASS:
            gradientMap.setRGB(i, j, grass.getRGB());
            break;
        }
      }
    }
    try
    {
      ImageIO.write(gradientMap, "PNG", new File("c:\\Users\\John\\Desktop\\antWorldTest\\drawMapTest0.PNG"));
    }
    catch (IOException ie)
    {
      ie.printStackTrace();
    }
  }

  /*
  // Used for testing
  public static void main(String[] args)
  {
    MapManager test = new MapManager("resources/AntTestWorld2.png");
    //test.createDiffusionGradient(150,150,1,500,GradientType.EXPLORE);
    //test.updateCellExploration(150,150);
    //test.drawGradient();
    DiffusionGradient testGrad = new DiffusionGradient(30,1024,30);
    test.writeGradientToMap(150,150,7,testGrad.getGradient(),GradientType.FOOD);
  }
  */
}
