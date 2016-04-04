package edu.drexel.cs.db.rank.loader;

import edu.drexel.cs.db.rank.core.Item;
import edu.drexel.cs.db.rank.core.ItemSet;
import edu.drexel.cs.db.rank.core.Sample;
import edu.drexel.cs.db.rank.rating.RatingSet;
import edu.drexel.cs.db.rank.util.FileUtils;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringTokenizer;

/** Loads RatingsSample from the text file (user_id, item_id, rating [, weight]) */
public class RatingsLoader {

  private final ItemSet itemSet;
  private final Sample<RatingSet> sample;
  private final String delimiters;
  
  private Map<String, Item> tags = new HashMap<String, Item>();
  
  public RatingsLoader(File file) throws IOException {
    this(new FileReader(file), ", \t;");
  }
  
  /** Loads sample from the reader
   * @param reader Source of data
   * @param delimiters Commas, tabs, semi-colons...
   */
  public RatingsLoader(Reader reader, String delimiters) throws IOException {
    this.delimiters = delimiters;
    
    List<String> lines = FileUtils.readLines(reader);
    
    // Load items
    this.itemSet = getItemSet(lines);
    for (Item e: itemSet) {
      tags.put((String) e.getTag(), e);
    }
    
    // Load ratings
    this.sample = new Sample<RatingSet>(itemSet);
    loadSample(lines);
  }
  
  
  private void loadSample(List<String> lines) {
    Map<Integer, RatingSet> users = new HashMap<Integer, RatingSet>();
    for (String line: lines) {
      try {
        StringTokenizer tokenizer = new StringTokenizer(line, delimiters);
        Integer uid = Integer.valueOf(tokenizer.nextToken());
        String itemId = tokenizer.nextToken();
        Float val = Float.valueOf(tokenizer.nextToken());
        RatingSet ratings = users.get(uid);
        if (ratings == null) {
          ratings = new RatingSet(itemSet);
          users.put(uid, ratings);
        }
        ratings.put(tags.get(itemId), val);
      }
      catch (NumberFormatException skip) {}
    }
    
    for (RatingSet ratings: users.values()) {
      sample.add(ratings);
    }
  }

  
  private ItemSet getItemSet(List<String> lines) {
    Set<String> ids = new HashSet<String>();
    for (String line: lines) {
      try {
        StringTokenizer tokenizer = new StringTokenizer(line, delimiters);
        int uid = Integer.parseInt(tokenizer.nextToken()); // skip uid
        String itemId = tokenizer.nextToken();
        ids.add(itemId);
      }
      catch (NumberFormatException skip) {}
    }
    return new ItemSet(ids.toArray());
  }

  public Sample<RatingSet> getRatingsSample() {
    return sample;
  }  
}
