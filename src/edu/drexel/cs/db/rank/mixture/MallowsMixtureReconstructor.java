package edu.drexel.cs.db.rank.mixture;

import edu.drexel.cs.db.rank.distance.PreferenceSimilarity;
import edu.drexel.cs.db.rank.distance.RatingsSimilarity;
import edu.drexel.cs.db.rank.core.ItemSet;
import edu.drexel.cs.db.rank.core.Ranking;
import edu.drexel.cs.db.rank.rating.Ratings;
import edu.drexel.cs.db.rank.rating.RatingsSample;
import edu.drexel.cs.db.rank.core.RankingSample;
import edu.drexel.cs.db.rank.core.Sample;
import edu.drexel.cs.db.rank.util.Histogram;
import edu.drexel.cs.db.rank.kemeny.BubbleTableKemenizator;
import edu.drexel.cs.db.rank.kemeny.KemenyCandidate;
import edu.drexel.cs.db.rank.model.MallowsModel;
import edu.drexel.cs.db.rank.preference.MapPreferenceSet;
import edu.drexel.cs.db.rank.preference.PreferenceSet;
import edu.drexel.cs.db.rank.reconstruct.MallowsReconstructor;
import edu.drexel.cs.db.rank.util.Logger;
import fr.lri.tao.apro.ap.Apro;
import fr.lri.tao.apro.data.DataProvider;
import fr.lri.tao.apro.data.MatrixProvider;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MallowsMixtureReconstructor {

  private int maxClusters = 10;
  private MallowsReconstructor reconstructor;
  private double alphaDecay = 0.65d; // 0.65d // smaller alphaDecay, more clusters; bigger alpha, more agressive clustering. 0.65 is OK

  public MallowsMixtureReconstructor(MallowsReconstructor reconstructor) {
    this.reconstructor = reconstructor;
  }

  public MallowsMixtureReconstructor(MallowsReconstructor reconstructor, int maxClusters) {
    this.reconstructor = reconstructor;
    this.maxClusters = maxClusters;
  }

  public ClusteringResult cluster(RankingSample sample) {
    return cluster(sample, 1d);
  }

  private ClusteringResult cluster(RankingSample sample, double alpha) {
    Histogram<Ranking> hist = new Histogram<Ranking>();
    hist.add(sample.rankings(), sample.weights());
    Map<Ranking, Double> weights = hist.getMap();
    List<Ranking> rankings = new ArrayList<Ranking>();
    rankings.addAll(weights.keySet());
    System.out.println(String.format("There are %d different rankings out of %d total rankings", rankings.size(), sample.size()));

    double minSim = Double.POSITIVE_INFINITY;
    double maxSim = Double.NEGATIVE_INFINITY;
    double minPref = Double.POSITIVE_INFINITY;
    double maxPref = Double.NEGATIVE_INFINITY;

    /* Create similarity matrix */
    long start = System.currentTimeMillis();
    double[][] matrix = new double[rankings.size()][rankings.size()];

    Map<PreferenceSet, PreferenceSet> transitiveClosures = new HashMap<PreferenceSet, PreferenceSet>();
    for (Ranking r : rankings) {
      transitiveClosures.put(r, r.transitiveClosure());
    }

    double lastPercent = -20;
    int done = 0;
    double nsquare100 = 200d / (matrix.length * (matrix.length - 1));
    for (int i = 0; i < matrix.length; i++) {

      double percent = nsquare100 * done;
      if (percent > lastPercent + 10) {
        System.out.print(String.format("%.0f%% ", percent));
        lastPercent = percent;
      }

      Ranking ranking = rankings.get(i);
      double pref = weights.get(ranking);
      maxPref = Math.max(maxPref, pref);
      minPref = Math.min(minPref, pref);
      matrix[i][i] = pref;

      // Similarities
      for (int j = i + 1; j < matrix.length; j++) {
        double s = PreferenceSimilarity.similarity(transitiveClosures.get(ranking), transitiveClosures.get(rankings.get(j)));
        maxSim = Math.max(maxSim, s);
        minSim = Math.min(minSim, s);
        matrix[i][j] = matrix[j][i] = s;
        done++;
      }
    }

    for (int i = 0; i < matrix.length; i++) {
      matrix[i][i] = alpha * matrix[i][i] * minSim - (1 - alpha) * maxSim;
      //matrix[i][i] *= minSim;
      //matrix[i][i] = matrix[i][i] - maxSim * (1 - alpha);
    }

    Logger.info("Pref: [%f, %f], Sim: [%f, %f]", minPref, maxPref, minSim, maxSim);
    Logger.info("Matrix %d x %d created in %d ms", matrix.length, matrix.length, System.currentTimeMillis() - start);

    /* Run Affinity Propagation */
    DataProvider provider = new MatrixProvider(matrix);
    provider.addNoise();
    int groupCount = Math.min(8, matrix.length);
    Apro apro = new Apro(provider, groupCount, false);
    apro.run(100);

    /* Get exemplars for each mapPairsOneUser */
    Map<Ranking, Ranking> exemplars = new HashMap<Ranking, Ranking>();
    Map<Ranking, RankingSample> samples = new HashMap<Ranking, RankingSample>(); // a sample for each exemplar
    for (int i = 0; i < rankings.size(); i++) {
      Ranking r = rankings.get(i);
      int exi = apro.getExemplar(i);
      Ranking exemplar = (exi != -1) ? rankings.get(exi) : r;
      exemplars.put(r, exemplar);

      // put it in the sample      
      RankingSample s = samples.get(exemplar);
      if (s == null) {
        s = new RankingSample(sample.getItemSet());
        samples.put(exemplar, s);
      }
      s.add(r, weights.get(r));
    }

    /* If there are too much clusters, do it again */
    if (samples.size() > maxClusters && maxSim > 0) { // && samples.size() < sample.size()) {      
      RankingSample more = new RankingSample(sample.getItemSet());
      // alpha = 0.3 * Math.random();
      Logger.info("%d exemplars. Compacting more with alpha = %.3f...", samples.size(), alpha);
      for (Ranking r : samples.keySet()) {
        double w = samples.get(r).sumWeights();
        more.add(r, w);
      }

      ClusteringResult sub = cluster(more, alphaDecay * alpha);
      Map<Ranking, Ranking> newExs = new HashMap<Ranking, Ranking>();
      Map<Ranking, RankingSample> newSamps = new HashMap<Ranking, RankingSample>();
      for (Ranking r : rankings) {
        Ranking ex1 = exemplars.get(r);
        Ranking ex2 = sub.exemplars.get(ex1);
        newExs.put(r, ex2);

        RankingSample s = newSamps.get(ex2);
        if (s == null) {
          s = new RankingSample(sample.getItemSet());
          newSamps.put(ex2, s);
        }
        s.add(r, weights.get(r));
      }

      exemplars = newExs;
      samples = newSamps;
    }

    return new ClusteringResult(exemplars, samples);
  }

  public ClusteringResultPairs cluster(Sample<MapPreferenceSet> sample) {
    return cluster(sample, 1d);
  }

  private ClusteringResultPairs cluster(Sample<MapPreferenceSet> sample, double alpha) {
    Histogram<MapPreferenceSet> hist = new Histogram<MapPreferenceSet>();
    hist.add(sample.preferenceSets(), sample.weights());
    Map<MapPreferenceSet, Double> weights = hist.getMap();
    List<MapPreferenceSet> rankings = new ArrayList<MapPreferenceSet>();
    rankings.addAll(weights.keySet());
    System.out.println(String.format("There are %d different rankings out of %d total rankings", rankings.size(), sample.size()));

    double minSim = Double.POSITIVE_INFINITY;
    double maxSim = Double.NEGATIVE_INFINITY;
    double minPref = Double.POSITIVE_INFINITY;
    double maxPref = Double.NEGATIVE_INFINITY;

    /* Create similarity matrix */
    long start = System.currentTimeMillis();
    double[][] matrix = new double[rankings.size()][rankings.size()];

    Map<PreferenceSet, PreferenceSet> transitiveClosures = new HashMap<PreferenceSet, PreferenceSet>();
    for (MapPreferenceSet r : rankings) {
      transitiveClosures.put(r, r.transitiveClosure());
    }

    double lastPercent = -20;
    int done = 0;
    double nsquare100 = 200d / (matrix.length * (matrix.length - 1));
    for (int i = 0; i < matrix.length; i++) {

      double percent = nsquare100 * done;
      if (percent > lastPercent + 10) {
        System.out.print(String.format("%.0f%% ", percent));
        lastPercent = percent;
      }

      MapPreferenceSet mapPairsOneUser = rankings.get(i);
      double pref = weights.get(mapPairsOneUser);
      maxPref = Math.max(maxPref, pref);
      minPref = Math.min(minPref, pref);
      matrix[i][i] = pref;

      // Similarities
      for (int j = i + 1; j < matrix.length; j++) {
        double s = PreferenceSimilarity.similarity(transitiveClosures.get(mapPairsOneUser), transitiveClosures.get(rankings.get(j)));
        maxSim = Math.max(maxSim, s);
        minSim = Math.min(minSim, s);
        matrix[i][j] = matrix[j][i] = s;
        done++;
      }
    }

    for (int i = 0; i < matrix.length; i++) {
      matrix[i][i] = alpha * matrix[i][i] * minSim - (1 - alpha) * maxSim;
      //matrix[i][i] *= minSim;
      //matrix[i][i] = matrix[i][i] - maxSim * (1 - alpha);
    }

    Logger.info("Pref: [%f, %f], Sim: [%f, %f]", minPref, maxPref, minSim, maxSim);
    Logger.info("Matrix %d x %d created in %d ms", matrix.length, matrix.length, System.currentTimeMillis() - start);

    /* Run Affinity Propagation */
    DataProvider provider = new MatrixProvider(matrix);
    provider.addNoise();
    int groupCount = Math.min(8, matrix.length);
    Apro apro = new Apro(provider, groupCount, false);
    apro.run(100);

    /* Get exemplars for each mapPairsOneUser */
    Map<MapPreferenceSet, MapPreferenceSet> exemplars = new HashMap<>();
    Map<MapPreferenceSet, Sample<MapPreferenceSet>> samples = new HashMap<>(); // a sample for each exemplar
    for (int i = 0; i < rankings.size(); i++) {
      MapPreferenceSet r = rankings.get(i);
      int exi = apro.getExemplar(i);
      MapPreferenceSet exemplar = (exi != -1) ? rankings.get(exi) : r;
      exemplars.put(r, exemplar);

      // put it in the sample      
      Sample<MapPreferenceSet> s = samples.get(exemplar);
      if (s == null) {
        s = new Sample<>(sample.getItemSet());
        samples.put(exemplar, s);
      }
      s.add(r, weights.get(r));
    }

    /* If there are too much clusters, do it again */
    if (samples.size() > maxClusters && maxSim > 0) { // && samples.size() < sample.size()) {      
      Sample<MapPreferenceSet> more = new Sample<>(sample.getItemSet());
      // alpha = 0.3 * Math.random();
      Logger.info("%d exemplars. Compacting more with alpha = %.3f...", samples.size(), alpha);
      for (MapPreferenceSet r : samples.keySet()) {
        double w = samples.get(r).sumWeights();
        more.add(r, w);
      }

      ClusteringResultPairs sub = cluster(more, alphaDecay * alpha);
      Map<MapPreferenceSet, MapPreferenceSet> newExs = new HashMap<>();
      Map<MapPreferenceSet, Sample<MapPreferenceSet>> newSamps = new HashMap<>();
      for (MapPreferenceSet r : rankings) {
        MapPreferenceSet ex1 = exemplars.get(r);
        MapPreferenceSet ex2 = sub.exemplars.get(ex1);
        newExs.put(r, ex2);

        Sample<MapPreferenceSet> s = newSamps.get(ex2);
        if (s == null) {
          s = new Sample<MapPreferenceSet>(sample.getItemSet());
          newSamps.put(ex2, s);
        }
        s.add(r, weights.get(r));
      }

      exemplars = newExs;
      samples = newSamps;
    }

    return new ClusteringResultPairs(exemplars, samples);
  }

  public MallowsMixtureModel reconstruct(RankingSample sample) throws Exception {
    ClusteringResult clustering = cluster(sample);
    return model(clustering);
  }

  /**
   * Now reconstruct each model from ClusteringResult
   */
  private MallowsMixtureModel model(ClusteringResult clustering) throws Exception {
    MallowsMixtureModel model = new MallowsMixtureModel(clustering.getItemSet());
    BubbleTableKemenizator kemenizator = new BubbleTableKemenizator();
    int m = 0;
    for (Ranking exemplar : clustering.samples.keySet()) {
      m++;
      Logger.info("Reconstructing model %d of %d", m, clustering.samples.size());
      RankingSample s = clustering.samples.get(exemplar);
      Ranking center = KemenyCandidate.complete(exemplar);
      center = kemenizator.kemenize(s, center);
      MallowsModel mm = reconstructor.reconstruct(s, center);
      model.add(mm, s.sumWeights());
      Logger.info("Model %d of %d: %s", m, clustering.samples.size(), mm);
    }
    return model;
  }

  public int getMaxClusters() {
    return this.maxClusters;
  }

  public class ClusteringResultPairs {

    public final Map<MapPreferenceSet, MapPreferenceSet> exemplars;
    public final Map<MapPreferenceSet, Sample<MapPreferenceSet>> samples;

    private ClusteringResultPairs(Map<MapPreferenceSet, MapPreferenceSet> exemplars, Map<MapPreferenceSet, Sample<MapPreferenceSet>> samples) {
      this.exemplars = exemplars;
      this.samples = samples;
    }

    public ItemSet getItemSet() {
      for (MapPreferenceSet r : exemplars.keySet()) {
        return r.getItemSet();
      }
      for (MapPreferenceSet r : samples.keySet()) {
        return r.getItemSet();
      }
      return null;
    }

  }

  public class ClusteringResult {

    public final Map<Ranking, Ranking> exemplars;
    public final Map<Ranking, RankingSample> samples;

    private ClusteringResult(Map<Ranking, Ranking> exemplars, Map<Ranking, RankingSample> samples) {
      this.exemplars = exemplars;
      this.samples = samples;
    }

    public ItemSet getItemSet() {
      for (Ranking r : exemplars.keySet()) {
        return r.getItemSet();
      }
      for (Ranking r : samples.keySet()) {
        return r.getItemSet();
      }
      return null;
    }

  }

}
