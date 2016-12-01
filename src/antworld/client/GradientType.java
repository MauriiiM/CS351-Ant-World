package antworld.client;

/**
 * Created by John on 11/27/2016.
 */
public enum GradientType
{
  FOOD{
    public int polarity()
    {
      return 1;
    }
  },
  EXPLORE{
    public int polarity()
    {
      return -1;
    }
  };

  public abstract int polarity();
}
