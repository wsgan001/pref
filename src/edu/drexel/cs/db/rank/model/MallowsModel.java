package edu.drexel.cs.db.rank.model;

import edu.drexel.cs.db.rank.distance.KendallTauDistance;
import edu.drexel.cs.db.rank.entity.ElementSet;
import edu.drexel.cs.db.rank.entity.Ranking;
import edu.drexel.cs.db.rank.entity.Sample;
import edu.drexel.cs.db.rank.generator.MallowsUtils;


public class MallowsModel {

  private final Ranking center;
  private final double phi;
  
  public MallowsModel(Ranking center, double phi) {
    this.center = center;
    this.phi = phi;
  }

  public ElementSet getElements() {
    return this.center.getElementSet();
  }

  public Ranking getCenter() {
    return center;
  }

  public double getPhi() {
    return phi;
  }
  
  /** Expected distance from the center, depends only on phi */
  public double getE() {
    return phiToE(phi);
  }

  @Override
  public boolean equals(Object obj) {
    if (!(obj instanceof MallowsModel)) return false;
    MallowsModel mm = (MallowsModel) obj;
    return this.center.equals(mm.center) && this.phi == mm.phi;
  }

  @Override
  public String toString() {
    return "Center: " + center + "; phi: " + phi;
  }
  
  /** Convert phi to expected distance */
  public static double phiToE(double phi) {
    return phi / (1 - phi);
  }
  
  /** Convert expected distance to phi */
  public static double eToPhi(double e) {
    return e / (e+1);
  }
  
  /** Normalization factor */
  public double z() {
    double z = 1;
    double s = 1;
    double phip = 1;
    for (int i = 1; i < this.getElements().size(); i++) {
      phip *= phi;
      s  += phip;
      z *= s;
    }
    return z;
  }
  
  
  /** @return Probability of the ranking being generated by this model */
  public double getProbability(Ranking r) {
    double d = KendallTauDistance.between(center, r);
    return Math.pow(phi, d) / z();
  }
  
  public double getLogLikelihood(Sample sample) {
    double ll = 0;
    double lnZ = Math.log(z());
    double lnPhi = Math.log(phi);
    for (Sample.RW rw: sample.enumerate()) {
      ll += rw.w * (KendallTauDistance.between(center, rw.r) * lnPhi - lnZ);
    }
    return ll / sample.sumWeights();
  }
    
  public static void main(String[] args) {
    ElementSet elements = new ElementSet(10);
    MallowsModel model = new MallowsModel(elements.getReferenceRanking(), 0.3);
    Sample sample = MallowsUtils.sample(model, 1000);
    System.out.println(model.getLogLikelihood(sample));
    
    
    MallowsModel model2 = new MallowsModel(elements.getReferenceRanking(), 0.7);
    System.out.println(model2.getLogLikelihood(sample));
    
    MallowsModel model3 = new MallowsModel(elements.getRandomRanking(), 0.3);
    System.out.println(model3.getLogLikelihood(sample));
  }
  
}
