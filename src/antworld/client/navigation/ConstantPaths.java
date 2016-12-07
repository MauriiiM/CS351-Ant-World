package antworld.client.navigation;

import antworld.common.LandType;
import antworld.common.NestNameEnum;
import antworld.server.Cell;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.*;
import java.net.URL;

/**
 * @todo find nest center, spiral out to find nearest water, when found use A* and store it
 */
public class ConstantPaths
{
  private BufferedImage mapImage;
  private int nestNum = 0;
  private Cell[][] geomap;
  private Point[] nestXY = new Point[NestNameEnum.SIZE];
  private Point[] waterXY = new Point[NestNameEnum.SIZE];//corresponding

  ConstantPaths(String imagePath)
  {
    MapManager mapManager = new MapManager(imagePath);
    geomap = mapManager.getGeoMap();
    mapImage = loadMap(imagePath);
    findNestCenter();

    try
    {
      ImageIO.write(mapImage, "PNG", new File("/Users/mauricio/Documents/CS351/AntWorld/resources/AntWorldSpiralTest.png"));
    }
    catch (IOException ie)
    {
      ie.printStackTrace();
    }
  }

  private void findNestCenter()
  {
    int[] pixel;

    for (int x = 0; x < mapImage.getWidth(); x++)
    {
      for (int y = 0; y < mapImage.getHeight(); y++)
      {
        pixel = mapImage.getRaster().getPixel(x, y, new int[3]);
        if (pixel[0] == 0 && pixel[1] == 0 && pixel[2] == 0) //black = center of nest
        {
          nestXY[nestNum] = new Point();
          nestXY[nestNum].setLocation(x, y);
          System.out.println("nest at(" + x + ", " + y + ")");
          waterXY[nestNum] = findNearestWater(nestXY[nestNum]);
          nestNum++;
          try
          {
            ImageIO.write(mapImage, "PNG", new File("/Users/mauricio/Documents/CS351/AntWorld/resources/AntWorldSpiralTest.png"));
          }
          catch (IOException ie)
          {
            ie.printStackTrace();
          }
        }
      }
    }
  }

  private Point findNearestWater(Point nest)
  {
    Point waterPoint = new Point();
    int x = (int) nest.getX();
    int y = (int) nest.getY();
    int dx = 0, dy = -1;

    while (geomap[x][y].getLandType() != LandType.WATER)
    {
      if ((x == nest.getX() && y == nest.getY())
          || (x > nest.getX() && x == (nest.getX() + 1) - y)
          || (x < nest.getX() && x == -y))
      {
        dx = -dy;
        dy = dx;
      }
      x += dx;
      y += dy;
      mapImage.setRGB(x, y, LandType.NEST.getMapColor());
    }
    if (geomap[x][y].getLandType() == LandType.WATER)
    {
      System.out.println("water at(" + x + ", " + y + ")");
      waterPoint.setLocation(x, y);
    }

    return waterPoint;
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
      System.err.println("Cannot Open image: " + imagePath);
      e.printStackTrace();
      System.exit(0);
    }
    return map;
  }

  public static void main(String args[])
  {
    ConstantPaths cp = new ConstantPaths("resources/AntWorld.png");

  }
}
