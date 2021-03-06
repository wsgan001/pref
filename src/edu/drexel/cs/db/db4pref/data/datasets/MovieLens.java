package edu.drexel.cs.db.db4pref.data.datasets;

import edu.drexel.cs.db.db4pref.data.RatingsLoader;
import edu.drexel.cs.db.db4pref.core.ItemSet;
import edu.drexel.cs.db.db4pref.core.Ranking;
import edu.drexel.cs.db.db4pref.core.RankingSample;
import edu.drexel.cs.db.db4pref.core.Sample;
import edu.drexel.cs.db.db4pref.filter.Split;
import edu.drexel.cs.db.db4pref.sampler.MallowsUtils;
import edu.drexel.cs.db.db4pref.distance.KL;
import edu.drexel.cs.db.db4pref.mixture.MallowsMixtureModel;
import edu.drexel.cs.db.db4pref.mixture.MallowsMixtureReconstructor;
import edu.drexel.cs.db.db4pref.model.MallowsModel;
import edu.drexel.cs.db.db4pref.core.PairwisePreferenceMatrix;
import edu.drexel.cs.db.db4pref.core.PreferenceSet;
import edu.drexel.cs.db.db4pref.core.Ratings;
import edu.drexel.cs.db.db4pref.reconstruct.MallowsReconstructor;
import edu.drexel.cs.db.db4pref.util.Logger;
import java.io.File;
import java.io.IOException;
import java.util.List;

/** Class used for loading and accessing MovieLens dataset */
public class MovieLens {
    
  private File data;
  
  /** Load MovieLens dataset from the specified file */
  public MovieLens(File data) {
    this.data = data;
  }
  
  
  public Sample<Ratings> getSample() throws IOException {
    Sample<Ratings> sample = new RatingsLoader(data).getRatingsSample();
    Logger.info("MovieLens dataset loaded: %d users, %d movies", sample.size(), sample.getItemSet().size());
    return sample;
  }
  
  public ItemSet getItemSet() throws IOException {
    return getSample().getItemSet();
  }
  
//    [Model 1] Center = 923-858-750-527-904-318-2858-1221-1193-912-50-296-1136-608-2324-1247-908-1213-1252-1198, phi = 0.98, weight = 24
//    [Model 2] Center = 858-750-923-912-260-50-1198-1136-904-913-541-1193-1206-924-908-296-1221-1252-1208-318, phi = 0.98, weight = 23
//    [Model 3] Center = 1198-858-527-904-260-318-912-2762-1219-923-1234-50-2028-1221-593-919-750-1387-110-1200, phi = 0.98, weight = 21
//    [Model 4] Center = 318-2324-1198-527-260-2571-2762-1234-356-50-110-3147-1291-2028-1197-1196-593-1704-2918-1307, phi = 0.98, weight = 19
//    [Model 5] Center = 50-318-527-2324-2804-1288-2858-2762-296-1197-593-858-356-2959-608-2918-1394-2028-1704-2571, phi = 0.97, weight = 13  
  public MallowsMixtureModel getGrimModel() throws IOException {
    ItemSet items = getItemSet();
    MallowsMixtureModel model = new MallowsMixtureModel(items);

    Ranking c1 = Ranking.fromStringByTag(items, "923-858-750-527-904-318-2858-1221-1193-912-50-296-1136-608-2324-1247-908-1213-1252-1198");
    model.add(new MallowsModel(c1, 0.98), 24);
    Ranking c2 = Ranking.fromStringByTag(items, "858-750-923-912-260-50-1198-1136-904-913-541-1193-1206-924-908-296-1221-1252-1208-318");
    model.add(new MallowsModel(c2, 0.98), 23);
    Ranking c3 = Ranking.fromStringByTag(items, "1198-858-527-904-260-318-912-2762-1219-923-1234-50-2028-1221-593-919-750-1387-110-1200");
    model.add(new MallowsModel(c3, 0.98), 21);
    Ranking c4 = Ranking.fromStringByTag(items, "318-2324-1198-527-260-2571-2762-1234-356-50-110-3147-1291-2028-1197-1196-593-1704-2918-1307");
    model.add(new MallowsModel(c4, 0.98), 19);
    Ranking c5 = Ranking.fromStringByTag(items, "50-318-527-2324-2804-1288-2858-2762-296-1197-593-858-356-2959-608-2918-1394-2028-1704-2571");
    model.add(new MallowsModel(c5, 0.97), 13);
    
    return model;
  }
  
}
