package cn.usbdoctor;

import android.app.*;
import android.os.*;
import android.content.*;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.hardware.usb.*;
import android.net.*;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.text.SimpleDateFormat;
import java.util.*;
import java.util.concurrent.*;
import java.util.regex.*;

public class MainActivity extends Activity {
    static final int BG=0xff0b1420, CARD=0xff152333, INK=0xffedf4f8, MUTED=0xff9aafc3, GREEN=0xff55e0c2, AMBER=0xffffcb7b;
    final Handler ui=new Handler(Looper.getMainLooper());
    final ExecutorService worker=Executors.newSingleThreadExecutor();
    final ArrayList<Device> devices=new ArrayList<>();
    final ArrayList<String> ports=new ArrayList<>();
    final ArrayList<Button> controls=new ArrayList<>();
    TextView headline, detail, rootLabel, deviceText, networkText, logText, busyText;
    LinearLayout body; Switch monitor;
    boolean busy=false, foreground=false, destroyed=false, root=false;
    String su="su", latest="", lastFingerprint="", exportText="";
    final StringBuilder events=new StringBuilder();
    File logFile;
    final Runnable ticker=new Runnable(){ public void run(){ if(foreground && monitor.isChecked() && !busy) scan(false); if(foreground) ui.postDelayed(this,5000); }};
    final BroadcastReceiver receiver=new BroadcastReceiver(){ public void onReceive(Context c,Intent i){ event("USB 事件："+i.getAction()); if(!busy) scan(false); }};

