package antworld.client.navigation;

import antworld.common.LandType;
import antworld.common.NestNameEnum;
import antworld.server.Cell;
import antworld.server.Nest;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.awt.image.ColorConvertOp;
import java.io.*;
import java.net.URL;

/**
 * @todo find nest center, spiral out to find nearest water, when found use A* and write map
 */
public class FindClosestWater
{
  final boolean DEBUG = true;
  private BufferedImage mapImage;
  private int nestNum = 0;
  private Cell[][] geomap;
  private Point[] nestXY = new Point[NestNameEnum.SIZE];
  private Point[] waterXY = new Point[NestNameEnum.SIZE]; //corresponding
  private Point[] nearestNestXY = new Point[NestNameEnum.SIZE]; //corresponding

  Path path;

  FindClosestWater(String imagePath)
  {
    MapManager mapManager = new MapManager(imagePath);
    geomap = mapManager.getGeoMap();
    mapImage = loadMap(imagePath);
    PathFinder pathfinder;
    findNestCenter();
    Color rgb;
    int red = 40;
    int green, blue;
    Color currentPixel;

    for (int i = 0; i < nestXY.length; i++)
    {
//      System.out.printf("%s(%d,%d), nearest water(%d,%d)\n",
//          NestNameEnum.values()[i], nestXY[i].x, nestXY[i].y, waterXY[i].x, waterXY[i].y);
      if (!DEBUG) System.out.printf("static final int[] %s = new int[]{", NestNameEnum.values()[i]);

      pathfinder = new PathFinder(geomap, mapImage.getWidth(), mapImage.getHeight(), nestXY[i].x, nestXY[i].y);
      path = pathfinder.findPath(waterXY[i].x, waterXY[i].y, nestXY[i].x, nestXY[i].y);
      for (int j = 0; j < path.getPath().size(); j++)
      {
        if (!DEBUG)
        {
          if (j != path.getPath().size() - 1)
            System.out.printf("%d, %d, ", path.getPath().get(j).getX(), path.getPath().get(j).getY());
          else System.out.printf("%d, %d", path.getPath().get(j).getX(), path.getPath().get(j).getY());
        }
        else
        {
          currentPixel = new Color(mapImage.getRGB(path.getPath().get(j).getX(), path.getPath().get(j).getY()));
          if (red == 160) red = 40;
          green = currentPixel.getGreen();
          blue = currentPixel.getBlue();
          red += 40;
          rgb = new Color(red, green, blue);
          mapImage.setRGB(path.getPath().get(j).getX(), path.getPath().get(j).getY(), rgb.getRGB());
        }
      }
      if (!DEBUG) System.out.print("};\n");
    }

    try
    {
      ImageIO.write(mapImage, "PNG", new File("/Users/mauricio/Documents/CS351/AntWorld/resources/AntWorldWaterPath.png"));
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
//          System.out.println("nest at(" + x + ", " + y + ")");
//          waterXY[nestNum] = findNearestWater(nestXY[nestNum]);
          nearestNestXY[nestNum] = findNearestNest(nestXY[nestNum]);
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
    }
    if (geomap[x + Math.abs(X)][y + Math.abs(Y)].getLandType() == LandType.WATER)
    {
//      System.out.println("water at(" + x + Math.abs(X) + ", " + y + Math.abs(Y) + ")");
      waterPoint.setLocation(x + X, y + Y);
    }
    return waterPoint;
  }

  private Point findNearestNest(Point nest)
  {
    Point waterPoint = new Point();
    int X = (int) nest.getX();
    int Y = (int) nest.getY();
    int x = 0, y = 0;
    int dx = 0, mid, dy = -1;

    while (geomap[x + X][y + Y].getLandType() != LandType.NEST)
    {
      if ((x == y) || ((x < 0) && (x == -y)) || ((x > 0) && (x == 1 - y)))
      {
        mid = dx;
        dx = -dy;
        dy = mid;
      }
      x += dx;
      y += dy;
    }
    if (geomap[x + Math.abs(X)][y + Math.abs(Y)].getLandType() == LandType.NEST)
    {
//      System.out.println("water at(" + x + Math.abs(X) + ", " + y + Math.abs(Y) + ")");
      waterPoint.setLocation(x + X, y + Y);
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
