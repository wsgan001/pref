package edu.drexel.cs.db.rank.incomplete;

import edu.drexel.cs.db.rank.core.Ranking;
import edu.drexel.cs.db.rank.core.Sample;
import edu.drexel.cs.db.rank.model.MallowsModel;
import edu.drexel.cs.db.rank.reconstruct.PolynomialReconstructor;
import edu.drexel.cs.db.rank.sampler.other.AMPxDSamplerByItem;

/**
 * Creates insertion triangle from the sample of the previous iteration (only)
 * and updates it through the iteration. The first iteration uses the initial
 * sample. Dynamic, iterative, no smoothing, by item
 */
@Deprecated
public class AMPxDIByItemReconstructor extends EMReconstructor {

  private final double alpha;

  public AMPxDIByItemReconstructor(MallowsModel model, int iterations, double alpha) {
    super(model, iterations);
    this.alpha = alpha;
  }

  @Override
  public MallowsModel reconstruct(Sample sample, Ranking center) throws Exception {
    MallowsModel estimate = model;
    PolynomialReconstructor reconstructor = new PolynomialReconstructor();
    Sample resample = sample;
    double oldPhi, newPhi;
    for (int i = 0; i < iterations; i++) {
      oldPhi = estimate.getPhi();
      AMPxDSamplerByItem sampler = new AMPxDSamplerByItem(estimate, resample, alpha);
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
}