package cn.usbdoctor;

/** Kernel link evidence only; Android network registration is a separate layer. */
final class LinkState {
    enum Status { DISABLED, NO_CARRIER, LINK, WAITING, UNKNOWN }
    final String name, operstate, carrier, speed, duplex, flags;
    final Status status;
    LinkState(String[] p) {
        name=p[1]; operstate=p[2]; carrier=p[3]; speed=p[4]; duplex=p[5]; flags=p.length>6?p[6]:"";
        Boolean admin=adminUp(flags);
        if(Boolean.FALSE.equals(admin))status=Status.DISABLED;
        else if("1".equals(carrier))status=("dormant".equals(operstate)||"testing".equals(operstate))?Status.WAITING:Status.LINK;
        else if("0".equals(carrier))status=Status.NO_CARRIER;
        else status=Status.UNKNOWN;
    }
    static Boolean adminUp(String flags) {
        try { return (Long.decode(flags)&1L)!=0; } catch(NumberFormatException e) { return null; }
    }
    String label() {
        switch(status) {
            case DISABLED:return "接口未启用";
            case NO_CARRIER:return "驱动未报告链路";
            case LINK:return "链路已建立";
            case WAITING:return "链路已建立 · 等待就绪";
            default:return "链路状态未知";
        }
    }
    String hint() {
        switch(status) {
            case DISABLED:return "接口管理状态为 DOWN，不代表网线未插。先采集诊断，Root 可尝试启用网卡接口。";
            case NO_CARRIER:return "carrier=0；可能涉及链路协商、驱动初始化、休眠或物理连接，不能仅凭此判定没插网线。先采集诊断，再尝试重启网卡接口。";
            case LINK:return "驱动报告 carrier=1。能否上网还需核对该接口的 IP、路由和系统网络验证。";
            case WAITING:return "驱动报告 carrier=1，但接口仍在等待协议或测试完成。请核对系统以太网诊断。";
            default:return "未取得有效 carrier 数据，可能受权限或接口状态限制。不能判断网线是否连接；请用 Shizuku / Root 采集诊断。";
        }
    }
    String detail() {
        Boolean admin=adminUp(flags);
        boolean hasSpeed=(status==Status.LINK||status==Status.WAITING)&&speed.matches("[1-9][0-9]*");
        return name+" · "+label()+"\n管理状态 "+(admin==null?"未知":admin?"UP":"DOWN")
            +" · 运行状态 "+(operstate.isEmpty()?"未知":operstate)+" · carrier="+(carrier.isEmpty()?"不可读":carrier)
            +"\n协商速率 "+(hasSpeed?speed+" Mb/s":"未知")+" · 双工 "+(hasSpeed&&!duplex.isEmpty()?duplex:"未知")+"\n";
    }
}
