package org.bench.boxhome;
public final class LayoutRulesCheck {
 public static void main(String[] args) {
  int[] extras={0,1,12,13,24,25},pages={1,2,2,3,3,4};
  assert LayoutRules.COLUMNS==6 && LayoutRules.PAGE_SIZE==12;
  assert !LayoutRules.isDoubleTap(-1,100,300);
  assert LayoutRules.isDoubleTap(100,399,300);
  assert LayoutRules.isDoubleTap(100,400,300);
  assert !LayoutRules.isDoubleTap(100,401,300);
  assert !LayoutRules.isDoubleTap(100,99,300);
  assert LayoutRules.localRailWidth(1744,0)==104;
  assert LayoutRules.localRailWidth(1744,104)==0;
  assert LayoutRules.localRailWidth(1744,200)==0;
  assert LayoutRules.localRailWidth(872,0)==104;
  for(int i=0;i<extras.length;i++)assert LayoutRules.pages(extras[i])==pages[i];
  assert LayoutRules.snap(0,121,1000,3)==1;
  assert LayoutRules.snap(1,-121,1000,3)==0;
  assert LayoutRules.snap(1,80,1000,3)==1;
  assert LayoutRules.snap(2,800,1000,3)==2;
  assert LayoutRules.snap(0,-800,1000,3)==0;
  assert LayoutRules.snap(0,500,0,3)==0;
  System.out.println("PASS: native page counts, swipe threshold, boundaries and zero-width guard.");
 }
}
