package antworld.server;

import antworld.common.AntData;
import antworld.common.FoodData;
import antworld.common.LandType;
import antworld.common.NestNameEnum;
import antworld.server.GameObject.GameObjectType;

public class Cell 
{
  private final int height;
  private final int x, y;
  private LandType landType;
  private Nest nest = null;
  private GameObject gameObject = null;
  
  
  public Cell(LandType landType, int height, int x, int y)
  {
    this.landType = landType;
    this.height = height;
    this.x = x;
    this.y = y;
    nest = null;
  }
  
  public LandType getLandType() {return landType;}
  public int getHeight() {return height;}
  public int getLocationX() {return x;}
  public int getLocationY() {return y;}

  public boolean isEmpty() {if (gameObject == null) return true; else return false;}
  
  public GameObject getGameObject() { return gameObject;}
  
  public AntData getAnt() 
  { 
    if (gameObject == null) return null;
    return gameObject.ant;
  }
  
  public FoodData getFood()  
  { 
    if (gameObject == null) return null;
    return gameObject.food;
  }
  
  
  public Nest getNest() {  return nest;}
  public NestNameEnum getNestName()
  { if (nest == null) return null;
    return nest.nestName;
  }
  
  public void setAnt(AntData ant) 
  { 
    if (ant == null) gameObject = null;
    else gameObject = new GameObject(ant);
  }
  
  
  public void setFood(FoodSpawnSite foodSpawnSite, FoodData food) 
  { 
    if (food == null) gameObject = null;
    else gameObject = new GameObject(foodSpawnSite, food);
  }
  
  
  public void setNest(Nest nest) 
  { this.nest = nest;
    this.landType = LandType.NEST;
  }
  
  public void setLandType(LandType landType)
  { this.landType = landType;
  }
  
  public int getRGB()
  { 
    if (gameObject != null)
    {
      if(gameObject.type == GameObjectType.ANT) return 0xD7240D;
    
      else
      {
        return gameObject.food.foodType.getColor();
      }
    }
    
    if (landType == LandType.WATER) return landType.getMapColor();
    if (landType == LandType.NEST)
    { 
      if ((x == nest.getCenterX()) && (y == nest.getCenterY())) return 0x0;
      return landType.getMapColor();
    }
    if (landType == LandType.GRASS)
    { 
      int r1 = 40, r2=186;
      int g = Math.min(255, 55 + height);
      int b1 = 36, b2=166;
      
      
      int diffR = r2-r1;
      int diffB = b2-b1;
      
      int r = r1 + (diffR*height)/200;
      int b = b1 + (diffB*height)/200;
      
      //System.out.println("r="+r+", g="+g+", b="+b);

      
      return (r<<16) | (g<<8) | b;
    }
    else return 0;
  }
  
  public String toString()
  {
    return "Cell: " + landType + "("+x+", " + y + "), height=" +height;
  }
}

