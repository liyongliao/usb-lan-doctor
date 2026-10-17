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
    LinearLayout body; Switch monitor; FrameLayout pages; TextView backendLabel,shizukuLabel;
    final ArrayList<View> pageViews=new ArrayList<>();final ArrayList<NavItem> navButtons=new ArrayList<>();
    ShizukuBridge bridge; int mode=0,activePage=0; LinearLayout feedbackBar; TextView feedbackText; ProgressBar progress; boolean quietTask=false; long feedbackVersion=0;
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
        mode=getPreferences(0).getInt("mode",root?2:0);build();
        bridge=new ShizukuBridge(this,()->{updateRootLabel();if(mode==1&&bridge.permitted()&&!bridge.ready())try{bridge.bind();}catch(Exception e){event(e.toString());}});updateRootLabel();
        IntentFilter f=new IntentFilter();f.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);f.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        if(Build.VERSION.SDK_INT>=33) registerReceiver(receiver,f,Context.RECEIVER_EXPORTED); else registerReceiver(receiver,f);
        event("应用启动 · "+Build.MODEL+" · Android "+Build.VERSION.RELEASE);
        scan(false);
    }
    @Override public void onResume(){super.onResume();foreground=true;ui.removeCallbacks(ticker);ui.postDelayed(ticker,5000);if(bridge!=null&&mode==1&&bridge.permitted()&&!bridge.ready())try{bridge.bind();}catch(Exception e){event(e.toString());}updateRootLabel();}
    @Override public void onPause(){foreground=false;ui.removeCallbacks(ticker);super.onPause();}
    @Override public void onDestroy(){destroyed=true;ui.removeCallbacks(ticker);unregisterReceiver(receiver);if(bridge!=null)bridge.close();worker.shutdown();super.onDestroy();}
    int dp(float n){return (int)(n*getResources().getDisplayMetrics().density+.5f);}
    TextView text(String s,int size,int color){TextView t=new TextView(this);t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setLineSpacing(dp(3),1);return t;}
    GradientDrawable bg(int color,int radius){GradientDrawable g=new GradientDrawable();g.setColor(color);g.setCornerRadius(dp(radius));return g;}
    void gap(LinearLayout p,int n){View v=new View(this);p.addView(v,new LinearLayout.LayoutParams(1,dp(n)));}
    LinearLayout card(String title){ LinearLayout c=new LinearLayout(this);c.setOrientation(1);c.setPadding(dp(18),dp(18),dp(18),dp(18));c.setBackground(bg(CARD,20));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.bottomMargin=dp(14);body.addView(c,lp);TextView h=text(title,13,GREEN);h.setTypeface(null,Typeface.BOLD);c.addView(h);gap(c,12);return c; }
    final class ActionButton extends Button {
        ActionButton(){super(MainActivity.this);setStateListAnimator(null);}
        @Override public void setPressed(boolean pressed){super.setPressed(pressed);animate().scaleX(pressed?.975f:1f).scaleY(pressed?.975f:1f).setDuration(pressed?75:150).start();}
        @Override public void setEnabled(boolean enabled){super.setEnabled(enabled);setAlpha(enabled?1f:.42f);}
    }
    void button(LinearLayout parent,String title,boolean primary,Runnable run){Button b=new ActionButton();b.setText(title);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(primary?BG:INK);b.setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(primary?0x440b1420:0x446fe3c4),bg(primary?GREEN:0xff24384b,12),bg(0xffffffff,12)));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(49));lp.topMargin=dp(9);parent.addView(b,lp);b.setOnClickListener(v->{v.performHapticFeedback(HapticFeedbackConstants.VIRTUAL_KEY);run.run();});controls.add(b);}
 /** Compact icon-and-label navigation with a selected icon capsule and native ripple. */
 final class NavItem extends LinearLayout {
  final ImageView icon; final TextView label; final FrameLayout indicator;
  NavItem(String name,int resource){super(MainActivity.this);setOrientation(VERTICAL);setGravity(Gravity.CENTER);setClickable(true);setFocusable(true);setContentDescription(name);setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_YES);
   setBackground(new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x226fe3c4),null,bg(0xffffffff,20)));
   indicator=new FrameLayout(MainActivity.this);addView(indicator,new LinearLayout.LayoutParams(dp(54),dp(30)));
   icon=new ImageView(MainActivity.this);icon.setImageResource(resource);icon.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);FrameLayout.LayoutParams iconParams=new FrameLayout.LayoutParams(dp(22),dp(22),Gravity.CENTER);indicator.addView(icon,iconParams);
   label=text(name,11,MUTED);label.setIncludeFontPadding(false);label.setGravity(Gravity.CENTER);label.setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);LinearLayout.LayoutParams textParams=new LinearLayout.LayoutParams(-1,-2);textParams.topMargin=dp(5);addView(label,textParams);
  }
  @Override public void setPressed(boolean pressed){super.setPressed(pressed);if(icon!=null)icon.animate().scaleX(pressed?.85f:1f).scaleY(pressed?.85f:1f).setDuration(110).start();}
  void activate(boolean selected){setSelected(selected);indicator.setBackground(bg(selected?0xff28483f:0x00000000,15));icon.setColorFilter(selected?GREEN:MUTED);label.setTextColor(selected?GREEN:MUTED);label.setTypeface(null,selected?Typeface.BOLD:Typeface.NORMAL);}
  @Override public void onInitializeAccessibilityNodeInfo(android.view.accessibility.AccessibilityNodeInfo info){super.onInitializeAccessibilityNodeInfo(info);info.setClassName("android.widget.RadioButton");info.setCheckable(true);info.setChecked(isSelected());}
 }
    void page(String title,String subtitle){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(true);body=new LinearLayout(this);body.setOrientation(1);body.setPadding(dp(20),dp(18),dp(20),dp(24));scroll.addView(body);pages.addView(scroll,new FrameLayout.LayoutParams(-1,-1));pageViews.add(scroll);TextView h=text(title,28,INK);h.setTypeface(null,Typeface.BOLD);body.addView(h);gap(body,5);body.addView(text(subtitle,13,MUTED));gap(body,20);}
    void showPage(int index){activePage=index;for(int i=0;i<pageViews.size();i++){pageViews.get(i).setVisibility(i==index?View.VISIBLE:View.GONE);navButtons.get(i).activate(i==index);}}
    void build(){
        LinearLayout shell=new LinearLayout(this);shell.setOrientation(1);shell.setBackgroundColor(BG);setContentView(shell);shell.setOnApplyWindowInsetsListener((v,i)->{v.setPadding(i.getSystemWindowInsetLeft(),i.getSystemWindowInsetTop(),i.getSystemWindowInsetRight(),i.getSystemWindowInsetBottom());return i;});
        LinearLayout bar=new LinearLayout(this);bar.setPadding(dp(22),dp(10),dp(22),dp(6));bar.setGravity(Gravity.CENTER_VERTICAL);shell.addView(bar);TextView brand=text("USB网卡医生",14,GREEN);brand.setTypeface(null,Typeface.BOLD);bar.addView(brand,new LinearLayout.LayoutParams(0,-2,1));backendLabel=text("普通诊断",12,MUTED);bar.addView(backendLabel);
        feedbackBar=new LinearLayout(this);feedbackBar.setGravity(Gravity.CENTER_VERTICAL);feedbackBar.setPadding(dp(14),dp(10),dp(14),dp(10));feedbackBar.setBackground(bg(0xff19362f,14));LinearLayout.LayoutParams fp=new LinearLayout.LayoutParams(-1,-2);fp.setMargins(dp(20),dp(6),dp(20),dp(4));shell.addView(feedbackBar,fp);progress=new ProgressBar(this);progress.setIndeterminateTintList(android.content.res.ColorStateList.valueOf(GREEN));feedbackBar.addView(progress,new LinearLayout.LayoutParams(dp(20),dp(20)));feedbackText=text("",13,GREEN);feedbackText.setMaxLines(3);feedbackText.setEllipsize(android.text.TextUtils.TruncateAt.END);feedbackText.setPadding(dp(10),0,0,0);feedbackText.setAccessibilityLiveRegion(View.ACCESSIBILITY_LIVE_REGION_POLITE);feedbackBar.addView(feedbackText,new LinearLayout.LayoutParams(0,-2,1));feedbackBar.setVisibility(View.GONE);
        pages=new FrameLayout(this);shell.addView(pages,new LinearLayout.LayoutParams(-1,0,1));
        page("连接状态","从 USB 枚举到以太网连接，逐层检查");LinearLayout status=card("实时概览");headline=text("正在读取设备…",23,INK);headline.setTypeface(null,Typeface.BOLD);status.addView(headline);gap(status,8);detail=text("",14,MUTED);status.addView(detail);busyText=text("",12,GREEN);status.addView(busyText);button(status,"刷新状态",true,()->scan(true));monitor=new Switch(this);monitor.setText("前台每 5 秒观察插拔");monitor.setTextSize(13);monitor.setTextColor(MUTED);monitor.setChecked(true);status.addView(monitor);
        LinearLayout device=card("USB 与网络");deviceText=text("等待扫描",14,INK);deviceText.setTextIsSelectable(true);device.addView(deviceText);gap(device,14);networkText=text("",13,MUTED);networkText.setTextIsSelectable(true);device.addView(networkText);
        page("诊断与恢复","按当前权限提供可执行的恢复路径");LinearLayout basic=card("所有手机都可以做");basic.addView(text("先记录故障，再检查 OTG 转接头、供电和网线。USB 访问授权不能让未枚举的网卡凭空出现。",13,MUTED));button(basic,"采集诊断",true,()->diagnose());button(basic,"打开系统网络设置",false,()->openSettings(android.provider.Settings.ACTION_WIRELESS_SETTINGS));
        LinearLayout linkRepair=card("网卡已识别但未联网 · Root");linkRepair.addView(text("先采集故障诊断。接口未启用时先启用；已启用但无链路时可尝试重启接口，再核对 IP 与系统网络状态。",13,MUTED));button(linkRepair,"启用网卡网络接口",false,()->deviceAction("linkup"));button(linkRepair,"重启网卡网络接口",false,()->deviceAction("linkreset"));
        LinearLayout repair=card("端口恢复 · Shizuku / Root");repair.addView(text("通过系统 USB 服务请求恢复。厂商权限和 USB HAL 决定是否支持，执行后需核对真实状态。",13,MUTED));button(repair,"请求 OTG 主机模式",false,()->portAction("host"));button(repair,"重启 USB 数据连接",false,()->portAction("cycle"));
        LinearLayout driver=card("内核操作 · 需要 Root");driver.addView(text("只对已确认的 USB 网卡操作。Shizuku 通过无线 ADB 启动时不具备这些权限。",13,MUTED));button(driver,"禁止网卡休眠（本次连接）",false,()->deviceAction("power"));button(driver,"重新授权网卡 / 重新识别",false,()->deviceAction("reauth"));button(driver,"重新探测未绑定的驱动",false,()->deviceAction("probe"));button(driver,"恢复网卡自动电源管理",false,()->deviceAction("auto"));
        page("权限与连接","无需 Root 也能开始排查");LinearLayout current=card("当前执行方式");rootLabel=text("",14,INK);current.addView(rootLabel);button(current,"使用普通诊断",false,()->selectMode(0));
        LinearLayout sh=card("推荐 · Shizuku 无线调试");shizukuLabel=text("",13,INK);sh.addView(shizukuLabel);gap(sh,10);sh.addView(text("Android 11+ 可在 Shizuku 中用无线调试配对并启动；授权本应用后即可读取更多诊断，尝试端口恢复。重启手机后通常需重新启动服务。",13,MUTED));button(sh,"连接 / 授权 Shizuku",true,()->connectShizuku());button(sh,"打开 Shizuku",false,()->openShizuku());button(sh,"打开开发者选项",false,()->openSettings(android.provider.Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS));
        LinearLayout rt=card("Root · 完整内核操作");rt.addView(text("已有 Root 的手机可以在管理器允许本应用，再检查授权。普通手机无需为基础诊断解锁或刷机。",13,MUTED));button(rt,"使用 Root / 检查授权",false,()->{mode=2;saveMode();checkRoot();});
        LinearLayout limits=card("权限能做什么");limits.addView(text("普通：USB 枚举、以太网连接、IP / DNS、网络验证结果。\n\nShizuku / ADB：系统 USB / 以太网日志、端口角色、系统允许的端口恢复。\n\nRoot：再增加网卡接口启用 / 重启、sysfs 写入、网卡重新授权、驱动重探测、休眠策略。\n\nShizuku 不能补齐内核驱动，也不能解决线材和供电故障。",13,MUTED));button(limits,"复制电脑 ADB 诊断命令",false,()->{((ClipboardManager)getSystemService(CLIPBOARD_SERVICE)).setPrimaryClip(ClipData.newPlainText("ADB 诊断","adb shell dumpsys usb\nadb shell dumpsys ethernet\nadb shell ip address"));Toast.makeText(this,"已复制，只读诊断命令",0).show();});
        page("诊断记录","记录故障前后状态，便于比较");LinearLayout log=card("本地日志");log.addView(text("导出含设备、IP 和 USB 信息，分享前请检查内容。",12,MUTED));button(log,"导出诊断 TXT",true,()->exportReport());button(log,"使用说明",false,()->help());gap(log,12);logText=text("",12,MUTED);logText.setTypeface(Typeface.MONOSPACE);logText.setTextIsSelectable(true);log.addView(logText);body.addView(text("v1.2.0 · 本地离线 · 无联网权限",11,MUTED));
        LinearLayout nav=new LinearLayout(this);nav.setGravity(Gravity.CENTER_VERTICAL);nav.setPadding(dp(6),dp(7),dp(6),dp(7));GradientDrawable dock=bg(0xff142231,26);dock.setStroke(dp(1),0xff243444);nav.setBackground(dock);nav.setElevation(dp(6));LinearLayout.LayoutParams dockParams=new LinearLayout.LayoutParams(-1,dp(76));dockParams.setMargins(dp(18),dp(6),dp(18),dp(10));shell.addView(nav,dockParams);String[] labels={"状态","恢复","权限","记录"};int[] icons={R.drawable.nav_status,R.drawable.nav_edit,R.drawable.nav_module,R.drawable.nav_log};for(int i=0;i<labels.length;i++){final int index=i;NavItem item=new NavItem(labels[i],icons[i]);nav.addView(item,new LinearLayout.LayoutParams(0,-1,1));navButtons.add(item);item.setOnClickListener(v->showPage(index));}showPage(0);updateRootLabel();
    }
    boolean elevated(){return mode==2&&root||mode==1&&bridge!=null&&bridge.ready();}
    boolean rootAccess(){return mode==2&&root||mode==1&&bridge!=null&&bridge.ready()&&bridge.uid()==0;}
    String modeName(){return mode==2?(root?"Root":"Root 未授权"):mode==1?(bridge!=null&&bridge.ready()?"Shizuku · "+(bridge.uid()==0?"Root":"ADB"):"Shizuku 未连接"):"普通诊断";}
    void updateRootLabel(){if(rootLabel==null)return;rootLabel.setText("当前方式："+modeName());rootLabel.setTextColor(elevated()?GREEN:MUTED);backendLabel.setText(modeName());if(shizukuLabel!=null)shizukuLabel.setText(bridge==null||!bridge.available()?"Shizuku 尚未启动":bridge.ready()?"服务已连接 · UID "+bridge.uid():bridge.permitted()?(bridge.binding?"已授权 · 正在连接服务":"已授权 · 点击连接服务"):"Shizuku 已启动 · 等待本应用授权");}
    void saveMode(){getPreferences(0).edit().putInt("mode",mode).apply();updateRootLabel();}
    void selectMode(int selected){if(busy)return;mode=selected;saveMode();event("已切换为 "+modeName());feedback("已切换为 "+modeName(),false);scan(false);}
    void connectShizuku(){if(busy)return;mode=1;saveMode();try{bridge.connect();feedback(bridge.ready()?"Shizuku 已连接":"正在连接 Shizuku；如弹出授权框，请点允许",false);}catch(Exception e){message("Shizuku 连接",e.getMessage());}updateRootLabel();}
    void openSettings(String action){try{startActivity(new Intent(action));}catch(Exception e){message("无法打开设置","请手动打开系统设置中的对应页面。");}}
    void openShizuku(){Intent intent=getPackageManager().getLaunchIntentForPackage("moe.shizuku.privileged.api");if(intent!=null)startActivity(intent);else{try{startActivity(new Intent(Intent.ACTION_VIEW,Uri.parse("https://shizuku.rikka.app/download/")));}catch(Exception e){message("安装 Shizuku","请访问 shizuku.rikka.app/download/ 安装。");}}}
    void message(String title,String content){if(!title.equals("使用说明"))feedback(title+" · "+content,true);new AlertDialog.Builder(this).setTitle(title).setMessage(content).setPositiveButton("知道了",null).show();}
    void feedback(String text,boolean error){long version=++feedbackVersion;feedbackBar.setVisibility(View.VISIBLE);progress.setVisibility(busy?View.VISIBLE:View.GONE);feedbackText.setText(text);feedbackText.setTextColor(error?AMBER:GREEN);feedbackBar.setBackground(bg(error?0xff3c3020:0xff19362f,14));if(!busy)ui.postDelayed(()->{if(!busy&&version==feedbackVersion)feedbackBar.setVisibility(View.GONE);},10000);}
    void setBusy(boolean b,String label){busy=b;for(Button v:controls)v.setEnabled(!b);busyText.setText(b?label:"");if(b&&!quietTask)feedback(label,false);if(b&&quietTask)backendLabel.setText(modeName()+" · 刷新中");if(!b){progress.setVisibility(View.GONE);updateRootLabel();}}
    void task(String label,Job job,java.util.function.Consumer<String> done){task(label,job,done,false);}
    void task(String label,Job job,java.util.function.Consumer<String> done,boolean quiet){
        if(busy){if(!quiet)feedback("正在处理上一项操作，请稍候…",false);return;}quietTask=quiet;setBusy(true,label);
        worker.execute(()->{String result;try{result=job.run();}catch(Exception e){result="执行失败："+e.toString();}final String out=result;ui.post(()->{if(destroyed)return;setBusy(false,"");if(out.startsWith("执行失败：")){event(out);if(!quiet)message("操作未完成",out);return;}done.accept(out);});});
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
        },out->{root=out.equals("ROOT_OK");getPreferences(0).edit().putBoolean("root",root).putString("su",su).apply();updateRootLabel();feedback(root?"Root 授权验证通过":"未取得 Root 授权",!root);event(root?"Root 授权验证通过":out);if(!root)new AlertDialog.Builder(this).setTitle("尚未取得 Root").setMessage(out).setPositiveButton("知道了",null).show();scan(false);});
    }
    String androidSnapshot(){
        StringBuilder b=new StringBuilder("\nAndroid USB API:\n");
        UsbManager m=(UsbManager)getSystemService(USB_SERVICE);
        try{for(UsbDevice d:m.getDeviceList().values())b.append(String.format(Locale.ROOT,"%04x:%04x %s %s\n",d.getVendorId(),d.getProductId(),d.getDeviceName(),d.getProductName()));}catch(Exception e){b.append(e).append('\n');}
        b.append("\nAndroid Ethernet:\n");
        ConnectivityManager cm=(ConnectivityManager)getSystemService(CONNECTIVITY_SERVICE);boolean has=false;
        for(Network n:cm.getAllNetworks()){
            NetworkCapabilities c=cm.getNetworkCapabilities(n);
            if(c!=null && c.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)){
                has=true;LinkProperties lp=cm.getLinkProperties(n);
                b.append("ETHERNET_REGISTERED ").append(lp==null?"接口信息暂不可读":lp.getInterfaceName()).append('\n');
                if(lp!=null){b.append("IP 地址：").append(lp.getLinkAddresses().isEmpty()?"暂无":lp.getLinkAddresses()).append('\n');b.append("DNS：").append(lp.getDnsServers()).append('\n');b.append("路由：").append(lp.getRoutes()).append('\n');}
                boolean validated=c.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED);
                b.append("互联网验证：").append(validated?"通过（系统结果）":"未通过或尚未完成，不代表网线未插").append('\n');
                b.append("INTERNET_VALIDATED=").append(validated).append('\n');
            }
        }
        if(!has)b.append("系统尚未注册以太网网络；这不等于 USB 未识别或网线未插。\n");
        try{Enumeration<NetworkInterface> es=NetworkInterface.getNetworkInterfaces();while(es!=null && es.hasMoreElements()){NetworkInterface ni=es.nextElement();String name=ni.getName();if(name.matches("(eth|en|usb).*"))b.append("地址 ").append(name).append(" ").append(Collections.list(ni.getInetAddresses())).append('\n');}}catch(Exception e){b.append("地址读取受限\n");}
        return b.toString();
    }
    Result runScript(String name,int seconds,String... args)throws Exception{
        if(mode==1){if(bridge==null||!bridge.ready())throw new IOException("Shizuku 未连接，请在权限栏目连接");String op=name.equals("snapshot.sh")?"snapshot":name.equals("diagnose.sh")?"diagnose":"recover";return bridge.run(op,args.length>0?args[0]:"",args.length>1?args[1]:"",args.length>2?args[2]:"");}
        return shell(script(name,args),mode==2&&root,seconds);
    }
    String collectSnapshot()throws Exception {
        String data;
        try{Result r=runScript("snapshot.sh",15);data=r.text;
            if(mode==2&&root&&(r.code!=0||!r.text.contains("ROOT|yes"))){root=false;getPreferences(0).edit().putBoolean("root",false).apply();data="LIMIT|Root 已失效，当前仅提供普通诊断\n"+shell(script("snapshot.sh"),false,10).text;}
            if(r.code!=0)data="LIMIT|诊断命令退出码 "+r.code+"\n"+data;
        }catch(Exception e){data="LIMIT|高级诊断不可用："+e.getMessage()+"\n";}
        return "BACKEND|"+modeName()+"\n"+data+androidSnapshot();
    }
    void scan(boolean explicit){task("读取 USB、角色与网卡状态…",()->collectSnapshot(),out->{latest=out;render(out);if(explicit){event("手动刷新完成");feedback("已刷新 · "+headline.getText(),out.contains("LIMIT|"));}},!explicit);}
    void render(String raw){
        devices.clear();ports.clear();StringBuilder net=new StringBuilder();ArrayList<LinkState> links=new ArrayList<>();
        for(String line:raw.split("\n")){String[] p=line.split("\\|",-1);if(p[0].equals("DEV") && p.length>=9)devices.add(new Device(p));if(p[0].equals("NET") && p.length>=6){LinkState link=new LinkState(p);links.add(link);net.append(link.detail()).append('\n');}}
        Matcher pm=Pattern.compile("(?m)(?:^\\s*id=|UsbPort\\{id=)([A-Za-z0-9_.:-]+)").matcher(raw);while(pm.find())if(!ports.contains(pm.group(1)))ports.add(pm.group(1));
        boolean target=false;StringBuilder ds=new StringBuilder();for(Device d:devices){target|=d.ethernet();if(ds.length()>0)ds.append("\n\n");ds.append(d.title()).append("\nUSB 总线速度 ").append(d.speed.isEmpty()?"未知":d.speed+" Mb/s").append("\n驱动 ").append(d.drivers.isEmpty()?"未绑定":d.drivers).append(" · 接口 ").append(d.nets.isEmpty()?"未创建":d.nets).append("\n电源策略 ").append(d.power.isEmpty()?"未知":d.power);}
        int apiStart=raw.indexOf("Android USB API:"); int apiEnd=raw.indexOf("Android Ethernet:"); String apiDevices=apiStart>=0 && apiEnd>apiStart ? raw.substring(apiStart+16,apiEnd).trim() : "";
        deviceText.setText(ds.length()==0?(apiDevices.isEmpty()?"当前未读到已枚举的 USB 外设":"Android USB API 已发现：\n"+apiDevices+"\n\n驱动细节可能需要 Shizuku 或 Root 权限。"):ds.toString());
        boolean apiTarget=apiDevices.contains("0bda:8156") || apiDevices.contains("0bda:8153") || apiDevices.contains("0bda:8152");
        int at=raw.indexOf("Android Ethernet:");if(at>=0)net.append('\n').append(raw.substring(at));networkText.setText(net.toString());
        String state,tip;
        boolean knownInterface=false;for(Device d:devices)if(d.ethernet()&&!d.nets.isEmpty())knownInterface=true;
        if(!links.isEmpty()){
            LinkState first=links.get(0);boolean mixed=false;for(LinkState link:links)mixed|=link.status!=first.status;
            state=mixed?"多个接口 · 状态不一致":"网卡已识别 · "+first.label();
            tip=mixed?"请逐个核对下方接口状态；一个接口在线不代表所有网卡都正常。":first.hint();
        }
        else if(knownInterface){state="网卡已识别 · 链路状态未知";tip="已发现网卡网络接口，但无法读取链路状态。不能判断网线连接情况，请采集诊断。";}
        else if(target || apiTarget){state="已发现 USB 网卡 · 接口待确认";tip="USB 枚举已完成，暂未读到对应网络接口；可能尚未创建，也可能受读取权限限制。请用 Shizuku / Root 采集诊断。";}
        else {state="当前未发现 USB 网卡";tip="若网卡已经接好且没有 USB 提示，先采集诊断，再请求 OTG 主机模式。连接电脑时没有网卡属于正常情况。";}
        Matcher role=Pattern.compile("(?:data_role=|dataRole=)(host|device|none)").matcher(raw); String actualRole=role.find()?role.group(1):"unknown"; boolean host=actualRole.equals("host"), dev=actualRole.equals("device");
        if(host)tip+="\n端口角色：HOST 主机";else if(dev)tip+="\n端口角色：DEVICE 从设备";
        if(raw.contains("ETHERNET_REGISTERED"))tip+="\n系统已注册以太网网络，详情见下方；这不替代 USB 网卡的链路检测。";
        if(!elevated())tip+="\n普通诊断可查看系统已枚举设备；更多日志可使用 Shizuku。";
        headline.setText(state);detail.setText(tip);updateRootLabel();
        String fingerprint=state+"|"+ds+"|"+net+"|"+(host?"host":dev?"device":"unknown");if(!fingerprint.equals(lastFingerprint)){event("状态变化："+state);appendFile(raw+"\n");lastFingerprint=fingerprint;}
    }
    boolean requireRoot(){if(rootAccess())return true;message("此操作需要 Root","网卡接口启用 / 重启、重新授权、驱动重探测和休眠策略需要 Root 权限。Shizuku 通过 ADB 启动无法提供此权限。可先使用普通诊断或端口恢复。");return false;}
    boolean requireElevated(){if(elevated())return true;message("连接 Shizuku 或 Root","在权限栏目连接 Shizuku（可通过无线调试启动），或使用已有 Root。普通权限无法请求系统端口切换。");showPage(2);return false;}
    void diagnose(){task("采集 USB 与以太网诊断…",()->{String snapshot=collectSnapshot();if(!elevated())return snapshot+"\n普通诊断完成。系统服务日志和内核日志需额外权限；可连接 Shizuku 后重试。";Result r=runScript("diagnose.sh",25);return snapshot+"\n"+r.text+"\n诊断命令退出码："+r.code;},out->{latest=out;render(out);appendFile("\n诊断\n"+out+"\n");event("诊断已保存到本地，可导出 TXT");feedback("诊断已保存，可在“记录”中导出 TXT",false);});}
    void choosePort(java.util.function.Consumer<String> fn){if(ports.isEmpty()){new AlertDialog.Builder(this).setTitle("未读到真实 USB 端口").setMessage("请连接 Shizuku 或 Root 后刷新并采集诊断。此系统未报告可操作端口，工具不会猜测路径。").setPositiveButton("知道了",null).show();return;}if(ports.size()==1){fn.accept(ports.get(0));return;}String[] items=ports.toArray(new String[0]);new AlertDialog.Builder(this).setTitle("选择真实 USB 端口").setItems(items,(d,i)->fn.accept(items[i])).show();}
    void portAction(String action){if(!requireElevated())return;choosePort(port->{String message=action.equals("host")?"请先把电脑 USB 线换成网卡。将请求 "+port+" 切换为供电主机（source / host）。若仍连着电脑，USB ADB 可能断开；需恢复时拔插电脑线。":"将关闭 USB 数据连接约 2 秒再恢复。该端口上的网卡、ADB 及其他 USB 外设会暂时断开；请先停止 USB 存储读写。手机当前 USB HAL 可能不支持此操作，失败会记入日志。";new AlertDialog.Builder(this).setTitle(action.equals("host")?"请求 OTG 主机模式":"重启 USB 数据连接").setMessage(message).setNegativeButton("取消",null).setPositiveButton("执行",(d,w)->recover(action,port,"")).show();});}
    void deviceAction(String action){if(!requireRoot())return;ArrayList<Device> eth=new ArrayList<>();for(Device d:devices)if(d.ethernet())eth.add(d);if(eth.isEmpty()){new AlertDialog.Builder(this).setTitle("没有可操作的网卡").setMessage("请先插入 RTL8156 网卡并刷新。完全没有 USB 枚举时，请使用上方 OTG 恢复和日志采集。").setPositiveButton("知道了",null).show();return;}String[] names=new String[eth.size()];for(int i=0;i<names.length;i++)names[i]=eth.get(i).title();new AlertDialog.Builder(this).setTitle("选择网卡").setItems(names,(dialog,which)->{Device d=eth.get(which);String msg=action.equals("linkup")?"仅将此 USB 网卡的网络接口设为 UP。不会设置 IP、MAC 或速率；系统是否接管网络需以刷新结果为准。":action.equals("linkreset")?"将此 USB 网卡的网络接口先关闭再启用，当前网卡连接会短暂中断。不会重启手机，也不会修改 Wi-Fi。执行后请等待协商并检查 IP；如设备含多个网络接口，将拒绝操作并记录原因。":action.equals("reauth")?"将短暂断开并重新授权此网卡。当前网卡上的网络连接会中断。":action.equals("power")?"仅禁止此网卡本次连接的自动休眠，可能增加耗电。拔插后按系统默认策略运行。":action.equals("auto")?"将此网卡的 power/control 设置为 auto，允许内核自动休眠。":"请求内核重新探测此网卡中尚未绑定驱动的接口。不会安装新内核驱动。";new AlertDialog.Builder(this).setTitle(d.title()).setMessage(msg).setNegativeButton("取消",null).setPositiveButton("执行",(a,b)->recover(action,d.name,d.vid+":"+d.pid)).show();}).show();}
    void recover(String action,String name,String identity){task("执行恢复并记录前后状态…",()->{String before=collectSnapshot();if(!elevated())return "权限已失效，未执行恢复。\n"+before;Result r=runScript("recover.sh",35,action,name,identity);String after;try{after=collectSnapshot();}catch(Exception e){after="状态刷新失败："+e;}appendFile("\n恢复前\n"+before+"\n操作 "+action+" "+name+"\n"+r.text+"\n退出码 "+r.code+"\n恢复后\n"+after);return "RESULT_CODE="+r.code+"\n"+r.text+"\n---AFTER---\n"+after;},out->{int i=out.indexOf("---AFTER---\n");String summary=i<0?out:out.substring(0,i);if(i>=0){latest=out.substring(i+12);render(latest);}event(summary);feedback(summary.startsWith("RESULT_CODE=0")?"请求已执行，正在展示实际设备状态；请核对是否恢复":"恢复未完成，查看结果中的原因",!summary.startsWith("RESULT_CODE=0"));new AlertDialog.Builder(this).setTitle(summary.startsWith("RESULT_CODE=0")?"恢复请求已执行 · 请核对状态":"恢复未完成").setMessage(summary+"\n请以设备列表和实际联网结果确认是否恢复。").setPositiveButton("知道了",null).show();});}
    void exportReport(){exportText="USB 网卡医生 v1.2.0\n"+Build.MODEL+" / Android "+Build.VERSION.RELEASE+"\n";try{File previous=new File(getFilesDir(),"previous-session.txt");if(previous.exists())exportText+=read(previous);exportText+=read(logFile);}catch(Exception e){exportText+=events.toString();}exportText+="\n最新状态\n"+latest;Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT);i.addCategory(Intent.CATEGORY_OPENABLE);i.setType("text/plain");i.putExtra(Intent.EXTRA_TITLE,"USB-LAN-"+new SimpleDateFormat("yyyyMMdd-HHmmss",Locale.ROOT).format(new Date())+".txt");try{startActivityForResult(i,42);}catch(ActivityNotFoundException e){Toast.makeText(this,"系统没有文件保存器",Toast.LENGTH_LONG).show();}}
    String read(File f)throws IOException{try(InputStream in=new FileInputStream(f);ByteArrayOutputStream o=new ByteArrayOutputStream()){byte[] b=new byte[8192];int n;while((n=in.read(b))!=-1)o.write(b,0,n);return o.toString("UTF-8");}}
    @Override protected void onActivityResult(int r,int c,Intent data){super.onActivityResult(r,c,data);if(r==42 && c==RESULT_OK && data!=null && data.getData()!=null){try(OutputStream o=getContentResolver().openOutputStream(data.getData())){if(o==null)throw new IOException("无法打开文件");o.write(exportText.getBytes(StandardCharsets.UTF_8));event("诊断已导出");}catch(Exception e){event("导出失败："+e);}}}
    void help(){message("使用说明","1. 无 Root 也可查看 USB 设备、以太网和 IP 状态；先采集故障诊断。\n\n2. 推荐使用 Shizuku：Android 11+ 在 Shizuku 内通过无线调试配对、启动，返回本应用的权限栏目授权连接。Android 8–10 可用电脑 ADB 启动 Shizuku。\n\n3. Shizuku / ADB 可以读取更多系统日志，并尝试 OTG 主机模式和 USB 数据恢复；厂商限制、HAL 能力不足会导致失败。无线调试只是 ADB 权限，不是 Root。\n\n4. 网卡已识别但无网络时，先采集诊断；接口启用 / 重启、重新授权、驱动探测和休眠策略修改仍需要 Root。无法靠应用补上内核缺失的驱动。\n\n5. USB 设备访问授权不等于网卡驱动授权。本应用不会抢占网卡接口，也不会自动改动端口。\n\n6. USB 总线速度与以太网协商速率不同。请检查供电、OTG 转接头、网线和手机系统中的 OTG 开关。恢复请求后以真实枚举和联网结果为准。\n\nShizuku API 13.1.5 · MIT License，来源 github.com/RikkaApps/Shizuku-API，许可随 APK 提供。");}
}
