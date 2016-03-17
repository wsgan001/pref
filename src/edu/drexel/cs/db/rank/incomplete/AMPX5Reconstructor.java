package edu.drexel.cs.db.rank.incomplete;

import edu.drexel.cs.db.rank.core.Ranking;
import edu.drexel.cs.db.rank.core.Sample;
import edu.drexel.cs.db.rank.model.MallowsModel;
import edu.drexel.cs.db.rank.preference.MapPreferenceSet;
import edu.drexel.cs.db.rank.reconstruct.PolynomialReconstructor;
import edu.drexel.cs.db.rank.sampler.AMPSamplerXD;

/**
 * Starts from the sample from the previous iteration, and updates it during
 * iteration dynamic, iterative, no smoothing
 */
public class AMPX5Reconstructor extends EMReconstructor {

  private final double alpha;

  public AMPX5Reconstructor(MallowsModel model, int iterations, double alpha) {
    super(model, iterations);
    this.alpha = alpha;
  }

  @Override
  public MallowsModel reconstruct(Sample<Ranking> sample, Ranking center) throws Exception {
    MallowsModel estimate = model;
    PolynomialReconstructor reconstructor = new PolynomialReconstructor();
    Sample<Ranking> resample = sample;
    double oldPhi, newPhi;
    for (int i = 0; i < iterations; i++) {
      oldPhi = estimate.getPhi();
      AMPSamplerXD sampler = new AMPSamplerXD(estimate, resample, alpha);
      if (listener != null) {
        listener.onIterationStart(i, estimate, sample);
      }
      resample = sampler.sample(sample);
      estimate = reconstructor.reconstruct(resample, center);
      if (listener != null) {
        listener.onIterationEnd(i, estimate, resample);
      }
      newPhi = estimate.getPhi();
      if (Math.abs(newPhi - oldPhi) < 0.001) {
        break;
      }
    }

    return estimate;
  }
    public MallowsModel reconstructFromPairs(Sample<MapPreferenceSet> sample, Ranking center) throws Exception {
    MallowsModel estimate = model;
    PolynomialReconstructor reconstructor = new PolynomialReconstructor();
    Sample<Ranking> resample = new Sample<>(sample.getItemSet());
    double oldPhi, newPhi;
    for (int i = 0; i < iterations; i++) {
      oldPhi = estimate.getPhi();
      AMPSamplerXD sampler = new AMPSamplerXD(estimate, resample, alpha);
      if (listenerPairs != null) {
        listenerPairs.onIterationStart(i, estimate, sample);
      }
      resample = sampler.sample(sample);
      estimate = reconstructor.reconstruct(resample, center);
      if (listener != null) {
        listener.onIterationEnd(i, estimate, resample);
      }
      newPhi = estimate.getPhi();
      if (Math.abs(newPhi - oldPhi) < 0.001) {
        break;
      }
    }

    return estimate;
  }
  
}
