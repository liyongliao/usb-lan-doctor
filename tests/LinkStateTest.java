package cn.usbdoctor;

public class LinkStateTest {
    static int cases;
    static LinkState expect(String row,LinkState.Status expected) {
        LinkState link=new LinkState(row.split("\\|",-1));
        if(link.status!=expected)throw new AssertionError(row+": "+link.status+" != "+expected);
        if(link.detail().contains("未插")||link.label().contains("网线"))throw new AssertionError("Physical cable inferred");
        cases++;return link;
    }
    public static void main(String[] args) {
        expect("NET|eth0|down||-1||0x1002",LinkState.Status.DISABLED);
        expect("NET|eth0|down|0|-1||0x1002",LinkState.Status.DISABLED);
        expect("NET|eth0|down||-1||0x1003",LinkState.Status.UNKNOWN);
        expect("NET|eth0|down||-1||",LinkState.Status.UNKNOWN);
        expect("NET|eth0|down|0|-1||0x1003",LinkState.Status.NO_CARRIER);
        expect("NET|eth0|down|0|-1||",LinkState.Status.NO_CARRIER);
        expect("NET|eth0|dormant|1|2500|full|0x1003",LinkState.Status.WAITING);
        expect("NET|eth0|testing|1|2500|full|0x1003",LinkState.Status.WAITING);
        expect("NET|eth0|up|1|2500|full|0x1003",LinkState.Status.LINK);
        expect("NET|eth0|up|1|2500|full",LinkState.Status.LINK);
        expect("NET|eth0|up|1|2500|full|invalid",LinkState.Status.LINK);
        expect("NET|eth0|up||2500|full|0x1003",LinkState.Status.UNKNOWN);
        expect("NET|eth0|unknown|Permission denied|||0x1003",LinkState.Status.UNKNOWN);
        LinkState stale=expect("NET|eth0|down|0|2500|full|0x1003",LinkState.Status.NO_CARRIER);
        if(stale.detail().contains("2500 Mb/s"))throw new AssertionError("Stale speed shown as negotiated");
        expect("NET|eth0|down|1|2500|full|0x1002",LinkState.Status.DISABLED);
        System.out.println("PASS: "+cases+" link-state cases, including unreadable carrier, admin DOWN and stale speed");
    }
}
