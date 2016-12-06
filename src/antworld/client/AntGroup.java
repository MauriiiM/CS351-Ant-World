package antworld.client;

import antworld.common.AntAction;

import java.util.ArrayList;

/**
 * has precautions to prevent over grouping
 *
 * @todo add central group manager to NestMAnager
 */
public class AntGroup
{
  static final int ANTS_PER_GROUP = 5;//this will need tweeking
  boolean underground = true; //for the most part only add ant into groups when underground
  private AntAction action;
  private ArrayList<Ant> group;

  AntGroup()
  {
    group = new ArrayList<>(ANTS_PER_GROUP);
  }

  boolean isFull()
  {
    return group.size() == ANTS_PER_GROUP;
  }

  boolean isUnderground()
  {
    return underground;
  }

  void addToGroup(Ant ant)
  {
    if (group.size() < ANTS_PER_GROUP) group.add(ant);
  }

  void removeFromGroup(Ant ant)
  {
    group.remove(ant);
  }
}
