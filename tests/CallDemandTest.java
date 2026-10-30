package org.bench.pi2call;
public final class CallDemandTest {
 public static void main(String[] args){
  CallDemand d=new CallDemand();d.car=true;d.phoneCall(true);d.bluetooth(true);assert d.sources()==1;
  d.bluetooth(false);assert d.sources()==4;d.bluetooth(true);assert d.sources()==1;
  d.bluetooth(false);d.phoneCall(false);assert d.sources()==0;
  d.phoneCall(true);assert d.sources()==0;d.bluetooth(true);d.phoneCall(false);d.bluetooth(false);d.phoneCall(true);assert d.sources()==0;d.phoneCall(false);
  d.apps=true;assert d.sources()==2;d.bluetooth(true);assert d.sources()==3;
  d.car=false;assert d.sources()==0;
  System.out.println("PASS: phone route hand-back retains CarPlay, and app microphone demand works without Bluetooth calls");
 }
}
