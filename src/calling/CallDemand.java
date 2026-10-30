package org.bench.pi2call;
final class CallDemand {
    static final int BLUETOOTH=1,APPS=2,HELD=4;
    boolean car,bluetooth,apps,phoneCall,hadBluetooth;
    int sources() {
        if(!car)return 0;
        int result=(bluetooth?BLUETOOTH:0)|(apps?APPS:0);
        if(result==0 && phoneCall && hadBluetooth)result=HELD;
        return result;
    }
    void bluetooth(boolean connected){bluetooth=connected;if(connected)hadBluetooth=true;else if(!phoneCall)hadBluetooth=false;}
    void phoneCall(boolean active){phoneCall=active;if(!active && !bluetooth)hadBluetooth=false;}
}
