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
public class FindClosestWater
{
  private BufferedImage mapImage;
  private int nestNum = 0;
  private Cell[][] geomap;
  private Point[] nestXY = new Point[NestNameEnum.SIZE];
  private Point[] waterXY = new Point[NestNameEnum.SIZE];//corresponding

  FindClosestWater(String imagePath)
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
        pixel = mapImage.getRaster().getPixel(x, y, new int[4]);
        if (pixel[0] == 0 && pixel[1] == 0 && pixel[2] == 0) //black = center of nest
        {
          nestXY[nestNum] = new Point();
          nestXY[nestNum].setLocation(x, y);
          System.out.println("nest at(" + x + ", " + y + ")");
          waterXY[nestNum] = findNearestWater(nestXY[nestNum]);
          nestNum++;
        }
      }
    }
  }

  private Point findNearestWater(Point nest)
  {
    Point waterPoint = new Point();
    int X = (int) nest.getX();
    int Y = (int) nest.getY();
    int x = 0, y = 0;
    int dx = 0, mid, dy = -1;
    int color = 25087660;

    while (geomap[x + X][y + Y].getLandType() != LandType.WATER)
    {
      if ((x == y) || ((x < 0) && (x == -y)) || ((x > 0) && (x == 1 - y)))
      {
        mid = dx;
        dx = -dy;
        dy = mid;
      }
      x += dx;
      y += dy;

      mapImage.setRGB(x + Math.abs(X), y + Math.abs(Y), color);
      color -= 5;
    }
    if (geomap[x + Math.abs(X)][y + Math.abs(Y)].getLandType() == LandType.WATER)
    {
      System.out.println("water at(" + x + Math.abs(X) + ", " + y + Math.abs(Y) + ")");
      mapImage.setRGB(x + Math.abs(X), y + Math.abs(Y), 1775046);
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
    FindClosestWater cp = new FindClosestWater("resources/AntWorld.png");

  }
}
