package antworld.client.navigation;

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
  },
  PATH{
    public int polarity()
    {
      return 1;
    }
  };

  public abstract int polarity();
}
