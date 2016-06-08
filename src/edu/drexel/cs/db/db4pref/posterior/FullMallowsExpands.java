package edu.drexel.cs.db.db4pref.posterior;

import edu.drexel.cs.db.db4pref.core.Item;
import java.util.HashMap;

/** Mapping from Expand states to their probabilities */
public class FullMallowsExpands extends HashMap<FullMallowsExpand, Double> {
  
  /** The owner of this object */
  private final FullExpander expander;
  
  public FullMallowsExpands(FullExpander expander) {
    this.expander = expander;
  }
  
  /** Clear this Expands so that it contains only null (empty) expansion */
  public void nullify() {
    this.clear();
    this.put(new FullMallowsExpand(expander), 1d);
  }
  
  public void add(FullMallowsExpand e, Double p) {
    Double prev = this.get(e);
    if (prev != null) p += prev;
    this.put(e, p);
  }
  
  /** Adds item e to the right of the item 'prev' in all the Expands.
   *  If <code>prev</code> is null, it is added at the beginning
   * @return Map of union of the states and their probabilities expanded after adding item e after prev to all expand states
   */  
  public FullMallowsExpands insert(Item e, Item prev) {
    FullMallowsExpands expands = new FullMallowsExpands(expander);
    for (FullMallowsExpand ex: this.keySet()) {
      double p = this.get(ex);
      FullMallowsExpands exs = ex.insert(e, prev);
      expands.add(exs, p);
    }
    //expands.normalize();
    return expands;
  }
  

  
  /** Normalizes sum of p to 1 */
  public void normalize() {
    double sum = 0;
    for (Double p: this.values()) {
      sum += p;
    }
    for (FullMallowsExpand e: this.keySet()) {
      Double v = this.get(e);
      this.put(e, v / sum);
    }    
  }
  
  /** Adds all the Expands to this one with weight p */
  public void add(FullMallowsExpands expands, double p) {
    for (FullMallowsExpand e: expands.keySet()) {
      double v = expands.get(e);
      this.add(e, p * v);
    }
  }
  
  
  /** Expand possible states if the specified item is missing (can be inserted between any two present items)
   * @param item To insert
   * @return Mapping of states to their probabilities
   */
  public FullMallowsExpands insertMissing(Item item) {
    FullMallowsExpands expands = new FullMallowsExpands(expander);    
    for (FullMallowsExpand ex: this.keySet()) {
      FullMallowsExpands exs = ex.insertMissing(item);
      expands.add(exs, this.get(ex));
    }
    //expands.normalize();
    return expands;
  }


  
  @Override
  public String toString() {
    StringBuilder sb = new StringBuilder();
    for (FullMallowsExpand expand: this.keySet()) {
      sb.append(expand).append(": ").append(this.get(expand)).append("\n");
    }
    return sb.toString();
  }
  
  
  /** Get the sum of weights where item e is at the position pos (zero based) */
  public double count(Item e, int pos) {
    double sum = 0;
    for (int i = 0; i < 10; i++) {
      for (FullMallowsExpand ex: this.keySet()) {
        if (ex.isAt(e, pos)) sum += this.get(ex);
      }
    }
    return sum;
  }
  
  /** Distribution of item e being at different positions */
  public double[] getDistribution(Item e) {
    double[] dist = null;
    double sum = 0;
    for (FullMallowsExpand ex: this.keySet()) {
      double p = this.get(ex);
      if (dist == null) dist = new double[ex.length()];
      int pos = ex.position(e);
      dist[pos] += p;
      sum += p;
    }
    
    if (dist != null && sum > 0) {
      for (int i = 0; i < dist.length; i++) {
        dist[i] = dist[i] / sum;
      }
    }
    else {
      dist = new double[0];
    }
    return dist;
  }

  /** Returns the total probability of all the expanded states */
  public double getProbability() {
    double sum = 0;
    for (double p: this.values()) sum += p;
    return sum;
  }
  
}