    static class Device {
        String name,vid,pid,label,speed,drivers,nets,power;
        Device(String[] p){name=p[1];vid=p[2];pid=p[3];label=p[4];speed=p[5];drivers=p[6].trim();nets=p[7].trim();power=p[8];}
        boolean ethernet(){return (vid.equals("0bda") && Arrays.asList("8152","8153","8155","8156","8157").contains(pid)) || (vid.equals("0b95") && Arrays.asList("1790","178a","772b","7720").contains(pid)) || drivers.matches(".*(r8152|r8153_ecm|rtl8150|asix|ax88179_178a|aqc111|cdc_ether|cdc_ncm).* ".trim());}
        String title(){ return (label.isEmpty()?"USB 设备":label)+" · "+vid+":"+pid+" · "+name; }
    }
    static class Result { int code; String text; Result(int c,String t){code=c;text=t;} }
    interface Job { String run() throws Exception; }
    @Override public void onCreate(Bundle b){
        super.onCreate(b);
        logFile=new File(getFilesDir(),"session.txt");
        try { for(String a:new String[]{"snapshot.sh","diagnose.sh","recover.sh"}) copyAsset(a); } catch(Exception e){Toast.makeText(this,e.toString(),Toast.LENGTH_LONG).show();}
        root=getPreferences(0).getBoolean("root",false); su=getPreferences(0).getString("su","su");
        build();
        IntentFilter f=new IntentFilter();f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_EXPORTED); else registerReceiver(receiver,f);
        event("应用启动 · "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);
        scan(false);
    }
    @Override public void onResume(){super.onResume();foreground=true;ui.removeCallbacks(ticker);ui.postDelayed(ticker,5000);}
    @Override public void onPause(){foreground=false;ui.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroy(){destroyed=true;ui.removeCallbacks(ticker);unregisterReceiver(receiver);worker.shutdown();super.onDestroy();}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    void gap(LinearLayout p,int n){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    LinearLayout card(String title){ LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(c,lp);TextView h=text(title,13,GREEN);h.setTypeface(null,Typeface.BOLD);c.addView(h);gap(c,12);return c; }
    void button(LinearLayout parent,String title,boolean primary,Runnable run){Button b=new Button(this);b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(bg(primary?GREEN:0xff24384b,12));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));lp.topMargin=dp(9);parent.addView(b,lp);b.setOnClickListener(v->run.run());controls.add(b);}
    void build(){
        ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);scroll.setBackgroundColor(BG);
        body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(20),dp(20),dp(30));scroll.addView(body);
        setContentView(scroll);
        scroll.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        TextView eyebrow=text("USB / ETHERNET LAB",12,GREEN);eyebrow.setLetterSpacing(.16f);body.addView(eyebrow);gap(body,8);
        TextView title=text("USB 网卡医生",30,INK);title.setTypeface(null,Typeface.BOLD);body.addView(title);gap(body,6);
        body.addView(text("RTL8156 · 小米 Root 诊断工具",14,MUTED));gap(body,22);
        LinearLayout status=card("01  /  连接状态");
        headline=text("正在读取设备…",23,INK);headline.setTypeface(null,Typeface.BOLD);status.addView(headline);gap(status,8);
        detail=text("",14,MUTED);status.addView(detail);gap(status,10);
        rootLabel=text("",13,AMBER);status.addView(rootLabel);updateRootLabel();
        busyText=text("",12,GREEN);status.addView(busyText);
        button(status,"刷新状态",true,()->scan(true));
        button(status,"申请 / 检查 Root",false,()->checkRoot());
        monitor=new Switch(this);monitor.setText("前台每 5 秒观察插拔");monitor.setTextSize(13);monitor.setTextColor(MUTED);monitor.setChecked(true);status.addView(monitor);
        LinearLayout device=card("02  /  USB 与网络");deviceText=text("等待扫描",14,INK);deviceText.setTextIsSelectable(true);device.addView(deviceText);gap(device,14);networkText=text("",13,MUTED);networkText.setTextIsSelectable(true);device.addView(networkText);
        LinearLayout repair=card("03  /  无提示、无法识别");repair.addView(text("先采集故障日志，再执行恢复。仅在网卡接着手机时使用下方操作。",13,MUTED));
        button(repair,"采集完整诊断",true,()->diagnose());
        button(repair,"请求 OTG 主机模式",false,()->portAction("host"));
        button(repair,"重启 USB 数据连接",false,()->portAction("cycle"));
        LinearLayout driver=card("04  /  已枚举的网卡");driver.addView(text("只操作所选网卡。完全没有枚举时，这些操作不可用。",13,MUTED));
        button(driver,"禁止网卡休眠（本次连接）",false,()->deviceAction("power"));
        button(driver,"重新授权网卡 / 重新识别",false,()->deviceAction("reauth"));
        button(driver,"重新探测未绑定的驱动",false,()->deviceAction("probe"));
        button(driver,"设置网卡自动电源管理",false,()->deviceAction("auto"));
        LinearLayout log=card("05  /  诊断记录");log.addView(text("只保存在手机本地。导出含设备、IP 和 USB 日志，分享前可先检查内容。",12,MUTED));
        button(log,"导出诊断 TXT",false,()->exportReport());
        button(log,"查看使用说明",false,()->help());gap(log,12);
        logText=text("",12,MUTED);logText.setTypeface(Typeface.MONOSPACE);logText.setTextIsSelectable(true);log.addView(logText);
        body.addView(text("v1.0 · 本地离线 · 无联网权限\n操作成功只代表命令执行完成，恢复以实际枚举和网络状态为准。",11,MUTED));
    }
    void updateRootLabel(){rootLabel.setText(root?"ROOT 已授权 · 恢复功能可用":"ROOT 未验证 · SukiSU 中需允许本应用");rootLabel.setTextColor(root?GREEN:AMBER);}
    void setBusy(boolean b,String label){busy=b;for(Button v:controls)v.setEnabled(!b);busyText.setText(b?label:"");}
    void task(String label,Job job,java.util.function.Consumer<String> done){
        if(busy)return;setBusy(true,label);
        worker.execute(()->{String result;try{result=job.run();}catch(Exception e){result="执行失败："+e.toString();}final String out=result;ui.post(()->{if(destroyed)return;setBusy(false,"");done.accept(out);});});
    }
    synchronized void appendFile(String s){try{if(logFile.length()>2*1024*1024){File old=new File(getFilesDir(),"previous-session.txt");old.delete();logFile.renameTo(old);}try(FileOutputStream f=new FileOutputStream(logFile,true)){f.write(s.getBytes(StandardCharsets.UTF_8));}}catch(Exception ignored){}}
    void event(String s){String line=new SimpleDateFormat("MM-dd HH:mm:ss",Locale.ROOT).format(new Date())+"  "+s+"\n";events.append(line);if(events.length()>10000)events.delete(0,events.length()-8000);appendFile(line);if(logText!=null)logText.setText(events.substring(Math.max(0,events.length()-3500)));}
    void copyAsset(String name)throws IOException{try(InputStream in=getAssets().open(name);OutputStream out=new FileOutputStream(new File(getFilesDir(),name))){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)out.write(b,0,n);}}
    static String quote(String s){return "'"+s.replace("'","'\\''")+"'";}
    Result process(String[] args,int seconds)throws Exception{
        java.lang.Process p=new ProcessBuilder(args).redirectErrorStream(true).start();p.getOutputStream().close();
        ByteArrayOutputStream out=new ByteArrayOutputStream();
        Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1){synchronized(out){if(out.size()<600000)out.write(b,0,Math.min(n,600000-out.size()));}}}catch(IOException ignored){}});reader.setDaemon(true);reader.start();
        boolean ended=p.waitFor(seconds,TimeUnit.SECONDS);if(!ended)p.destroyForcibly();reader.join(1500);
        String s; synchronized(out){s=out.toString("UTF-8");}return new Result(ended?p.exitValue():124,s+(ended?"":"\n操作超时；请检查 Root 授权或系统日志。"));
    }
    Result shell(String command,boolean elevated,int seconds)throws Exception{return process(elevated?new String[]{su,"-c",command}:new String[]{"/system/bin/sh","-c",command},seconds);}
    String script(String name,String... args){String c="/system/bin/sh "+quote(new File(getFilesDir(),name).getAbsolutePath());for(String a:args)c+=" "+quote(a);return c;}
    void checkRoot(){
        task("等待 Root 授权，最长 45 秒…",()->{
            StringBuilder failures=new StringBuilder();
            for(String candidate:new String[]{"su","/debug_ramdisk/su","/sbin/su","/system/xbin/su","/system/bin/su"}){
                try{Result r=process(new String[]{candidate,"-c","id -u"},45);if(r.code==0 && r.text.trim().equals("0")){su=candidate;return "ROOT_OK";}failures.append(candidate).append(": ").append(r.text).append("\n");break;}catch(IOException e){failures.append(candidate).append(": 不可见\n");}
            }return failures+"\nSukiSU / KernelSU 通常需要先在管理器的超级用户列表中手动允许 USB 网卡医生，再返回重试。";
        },out->{root=out.equals("ROOT_OK");getPreferences(0).edit().putBoolean("root",root).putString("su",su).apply();updateRootLabel();event(root?"Root 授权验证通过":out);if(!root)new AlertDialog.Builder(this).setTitle("尚未取得 Root").setMessage(out).setPositiveButton("知道了",null).show();scan(false);});
    }
    String androidSnapshot(){
        StringBuilder b=new StringBuilder("\nAndroid USB API:\n");
        UsbManager m=(UsbManager)getSystemService(USB_SERVICE);
        try{for(UsbDevice d:m.getDeviceList().values())b.append(String.format(Locale.ROOT,"%04x:%04x %s %s\n",d.getVendorId(),d.getProductId(),d.getDeviceName(),d.getProductName()));}catch(Exception e){b.append(e).append('\n');}
        b.append("\nAndroid Ethernet:\n");
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);boolean has=false;
        for(Network n:cm.getAllNetworks()){NetworkCapabilities c=cm.getNetworkCapabilities(n);if(c!=null && c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){has=true;LinkProperties lp=cm.getLinkProperties(n);b.append("ETHERNET_REGISTERED ").append(lp==null?"":lp.toString()).append('\n');b.append("INTERNET_VALIDATED=").append(c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)).append('\n');}}
        if(!has)b.append("系统尚未注册以太网网络\n");
        try{Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es!=null && es.hasMoreElements()){NetworkInterface ni=es.nextElement();String name=ni.getName();if(name.matches("(eth|en|usb).*"))b.append("地址 ").append(name).append(" ").append(Collections.list(ni.getInetAddresses())).append('\n');}}catch(Exception e){b.append("地址读取受限\n");}
        return b.toString();
    }
    String collectSnapshot()throws Exception {
        Result r=shell(script("snapshot.sh"),root,15);
        if(root && (r.code!=0 || !r.text.contains("ROOT|yes"))){root=false;getPreferences(0).edit().putBoolean("root",false).apply();r=shell(script("snapshot.sh"),false,10);r.text="LIMIT|Root 已失效，已切换普通诊断\n"+r.text;}
        return r.text+androidSnapshot();
    }
    void scan(boolean explicit){task("读取 USB、角色与网卡状态…",()->collectSnapshot(),out->{latest=out;render(out);if(explicit)event("手动刷新完成");});}
    void render(String raw){
        devices.clear();ports.clear();StringBuilder net=new StringBuilder();boolean netExists=false,carrier=false;
        for(String line:raw.split("\n")){String[] p=line.split("\\|",-1);if(p[0].equals("DEV") && p.length>=9)devices.add(new Device(p));if(p[0].equals("NET") && p.length>=6){netExists=true;carrier|=p[3].equals("1");net.append(p[1]).append(" · ").append(p[2]).append(" · 链路 ").append(p[3].equals("1")?"已连接":"未连接").append("\n协商速率 ").append(p[4].isEmpty()||p[4].equals("-1")?"未知":p[4]+" Mb/s").append(" · ").append(p[5]).append('\n');}}
        Matcher pm=Pattern.compile("(?m)(?:^\\s*id=|UsbPort\\{id=)([A-Za-z0-9_.:-]+)").matcher(raw);while(pm.find())if(!ports.contains(pm.group(1)))ports.add(pm.group(1));
        boolean target=false;StringBuilder ds=new StringBuilder();for(Device d:devices){target|=d.ethernet();if(ds.length()>0)ds.append("\n\n");ds.append(d.title()).append("\nUSB 总线速度 ").append(d.speed.isEmpty()?"未知":d.speed+" Mb/s").append("\n驱动 ").append(d.drivers.isEmpty()?"未绑定":d.drivers).append(" · 接口 ").append(d.nets.isEmpty()?"未创建":d.nets).append("\n电源策略 ").append(d.power.isEmpty()?"未知":d.power);}
        int apiStart=raw.indexOf("Android USB API:"); int apiEnd=raw.indexOf("Android Ethernet:"); String apiDevices=apiStart>=0 && apiEnd>apiStart ? raw.substring(apiStart+16,apiEnd).trim() : "";
        deviceText.setText(ds.length()==0?(apiDevices.isEmpty()?"当前未读到已枚举的 USB 外设":"Android USB API 已发现：\n"+apiDevices+"\n\n详细驱动和恢复操作需要 Root sysfs 信息。"):ds.toString());
        boolean apiTarget=apiDevices.contains("0bda:8156") || apiDevices.contains("0bda:8153") || apiDevices.contains("0bda:8152");
        if(raw.contains("ETHERNET_REGISTERED")){netExists=true;carrier=true;}
        int at=raw.indexOf("Android Ethernet:");if(at>=0)net.append('\n').append(raw.substring(at));networkText.setText(net.toString());
        String state,tip;
        if(netExists){state=carrier?"网卡已识别 · 链路在线":"网卡已识别 · 等待网线";tip=carrier?"USB 与网卡驱动已工作。上网仍需检查 IP、路由和系统验证状态。":"已创建网络接口；检查网线、交换机端口和供电。";}
        else if(target || apiTarget){state="已发现网卡 · 尚无网络接口";tip="USB 枚举已完成；检查驱动绑定，或尝试重新授权网卡。";}
        else {state=root?"尚未发现 USB 网卡":"尚未发现网卡 · 需 Root 确认";tip="若网卡已经接好且没有 USB 提示，先采集诊断，再请求 OTG 主机模式。连接电脑时没有网卡属于正常情况。";}
        Matcher role=Pattern.compile("(?:data_role=|dataRole=)(host|device|none)").matcher(raw); String actualRole=role.find()?role.group(1):"unknown"; boolean host=actualRole.equals("host"), dev=actualRole.equals("device");
        if(host)tip+="\n端口角色：HOST 主机";else if(dev)tip+="\n端口角色：DEVICE 从设备";
        if(!root)tip+="\n未授权 Root 时，sysfs 结果可能不完整。";
        headline.setText(state);detail.setText(tip);updateRootLabel();
        String fingerprint=state+"|"+ds+"|"+(host?"host":dev?"device":"unknown");if(!fingerprint.equals(lastFingerprint)){event("状态变化："+state);appendFile(raw+"\n");lastFingerprint=fingerprint;}
    }
    boolean requireRoot(){if(root)return true;new AlertDialog.Builder(this).setTitle("需要 Root").setMessage("请先点“申请 / 检查 Root”。若使用 SukiSU，请在超级用户列表中允许 USB 网卡医生（cn.usbdoctor）。").setPositiveButton("知道了",null).show();return false;}
    void diagnose(){task("采集系统、驱动与 USB 日志…",()->{String s=collectSnapshot();Result r=shell(script("diagnose.sh"),root,25);return s+"\n"+r.text+"\n诊断命令退出码："+r.code;},out->{latest=out;render(out);appendFile("\n完整诊断\n"+out+"\n");event("诊断已保存到本地，可导出 TXT");});}
    void choosePort(java.util.function.Consumer<String> fn){if(ports.isEmpty()){new AlertDialog.Builder(this).setTitle("未读到真实 USB 端口").setMessage("请刷新 Root 状态并采集诊断。此系统未报告可操作端口，工具不会猜测路径。").setPositiveButton("知道了",null).show();return;}if(ports.size()==1){fn.accept(ports.get(0));return;}String[] items=ports.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("选择真实 USB 端口").setItems(items,(d,i)->fn.accept(items[i])).show();}
    void portAction(String action){if(!requireRoot())return;choosePort(port->{String message=action.equals("host")?"请先把电脑 USB 线换成网卡。将请求 "+port+" 切换为供电主机（source / host）。若仍连着电脑，USB ADB 可能断开；需恢复时拔插电脑线。":"将关闭 USB 数据连接约 2 秒再恢复。该端口上的网卡、ADB 及其他 USB 外设会暂时断开；请先停止 USB 存储读写。手机当前 USB HAL 可能不支持此操作，失败会记入日志。";new AlertDialog.Builder(this).setTitle(action.equals("host")?"请求 OTG 主机模式":"重启 USB 数据连接").setMessage(message).setNegativeButton("取消",null).setPositiveButton("执行",(d,w)->recover(action,port,"")).show();});}
    void deviceAction(String action){if(!requireRoot())return;ArrayList<Device> eth=new ArrayList<>();for(Device d:devices)if(d.ethernet())eth.add(d);if(eth.isEmpty()){new AlertDialog.Builder(this).setTitle("没有可操作的网卡").setMessage("请先插入 RTL8156 网卡并刷新。完全没有 USB 枚举时，请使用上方 OTG 恢复和日志采集。").setPositiveButton("知道了",null).show();return;}String[] names=new String[eth.size()];for(int i=0;i<names.length;i++)names[i]=eth.get(i).title();new AlertDialog.Builder(this).setTitle("选择网卡").setItems(names,(dialog,which)->{Device d=eth.get(which);String msg=action.equals("reauth")?"将短暂断开并重新授权此网卡。当前网卡上的网络连接会中断。":action.equals("power")?"仅禁止此网卡本次连接的自动休眠，可能增加耗电。拔插后按系统默认策略运行。":action.equals("auto")?"将此网卡的 power/control 设置为 auto，允许内核自动休眠。":"请求内核重新探测此网卡中尚未绑定驱动的接口。不会安装新内核驱动。";new AlertDialog.Builder(this).setTitle(d.title()).setMessage(msg).setNegativeButton("取消",null).setPositiveButton("执行",(a,b)->recover(action,d.name,d.vid+":"+d.pid)).show();}).show();}
    void recover(String action,String name,String identity){task("执行恢复并记录前后状态…",()->{String before=collectSnapshot();if(!root)return "Root 已失效，未执行恢复。\n"+before;Result r=shell(script("recover.sh",action,name,identity),true,35);String after=collectSnapshot();appendFile("\n恢复前\n"+before+"\n操作 "+action+" "+name+"\n"+r.text+"\n退出码 "+r.code+"\n恢复后\n"+after);return "RESULT_CODE="+r.code+"\n"+r.text+"\n---AFTER---\n"+after;},out->{int i=out.indexOf("---AFTER---\n");String summary=i<0?out:out.substring(0,i);if(i>=0){latest=out.substring(i+12);render(latest);}event(summary);new AlertDialog.Builder(this).setTitle("恢复操作结果").setMessage(summary+"\n请以设备列表和实际联网结果确认是否恢复。").setPositiveButton("知道了",null).show();});}
    void exportReport(){exportText="USB 网卡医生 v1.0\n"+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n";try{File previous=new File(getFilesDir(),"previous-session.txt");if(previous.exists())exportText+=read(previous);exportText+=read(logFile);}catch(Exception e){exportText+=events.toString();}exportText+="\n最新状态\n"+latest;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"USB-LAN-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".txt");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){Toast.makeText(this,"系统没有文件保存器",Toast.LENGTH_LONG).show();}}
    String read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toString("UTF-8");}}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==42 && c==RESULT_OK && data!=null && data.getData()!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new IOException("无法打开文件");o.write(exportText.getBytes(StandardCharsets.UTF_8));event("诊断已导出");}catch(Exception e){event("导出失败："+e);}}}
    void help(){new AlertDialog.Builder(this).setTitle("RTL8156 排查步骤").setMessage("1. 在 SukiSU 中允许 USB 网卡医生（cn.usbdoctor）使用 Root，返回点申请 / 检查 Root。\n\n2. 拔掉电脑线，接上网卡，保持应用在前台。故障发生时先采集完整诊断。\n\n3. 完全没有枚举：请求 OTG 主机模式；仍失败可尝试重启 USB 数据连接。USB HAL 不支持时应保留错误日志。\n\n4. 已发现网卡：禁止本次连接休眠，或重新授权；未绑定时可重新探测驱动。\n\n5. 导出失败时的 TXT，再导出重启手机后正常连接的 TXT，便于对比。\n\nUSB 总线速度与以太网协商速率不同，2.5G 网卡不代表手机能达到 2.5Gbps。工具不能补上内核缺失的驱动，也不能修复线材或供电故障。\n\n只在前台观察，不自动执行恢复；不修改开机项、SELinux 或系统文件，不安装内核模块。").setPositiveButton("知道了",null).show();}
}
