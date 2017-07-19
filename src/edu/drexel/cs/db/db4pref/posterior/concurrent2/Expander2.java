package edu.drexel.cs.db.db4pref.posterior.concurrent2;

import edu.drexel.cs.db.db4pref.core.Item;
import edu.drexel.cs.db.db4pref.core.ItemSet;
import edu.drexel.cs.db.db4pref.core.MapPreferenceSet;
import edu.drexel.cs.db.db4pref.core.MutablePreferenceSet;
import edu.drexel.cs.db.db4pref.core.Preference;
import edu.drexel.cs.db.db4pref.core.PreferenceSet;
import edu.drexel.cs.db.db4pref.core.Ranking;
import edu.drexel.cs.db.db4pref.data.PreferenceIO;
import edu.drexel.cs.db.db4pref.gm.HasseDiagram;
import edu.drexel.cs.db.db4pref.model.MallowsModel;
import edu.drexel.cs.db.db4pref.posterior.Span;
import edu.drexel.cs.db.db4pref.posterior.expander.ExpanderListener;
import edu.drexel.cs.db.db4pref.posterior.expander.LowerBoundLast;
import edu.drexel.cs.db.db4pref.posterior.expander.ParallelExpanderListener;
import edu.drexel.cs.db.db4pref.util.Logger;
import edu.drexel.cs.db.db4pref.util.TestUtils;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeoutException;

/**
 * Best parallel so far
 */
public class Expander2 implements Cloneable{

  private final MallowsModel model;
  final Map<Item, Integer> referenceIndex;
  final int threads;

  private PreferenceSet pref;
  MutablePreferenceSet tc;
  private Expands2 expands;
  Map<Item, Span> spans;

  protected ParallelExpanderListener listener;
  public long startExpand;
  private long timeout = 0; // milliseconds

  /**
   * Creates a parallelized Expander for the given Mallows model with specified
   * number of threads
   */
  public Expander2(MallowsModel model, PreferenceSet pref, int threads) {
    this.model = model;
    this.referenceIndex = model.getCenter().getIndexMap();
    this.threads = threads;
    this.pref = pref.clone();
  }

  public double getProbability(PreferenceSet pref) throws TimeoutException, InterruptedException {
    return expand();
  }

  private void calculateSpans() {
    this.spans = new HashMap<Item, Span>();
    Ranking reference = model.getCenter();

    for (Item item : pref.getItems()) {
      int from = referenceIndex.get(item);
      Span span = new Span(from, from);
      spans.put(item, span);
    }

    HasseDiagram hasse = new HasseDiagram(pref);

    for (int step = 0; step < reference.length(); step++) {
      Item item = reference.get(step);
      if (pref.contains(item)) {
        hasse.add(item);
        for (Preference p : hasse.getPreferenceSet()) {
          int il = referenceIndex.get(p.lower);
          int ih = referenceIndex.get(p.higher);
          if (il < ih && ih == step) {
            spans.get(p.lower).setTo(step);
          } else if (il > ih && il == step) {
            spans.get(p.higher).setTo(step);
          }
        }
      }
    }
  }

  public Span getSpan(Item item) {
    return spans.get(item);
  }

  /**
   * Index of the item in the reference (center) ranking
   */
  public int getReferenceIndex(Item item) {
    return referenceIndex.get(item);
  }

  /**
   * Returns the index of highest sigma_i in the preference set
   */
  public int getMaxItem(PreferenceSet pref) {
    int i = 0;
    for (Item item : pref.getItems()) {
      i = Math.max(i, referenceIndex.get(item));
    }
    return i;
  }

  public double expand() throws TimeoutException, InterruptedException {
    return expand(false);
  }

  public double expand(boolean isLowerBound) throws TimeoutException, InterruptedException {
    if (expands == null) {
      expands = new Expands2(this);
      expands.nullify();
    }
    return mallowsDP(isLowerBound);
  }

  public double mallowsDP(boolean isLowerBound) throws TimeoutException, InterruptedException {

    this.tc = pref.transitiveClosure();
    calculateSpans();

    Ranking reference = model.getCenter();
    int maxIndex = getMaxItem(pref);

    startExpand = System.currentTimeMillis();
    if (listener != null) listener.onStart(this);

    int initialLength = expands.getStates().keySet().iterator().next().length();
    if (initialLength <= maxIndex) {
      Workers2 workers = new Workers2(threads);
      for (int i = initialLength; i <= maxIndex; i++) {
        if (listener != null) {
          listener.onStepBegin(this, i+1);
        }
        relax(i);

        Item item = reference.get(i);
        boolean missing = !this.pref.contains(item);
        expands = expands.insert(item, missing, isLowerBound, workers);

        if (listener != null) {
          listener.onStepEnd(this, i+1);
        }
      }
      workers.stop();
    }

    double p = expands.getProbability();
    if (listener != null) {
      listener.onEnd(this, p);
    }
    return p;
  }

  private HashMap<Span, Double> probs = new HashMap<>();

