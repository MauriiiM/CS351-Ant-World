package antworld.client.astar;

import antworld.client.MapCell;
import antworld.common.LandType;
import antworld.server.Cell;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.PackedColorModel;
import java.io.File;
import java.io.IOException;
import java.net.URL;
import java.util.ArrayList;

/**
 * Reads a map and creates an array of Cells to represent the world
 * The server creates a Cell array, this will create a copy for the client
 * Used for terrain/elevation checking when path finding.
 * Created by John on 11/23/2016.
 */
public class MapReader
{

  private String imagePath = null;  //"resources/AntTestWorld4.png"
  private BufferedImage map = null;
  private MapCell[][] world;
  private int mapWidth;
  private int mapHeight;
  private static final int DIFFUSIONCOEFFICIENT = 2;  //Lowering the value will reduce diffusion rate

  public MapReader(String mapPath)
  {
    this.imagePath = mapPath;
    map = loadMap(imagePath);
    world = readMap(map);
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

  private MapCell[][] readMap(BufferedImage map)
  {
    mapWidth = map.getWidth();
    mapHeight = map.getHeight();
    MapCell[][] world = new MapCell[mapWidth][mapHeight];
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
      }
    }
    return world;
  }

  public MapCell[][] getWorld()
  {
    return world;
  }

  public int getMapWidth()
  {
    return mapWidth;
  }

  public int getMapHeight()
  {
    return mapHeight;
  }

  public void updateCellFoodProximity(int foodX, int foodY)
  {
    createDiffusionGradient(foodX,foodY,35,9000);
  }

  /**
   * Writes the diffusion gradient to the map by updating cells with a new food proximity value
   * @param x - target x coord of center of gradient
   * @param y - target y coord of center of gradient
   * @param diameter - the diameter of the diffusion gradient
   * @param diffusionValues - the array of diffusion values
   */
  private void writeGradientToMap(int x, int y, int diameter, int[][] diffusionValues)
  {
    int radius = diameter/2;
    int nextX;
    int nextY;
    int nextVal;

    for(int i=0;i<diameter;i++)
    {

      if(i<radius)
      {
        nextX = x-(radius-i);
      }
      else if(i>radius)
      {
        nextX = x+(i-radius);
      }
      else
      {
        nextX = x;
      }

      for (int j = 0; j < diameter; j++)
      {


        if(j<radius)
        {
          nextY = y-(radius-j);
        }
        else if(j>radius)
        {
          nextY = y+(j-radius);
        }
        else
        {
          nextY = y;
        }

        nextVal = diffusionValues[i][j];
        world[nextX][nextY].setFoodProximityVal(nextVal);
        //System.out.println("i="+i+" || j="+j + " || diameter=" + diameter);
        //System.out.println("Setting world["+nextX+"]["+nextY+"] = " + nextVal);
      }
    }
  }

  /**
   * Creates a diffusion gradient of radius r, from an x,y coordinate, with goal value goal
   * I think radius must be even
   * NOTE: Might be worth experimenting on creating a cone towards another coord, instead creating an entire circle
   *  It could be good to allow gradients to blend together.
   *  Might be a good idea to integrate the values into the map cells tighter
   *  Could be adapted to make ants explore efficiently
   *  Small radius' can be used for fighting/tracking enemy ants
   */
  private void createDiffusionGradient(int x, int y, int radius, int goalValue)
  {
    int diameter = (radius*2) + 1;  //One is added to the diameter so that there is a definitive center to the gradient.
    int[][] diffusionValues = new int[diameter][diameter];  //This will store the diffusion gradient
    diffusionValues[radius][radius] = goalValue;  //The gradient center's diffusion value equals the goal
    diffusionValues = expandDiffusionGradient(radius,radius,diameter,diffusionValues);
    //printGradient(diameter,diffusionValues);
    writeGradientToMap(x,y,diameter,diffusionValues);
  }

  /**
   *Grows the diffusion gradient until it reaches the proper diameter.
   * @param x - the gradient center x coordinate
   * @param y - the gradient center y coordinate
   * @param diameter - the desired diameter of the diffusion gradient (Always odd, always greater than 1)
   * @param diffusionValues - the array of diffusion values stored from the initial/last iteration (only read from)
   */
  private int[][] expandDiffusionGradient(int x, int y, int diameter, int[][] diffusionValues)
  {
    int[][] updatedDiffValues;  //This will store the next iteration as the gradient expands

    for(int i=3;i<diameter+1;i+=2)
    {
      updatedDiffValues = new int[diameter][diameter];
      diffusionValues = updateGradientRing(x,y,i,diffusionValues,updatedDiffValues, true);  //Expand the gradient diameter by one cell

      if(i>3) //If the ring has expanded beyond the first ring surrounding the center, the inner ring must be updated too.
      {
        int[][] lastDiffValues = diffusionValues.clone(); //Create a template to read later (shallow)
        updatedDiffValues = new int[diameter][diameter];
        diffusionValues = updateGradientRing(x,y,i-2,diffusionValues,updatedDiffValues, false);  //Update the old outer diameter ring
        diffusionValues = mergeGradientInnerRing(i,diffusionValues,lastDiffValues);
      }
    }

    return diffusionValues;
  }

  /**
   * Combines the updated diffusion value of the second to last outer ring with the rest of the gradient
   * @param diameter - the diameter of the gradient
   * @param diffusionValues - the collection of diffusion values that make up the gradient
   * @param lastDiffVals - the updated average diffusion values of the second to last outer ring
   * @return - the merged diffusion values.
   */
  private int[][] mergeGradientInnerRing(int diameter, int[][] diffusionValues, int[][] lastDiffVals)
  {
    for(int i=0;i<diameter;i++)
    {
      for(int j=0;j<diameter;j++)
      {
        if(diffusionValues[i][j] != 0)
        {
          lastDiffVals[i][j] = diffusionValues[i][j];
        }
      }
    }

    return lastDiffVals;
  }

  /**
   *Updates a ring of the diffusion gradient by updating the cells a certain distance away from the center.
   * @param x - the gradient center x coordinate
   * @param y - the gradient center y coordinate
   * @param diameter - the diameter of the next iteration of the gradient (Always odd, always greater than 1)
   * @param diffusionValues - the array of diffusion values from the last iteration (only read from)
   * @param updatedDiffValues - the array of the next iteration of diffusion values (written to)
   */

  private int[][] updateGradientRing(int x, int y, int diameter, int[][] diffusionValues, int[][] updatedDiffValues, boolean outerRing)
  {
    int centralDistance = (diameter/2); //Distance from the center of the gradient

    for(int i=0;i<centralDistance+1;i++)  //x values
    {
      for(int j=0;j<centralDistance+1;j++)  //y values
      {

        if(i<centralDistance && j<centralDistance )  //Inner region of gradient, copy it to the updated diffusion values array
        {

          if(i==0 && j==0)
          {
            updatedDiffValues[x][y] = diffusionValues[x][y];
          }
          else
          {
            if(i<j)
            {
              updatedDiffValues[x+i][y+j] = diffusionValues[x+i][y+j];
              updatedDiffValues[x+i][y-j] = diffusionValues[x+i][y-j];

              if(i>0)
              {
                updatedDiffValues[x-i][y+j] = diffusionValues[x+i][y+j];
                updatedDiffValues[x-i][y-j] = diffusionValues[x+i][y-j];
              }
            }
            else if(i>j)
            {
              updatedDiffValues[x+i][y+j] = diffusionValues[x+i][y+j];
              updatedDiffValues[x-i][y+j] = diffusionValues[x-i][y+j];

              if(j>0)
              {
                updatedDiffValues[x+i][y-j] = diffusionValues[x+i][y-j];
                updatedDiffValues[x-i][y-j] = diffusionValues[x-i][y-j];
              }
            }
            else  //i = j
            {
              updatedDiffValues[x+i][y+j] = diffusionValues[x+i][y+j];
              updatedDiffValues[x-i][y-j] = diffusionValues[x-i][y-j];
              updatedDiffValues[x+i][y-j] = diffusionValues[x+i][y-j];
              updatedDiffValues[x-i][y+j] = diffusionValues[x-i][y+j];
            }
          }
        }
        else
        {
          if(i<j)
          {
            updateCellDiffusionValue(x+i,y+j, diffusionValues, updatedDiffValues, outerRing);
            updateCellDiffusionValue(x+i,y-j, diffusionValues, updatedDiffValues, outerRing);
            if(i>0)
            {
              updateCellDiffusionValue(x-i,y+j, diffusionValues, updatedDiffValues, outerRing);
              updateCellDiffusionValue(x-i,y-j, diffusionValues, updatedDiffValues, outerRing);
            }
          }
          else if(i>j)
          {
            updateCellDiffusionValue(x+i,y+j, diffusionValues, updatedDiffValues, outerRing);
            updateCellDiffusionValue(x-i,y+j, diffusionValues, updatedDiffValues, outerRing);
            if(j>0)
            {
              updateCellDiffusionValue(x+i,y-j, diffusionValues, updatedDiffValues, outerRing);
              updateCellDiffusionValue(x-i,y-j, diffusionValues, updatedDiffValues, outerRing);
            }
          }
          else  //i = j
          {
            updateCellDiffusionValue(x+i,y+j, diffusionValues, updatedDiffValues, outerRing);
            updateCellDiffusionValue(x-i,y-j, diffusionValues, updatedDiffValues, outerRing);
            updateCellDiffusionValue(x+i,y-j, diffusionValues, updatedDiffValues, outerRing);
            updateCellDiffusionValue(x-i,y+j, diffusionValues, updatedDiffValues, outerRing);
          }
        }
      }
    }

    return updatedDiffValues; //Updated difference value array complete, return to store it as the new template for the next update.
  }

  /**
   * Updates a single cell's diffusion value by taking the average of the diffusion values in the cell's Moore's neighborhood
   * @param x - the cell's x coordinate
   * @param y - the cell's y coordinate
   * @param diffusionValues - the array of diffusion values from the last iteration (only read from)
   * @param updatedDiffValues - the array of the next iteration of diffusion values (written to)
   * @param outerRing - Boolean value indicating if this is the outer most ring of the gradient
   */
  private void updateCellDiffusionValue(int x, int y, int[][] diffusionValues, int[][] updatedDiffValues, boolean outerRing)
  {
    int neighborValSum = 0;
    int cellDiffusionVal;
    int arraySize = diffusionValues[0].length;

    if(outerRing) //Checks which cells will have neighbors to avoid IndexOutOfBounds errors
    {
      boolean neighborsNorth = false;
      boolean neighborsSouth = false;
      boolean neighborsEast = false;
      boolean neighborsWest = false;

      if(y<arraySize-2)
        neighborsNorth = true;

      if(y>0)
        neighborsSouth = true;

      if(x<arraySize-2)
        neighborsEast = true;

      if(x>0)
        neighborsWest = true;

      if(neighborsNorth)
      {
        neighborValSum += diffusionValues[x][y+1];  //N neighbor

        if(neighborsEast)
          neighborValSum += diffusionValues[x+1][y+1];  //NE neighbor

        if(neighborsWest)
          neighborValSum += diffusionValues[x-1][y+1];  //NW neighbor
      }

      if(neighborsSouth)
      {
        neighborValSum += diffusionValues[x][y-1];  //S neighbor

        if(neighborsEast)
          neighborValSum += diffusionValues[x+1][y-1];  //SE neighbor

        if(neighborsWest)
          neighborValSum += diffusionValues[x-1][y-1];  //SW neighbor
      }

      if(neighborsEast)
        neighborValSum += diffusionValues[x+1][y];  //E neighbor

      if(neighborsWest)
        neighborValSum += diffusionValues[x-1][y];  //W neighbor
    }
    else
    {
      neighborValSum += diffusionValues[x][y+1];  //N neighbor
      neighborValSum += diffusionValues[x+1][y+1];  //NE neighbor
      neighborValSum += diffusionValues[x-1][y+1];  //NW neighbor
      neighborValSum += diffusionValues[x][y-1];  //S neighbor
      neighborValSum += diffusionValues[x+1][y-1];  //SE neighbor
      neighborValSum += diffusionValues[x-1][y-1];  //SW neighbor
      neighborValSum += diffusionValues[x+1][y];  //E neighbor
      neighborValSum += diffusionValues[x-1][y];  //W neighbor
    }

    cellDiffusionVal = neighborValSum/DIFFUSIONCOEFFICIENT;
    updatedDiffValues[x][y] = cellDiffusionVal;
  }

  /*
  public static void main(String[] args)
  {
    MapReader test = new MapReader("resources/AntTestWorld2.png");
    test.createDiffusionGradient(150,150,30,9000);
    test.drawGradient();
  }


  //Used for testing, can be deleted later.
  private void drawGradient()
  {
    System.out.println("Drawing Gradient:");
    BufferedImage gradientMap = new BufferedImage(mapWidth, mapHeight, BufferedImage.TYPE_INT_ARGB);
    Color nextColor;
    int nextVal;
    int colorVal;

    for (int i = 0; i < mapWidth; i++)
    {
      for(int j=0;j<mapHeight;j++)
      {
        nextVal = world[i][j].getFoodProximityVal();
        if(nextVal>0)
        {
          colorVal = 255/nextVal;
          nextColor = new Color(255,colorVal,colorVal,255);
          gradientMap.setRGB(i,j,nextColor.getRGB());
          System.out.println("Colorval = " + colorVal);
        }
        else
        {
          //colorVal = 255;
        }

        //nextColor = new Color(255,colorVal,colorVal,255);
        //gradientMap.setRGB(i,j,nextColor.getRGB());
      }
    }
    try
    {
      ImageIO.write(gradientMap,"PNG",new File("c:\\Users\\John\\Desktop\\antWorldTest\\gradientCoeff1.PNG"));
    } catch (IOException ie){
      ie.printStackTrace();
    }
  }

  //Used to print the diffusion values of the gradient
  private void printGradient(int diameter, int[][] diffusionValues)
  {
    System.out.println("PRINTING DIFFUSION VALUES");
    for(int i=0;i<diameter;i++)
    {
      for(int j=0;j<diameter;j++)
      {
        System.out.println("diffusionValues[" + i + "][" + j + "] = " + diffusionValues[i][j]);
      }
    }
  }
  */
}
