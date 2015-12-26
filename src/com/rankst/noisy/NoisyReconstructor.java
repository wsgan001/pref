package com.rankst.noisy;

import com.rankst.entity.Ranking;
import com.rankst.entity.Sample;
import com.rankst.generator.Resampler;
import com.rankst.incomplete.IncompleteUtils;
import com.rankst.ml.TrainUtils;
import com.rankst.model.MallowsModel;
import static com.rankst.noisy.NoisyAttributes.ATTRIBUTES;
import static com.rankst.noisy.NoisyAttributes.ATTRIBUTE_ELEMENTS;
import static com.rankst.noisy.NoisyAttributes.ATTRIBUTE_SAMPLE_SIZE;
import com.rankst.reconstruct.CenterReconstructor;
import com.rankst.reconstruct.MallowsReconstructor;
import com.rankst.reconstruct.PolynomialReconstructor;
import com.rankst.util.Logger;
import com.rankst.util.MathUtils;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import weka.classifiers.trees.M5P;
import weka.core.DenseInstance;
import weka.core.Instance;
import weka.core.Instances;
import weka.core.converters.ConverterUtils;

/** In case there is noise in the sample, direct and bootstrap reconstructor should differ. We can use this to reconstruct the correct model */
public class NoisyReconstructor implements MallowsReconstructor {

  private File arff;
  private M5P classifier;
  
  public NoisyReconstructor(File arff) throws Exception {    
    this.arff = arff;
    if (arff.exists()) load();
  }  
  
  private void load() throws Exception {
    long start = System.currentTimeMillis();
    InputStream is = new FileInputStream(arff);
    ConverterUtils.DataSource source = new ConverterUtils.DataSource(is);    
    Instances data = source.getDataSet();
    is.close();
    data.setClassIndex(data.numAttributes() - 1);    
    classifier = new M5P();
    classifier.setMinNumInstances(4);    
    classifier.buildClassifier(data);
    System.out.println(String.format("Noisy regression classifier learnt in %d ms", System.currentTimeMillis() - start));
  }
  
  @Override
  public MallowsModel reconstruct(Sample sample) throws Exception {
    Ranking center = CenterReconstructor.reconstruct(sample);
    return this.reconstruct(sample, center);
  }

  @Override
  public MallowsModel reconstruct(Sample sample, Ranking center) throws Exception {
    Instance instance = new DenseInstance(ATTRIBUTES.size()); 
    instance.setValue(ATTRIBUTES.indexOf(ATTRIBUTE_ELEMENTS), sample.getElements().size());
    instance.setValue(ATTRIBUTES.indexOf(ATTRIBUTE_SAMPLE_SIZE), sample.size());
      
    if (center == null) center = CenterReconstructor.reconstruct(sample);
    PolynomialReconstructor reconstructor = new PolynomialReconstructor();


    // triangle no row
    {
      MallowsModel mallows = reconstructor.reconstruct(sample, center);
      instance.setValue(ATTRIBUTES.indexOf(NoisyAttributes.ATTRIBUTE_DIRECT_PHI), mallows.getPhi());
    }

    
    // Bootstrap
    {
      Resampler resampler = new Resampler(sample);
      double bootstraps[] = new double[NoisyAttributes.BOOTSTRAPS];
      for (int j = 0; j < bootstraps.length; j++) {
        Sample resample = resampler.resample();
        MallowsModel m = reconstructor.reconstruct(resample, center);
        bootstraps[j] = m.getPhi();
      }
      instance.setValue(ATTRIBUTES.indexOf(NoisyAttributes.ATTRIBUTE_BOOTSTRAP_PHI), MathUtils.mean(bootstraps));
      instance.setValue(ATTRIBUTES.indexOf(NoisyAttributes.ATTRIBUTE_BOOTSTRAP_VAR), MathUtils.variance(bootstraps));
    }

    Logger.info("Instance: %s", instance);
    double regressionPhi = this.classifier.classifyInstance(instance);
    return new MallowsModel(center, regressionPhi);
  }

  
  
  
  
  
  public static void main(String[] args) {
    System.out.println("NoisyReconstructor");
  }
}
