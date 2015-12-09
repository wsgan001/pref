package com.rankst.triangle;

import com.rankst.entity.ElementSet;
import com.rankst.entity.Ranking;
import java.security.SecureRandom;
import java.util.Random;


public abstract class Triangle {

  public static final Random random = new SecureRandom();
  
  protected final Ranking reference;
  
  public Triangle(Ranking reference) {
    this.reference = reference;    
  }
  
  public ElementSet getElements() {
    return this.reference.getElementSet();
  }

  public Ranking getReference() {
    return reference;
  } 
  
  /** Get random position for the element e from the triangle */
  public abstract int randomPosition(int e);
  
  
  
}