  /**
   * Calculate the probability of the item being inserted at the given position.
   * Directly from the Mallows model
   */
  double probability(int itemIndex, int position) {
    Span span = new Span(itemIndex, position);
    Double p = probs.get(span);
    if (p != null) {
      return p;
    }

    double phi = model.getPhi();
    double p1 = Math.pow(phi, Math.abs(itemIndex - position)) * (1 - phi) / (1 - Math.pow(phi, itemIndex + 1));
    probs.put(span, p1);
    return p1;
  }

  /**
   * Starting from here, add approximation.
   */
  public void setListener(ParallelExpanderListener listener) {
    int maxIndexOfItem = getMaxItem(pref);
    if (listener.getStep() > maxIndexOfItem) {
      listener.setStep(maxIndexOfItem+1);
    }
    this.listener = listener;
  }

  public void setMaxWidth(int maxWidth) {
    this.maxWidth = maxWidth;
  }

  public void setTimeout(long timeout) {
    this.timeout = timeout;
  }

  public long getTimeout() {
    return timeout;
  }

  /**
   * Sets the maximum allowed width, and the step from which item removal can
   * start
   */
  public void setMaxWidth(int maxWidth, int start) {
    this.maxWidth = maxWidth;
    this.startRelax = start;
  }

  private int maxWidth = 0;
  private int startRelax = 0;

  private void relax(int step) {
    if (maxWidth <= 0) {
      return;
    }
    if (step < startRelax) {
      return;
    }

    List<Item> tracked = this.getTrackedItems(step);
    while (tracked.size() > maxWidth) {
      Item toRemove = tracked.get(0);
      pref.remove(toRemove);
      this.calculateSpans();
      tracked = this.getTrackedItems(step);
    }
  }

  public List<Item> getTrackedItems(int step) {
    List<Item> tracked = new ArrayList<Item>();
    for (Map.Entry<Item, Span> entry : spans.entrySet()) {
      Span span = entry.getValue();
      if (span.from <= step && step <= span.to && span.from != span.to) {
        tracked.add(entry.getKey());
      }
    }
    return tracked;
  }

  public Expands2 getExpands() {
    return expands;
  }

  public void setExpands(Expands2 expands) {
    this.expands = expands;
  }

  public MallowsModel getModel() {
    return model;
  }

  public PreferenceSet getPreferenceSet() {
    return pref;
  }

  public Expander2 clone() {
    Expander2 expander2 = new Expander2(model, pref, threads);
    expander2.setExpands(expands.clone());
    return expander2;
  }

  public static void main(String[] args) throws TimeoutException, InterruptedException {
    MapPreferenceSet pref = TestUtils.generate(20, 4, 5);
//    MapPreferenceSet pref = PreferenceIO.fromString("[19>12 12>8 4>8 9>16 19>11]", new ItemSet(20));
//    MapPreferenceSet pref = PreferenceIO.fromString("[17>10 12>6 2>12 14>10 6>5]", new ItemSet(20));

    Logger.info(pref);
    ItemSet items = pref.getItemSet();

    double phi = 0.8;
    MallowsModel model = new MallowsModel(items.getReferenceRanking(), phi);

    {
      Expander2 expander2 = new Expander2(model, pref, 2);
      System.out.printf("Exact probability = %f \n", expander2.expand());
    }

    for (int width = 1; width <= 3; width++) {
      Expander2 expander2 = new Expander2(model, pref, 2);
      expander2.setMaxWidth(width, 10);
      ParallelLowerBoundListener listener = new ParallelLowerBoundListener(10);
      expander2.setListener(listener);
      System.out.printf("upper = %f, lower = %f, under width=%d \n", expander2.expand(), listener.lb, width);
    }

    for (int step = 10; step <= 20; step += 1) {
      Expander2 expander2 = new Expander2(model, pref, 2);
      expander2.setMaxWidth(1, step);
      ParallelLowerBoundListener listener = new ParallelLowerBoundListener(step);
      expander2.setListener(listener);
      System.out.printf("upper = %f, lower = %f, under step=%d \n", expander2.expand(), listener.lb, step);
    }
  }

  ;
  
    private static class ParallelLowerBoundListener implements ParallelExpanderListener {

    int step;
    long timeBeforeLB, timeAfterLB;
    double lb;

    private ParallelLowerBoundListener(int step) {
      this.step = step;
    }

    @Override
    public int getStep() {
      return step;
    }

    @Override
    public void setStep(int step) {
      this.step = step;
    }

    @Override
    public void onStart(Expander2 expander) {
    }

    @Override
    public void onStepBegin(Expander2 expander, int step) throws TimeoutException {
      if (this.step == step) {
        this.timeBeforeLB = System.currentTimeMillis();
        Expander2 expander2 = expander.clone();
        try {
          lb = expander2.expand(true);
        } catch (InterruptedException e) {
          e.printStackTrace();
        }
        this.timeAfterLB = System.currentTimeMillis();
      }
    }

    @Override
    public void onStepEnd(Expander2 expander, int step) {
    }

    @Override
    public void onEnd(Expander2 expander, double p) {
    }
  }
}
