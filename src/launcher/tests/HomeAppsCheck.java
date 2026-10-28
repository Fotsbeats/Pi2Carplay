package org.bench.boxhome;
import java.util.*;
public final class HomeAppsCheck {
 public static void main(String[] args){
  String[] base={"a.one","b.two","c.three","d.four","e.five","f.six"};
  assert Arrays.equals(base,HomeApps.load(null,base));
  assert Arrays.equals(base,HomeApps.load("broken",base));
  String[] slots=HomeApps.load("a.one||a.one|bad/name|com.android.vending|f.six",base);
  assert Arrays.equals(slots,new String[]{"a.one","","","","","f.six"});
  HomeApps.set(slots,1,"f.six");assert slots[1].equals("f.six")&&slots[5].isEmpty();
  HomeApps.set(slots,0,"f.six");assert slots[0].equals("f.six")&&slots[1].equals("a.one");
  HomeApps.set(slots,0,"");assert slots[0].isEmpty();
  assert Arrays.equals(slots,HomeApps.load(HomeApps.save(slots),base));
  String[] empty=HomeApps.load("|||||",base);assert Arrays.equals(empty,new String[]{"","","","","",""});
  boolean rejected=false;try{HomeApps.set(slots,2,"com.android.vending");}catch(IllegalArgumentException e){rejected=true;}assert rejected;
  System.out.println("PASS: home slot defaults, persistence, blanks, swap/reorder, duplicate/corrupt settings and Store restriction");
 }
}
