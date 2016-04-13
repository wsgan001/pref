package edu.drexel.cs.db.rank.model;

import edu.drexel.cs.db.rank.distance.KendallTauDistance;
import edu.drexel.cs.db.rank.core.ItemSet;
import edu.drexel.cs.db.rank.core.Ranking;
import edu.drexel.cs.db.rank.core.RankingSample;
import edu.drexel.cs.db.rank.core.Sample.PW;
import edu.drexel.cs.db.rank.sampler.MallowsUtils;


public class MallowsModel {

  private final Ranking center;
  private final double phi;
  
  public MallowsModel(Ranking center, double phi) {
    this.center = center;
    this.phi = phi;
  }

  public ItemSet getItemSet() {
    return this.center.getItemSet();
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
    for (int i = 1; i < this.getItemSet().size(); i++) {
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
  
  
  public double getLogProbability(Ranking r) {
    double d = KendallTauDistance.between(center, r);
    return d * Math.log(phi) - Math.log(z());
  }
    
    
  public double getLogLikelihood(RankingSample sample) {
    double ll = 0;
    double lnZ = Math.log(z());
    double lnPhi = Math.log(phi);
    for (PW<Ranking> pw: sample) {
      ll += pw.w * (KendallTauDistance.between(center, pw.p) * lnPhi - lnZ);
    }
    return ll / sample.sumWeights();
  }
    
  public static void main(String[] args) {
    ItemSet items = new ItemSet(10);
    MallowsModel model = new MallowsModel(items.getReferenceRanking(), 0.3);
    RankingSample sample = MallowsUtils.sample(model, 1000);
    System.out.println(model.getLogLikelihood(sample));
    
    
    MallowsModel model2 = new MallowsModel(items.getReferenceRanking(), 0.7);
    System.out.println(model2.getLogLikelihood(sample));
    
    MallowsModel model3 = new MallowsModel(items.getRandomRanking(), 0.3);
    System.out.println(model3.getLogLikelihood(sample));
  }
  
}
