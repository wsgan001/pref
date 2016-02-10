package edu.drexel.cs.db.rank.preference;

import edu.drexel.cs.db.rank.core.ItemSet;
import edu.drexel.cs.db.rank.preference.PreferenceSample.PW;
import java.util.ArrayList;


public class PreferenceSample extends ArrayList<PW> {

    private final ItemSet itemSet;

    public PreferenceSample(ItemSet items) {
      this.itemSet = items;
    }

    public ItemSet getItems() {
      return itemSet;
    }

    public double sumWeights() {
      double s = 0;
      for (PW pw: this) s += pw.w;
      return s;
    }

    public void add(PreferenceSet pref, double weight) {
      this.add(new PW(pref, weight));
    }  

    public PreferenceSet getPreferenceSet(int index) {
      return this.get(index).p;
    }

    public double getWeight(int index) {
      return this.get(index).w;
    }
    
    public double getWeight(PreferenceSet pref) {
      double w = 0;
      for (PW pw: this) w += pw.w;
      return w;
    }

    public void add(PreferenceSet pref) {
      this.add(pref, 1);
    }
    
    public static class PW {
    
      public final PreferenceSet p;
      public final double w;

      private PW(PreferenceSet p, double w) {
        this.p = p;
        this.w = w;
      }

      public String toString() {
        if (w == 1) return p.toString();
        else return p.toString() + " (" + w + ")";
      }
    }
}
