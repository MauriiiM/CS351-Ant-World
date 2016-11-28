package antworld.common;

/**
 * The Enum LandType.
 */
public enum LandType
{

  /** The nest. */
  NEST
      {
        public int getMapColor()
        {
          return 0xF0E68C;
        }
      },

  /**
   * The grass.
   * Note: grass land only uses the green color channel.
   * Thus, the client AI can use the red and blue channels to store
   * other common, such as something that takes the role of a pheromone trail.
   */
  GRASS
      {
        public int getMapColor()
        {
          return 0x283724;
        }
      },


  /** The water. */
  WATER
      {
        public int getMapColor()
        {
          return 0x1E90FF;
        }
      };

  /**
   * Gets the map color.
   *
   * @return the map color
   */
  public abstract int getMapColor();

  /**
   * Gets the map height of grass land.
   * Note: Movement to/from grass the nest area is always a cost of 1, as is
   * movement from nest area to nest area - regardless of encumbrance.
   * Thus, nests can always be though of as being the height of whatever grass they touch.
   *
   * @param rgb the rgb of the land color
   * @return the map height
   */
  public static int getMapHeight(int rgb)
  {
    int g = (rgb & 0x0000FF00) >> 8;
    return Math.max(0, g - 48);
  }

  /**
   * Gets the max map height.
   *
   * @return the max map height
   */
  public static int getMaxMapHeight()
  {
    return getMapHeight(0x0000FF00) - getMapHeight(0);
  }


}