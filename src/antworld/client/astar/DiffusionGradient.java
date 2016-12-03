package antworld.client.astar;

/**
 * A diffusion gradient that can written to the client map
 * Created by John on 11/29/2016.
 */
public class DiffusionGradient
{
  private int radius;
  private int diameter;
  private int goalValue;
  private int diffusionCoefficient;
  private int[][] gradient;

  /**
   * Creates a diffusion gradient of radius r, from an x,y coordinate, with goal value goal
   * @param radius - the radius of the diffusion gradient
   * @param goalValue - the value of the center cell in the gradient
   * @param diffusionCoefficient - the coefficient to diffuse the goal by as it creates the gradient.
   */
  public DiffusionGradient(int radius, int goalValue, int diffusionCoefficient)
  {
    this.radius = radius;
    this.diameter = (radius*2) +1;
    this.goalValue = goalValue;
    this.diffusionCoefficient = diffusionCoefficient;
    gradient = new int[diameter][diameter];
    createDiffusionGradient();
  }

  /**
   * Returns the diffusion gradient to write to the MapCell[][] world.
   * @return - the diffusion gradient containing values to increment/decrement/replace the value of MapCells.
   */
  public int[][] getGradient()
  {
    return this.gradient;
  }

  /**
   * Creates a diffusion gradient of radius r, from an x,y coordinate, with goal value goal
   * I think radius must be even
   * NOTE: Might be worth experimenting on creating a cone towards another coord, instead creating an entire circle
   *  It could be good to allow gradients to blend together.
   *  Might be a good idea to integrate the values into the map cells tighter
   *  Could be adapted to make ants explore efficiently
   *  Small radius' can be used for fighting/tracking enemy ants
   *  Gradient polarity is whether the gradient is positive or negative. (-1 or 1)
   *  Food is positive because it will increase the value of cells it affects
   *  Exploration is negative because it will decrease the value of the cells it affects
   */
  private void createDiffusionGradient()
  {
    gradient[radius][radius] = goalValue;  //The gradient center's diffusion value equals the goal
    gradient = expandDiffusionGradient(radius,radius);
    printGradient();    //Used for testing
  }

  /**
   *Grows the diffusion gradient until it reaches the proper diameter.
   * @param x - the central x-index of the 2D array the gradient is stored in.
   * @param y - the central y-index of the 2D array the gradient is stored in.
   */
  private int[][] expandDiffusionGradient(int x, int y)
  {
    int[][] updatedDiffValues;  //This will store the next iteration as the gradient expands

    for(int i=3;i<diameter+1;i+=2)
    {
      updatedDiffValues = new int[diameter][diameter];
      gradient = updateGradientRing(x,y,i,updatedDiffValues);  //Expand the gradient diameter by one cell
    }

    return gradient;
  }

  /**
   *Updates a ring of the diffusion gradient by updating the cells a certain distance away from the center.
   * @param x - the central x-index of the 2D array the gradient is stored in.
   * @param y - the central y-index of the 2D array the gradient is stored in.
   * @param diameter - the diameter of the next iteration of the gradient (Always odd, always greater than 1)
   * @param updatedDiffValues - the array of the next iteration of diffusion values (written to)
   */

  private int[][] updateGradientRing(int x, int y, int diameter, int[][] updatedDiffValues)
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
            updatedDiffValues[x][y] = gradient[x][y];
          }
          else
          {
            if(i<j)
            {
              updatedDiffValues[x+i][y+j] = gradient[x+i][y+j];
              updatedDiffValues[x+i][y-j] = gradient[x+i][y-j];

              if(i>0)
              {
                updatedDiffValues[x-i][y+j] = gradient[x+i][y+j];
                updatedDiffValues[x-i][y-j] = gradient[x+i][y-j];
              }
            }
            else if(i>j)
            {
              updatedDiffValues[x+i][y+j] = gradient[x+i][y+j];
              updatedDiffValues[x-i][y+j] = gradient[x-i][y+j];

              if(j>0)
              {
                updatedDiffValues[x+i][y-j] = gradient[x+i][y-j];
                updatedDiffValues[x-i][y-j] = gradient[x-i][y-j];
              }
            }
            else  //i = j
            {
              updatedDiffValues[x+i][y+j] = gradient[x+i][y+j];
              updatedDiffValues[x-i][y-j] = gradient[x-i][y-j];
              updatedDiffValues[x+i][y-j] = gradient[x+i][y-j];
              updatedDiffValues[x-i][y+j] = gradient[x-i][y+j];
            }
          }
        }
        else
        {
          if(i<j)
          {
            updateCellDiffusionValue(x+i,y+j, diameter, updatedDiffValues);
            updateCellDiffusionValue(x+i,y-j, diameter, updatedDiffValues);
            if(i>0)
            {
              updateCellDiffusionValue(x-i,y+j, diameter, updatedDiffValues);
              updateCellDiffusionValue(x-i,y-j, diameter, updatedDiffValues);
            }
          }
          else if(i>j)
          {
            updateCellDiffusionValue(x+i,y+j, diameter, updatedDiffValues);
            updateCellDiffusionValue(x-i,y+j, diameter, updatedDiffValues);
            if(j>0)
            {
              updateCellDiffusionValue(x+i,y-j, diameter, updatedDiffValues);
              updateCellDiffusionValue(x-i,y-j, diameter, updatedDiffValues);
            }
          }
          else  //i = j
          {
            updateCellDiffusionValue(x+i,y+j, diameter, updatedDiffValues);
            updateCellDiffusionValue(x-i,y-j, diameter, updatedDiffValues);
            updateCellDiffusionValue(x+i,y-j, diameter, updatedDiffValues);
            updateCellDiffusionValue(x-i,y+j, diameter, updatedDiffValues);
          }
        }
      }
    }

    return updatedDiffValues; //Updated difference value array complete, return to store it as the new template for the next update.
  }

  /**
   * Updates a single cell's diffusion value as a function of how far away the cell is from the center of the gradient.
   * @param x - the cell's x coordinate.
   * @param y - the cell's y coordinate.
   * @param diameter - the diameter of the diffusion gradient.
   * @param updatedDiffValues - the array of the next iteration of diffusion values (written to).
   */
  private void updateCellDiffusionValue(int x, int y, int diameter, int[][] updatedDiffValues)
  {
    double roughDiffVal;
    int cellDiffusionVal;
    int radius = diameter/2;

    //NOTE: Might need to reimplement so that it takes the sum of its neighbors instead of the distance from the center. Otherwise ants might not collaborate.

    roughDiffVal = goalValue-(diffusionCoefficient*radius);
    cellDiffusionVal = (int) roughDiffVal;
    updatedDiffValues[x][y] = cellDiffusionVal;
  }

  //Used to print the diffusion values of the gradient
  private void printGradient()
  {
    System.out.println("PRINTING DIFFUSION VALUES");
    for(int i=0;i<diameter;i++)
    {
      for(int j=0;j<diameter;j++)
      {
        System.out.print(gradient[i][j] + "\t");
        if(gradient[i][j]<1000)
        {
          System.out.print("\t");
        }
      }
      System.out.println("");
    }
  }
}
