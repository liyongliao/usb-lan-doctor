package cn.usbdoctor;
import android.app.*;import android.os.*;import android.content.*;import java.io.*;import java.nio.charset.StandardCharsets;import android.graphics.Bitmap;

public class NoRootTest extends Instrumentation {
 MainActivity a;StringBuilder report=new StringBuilder();
 public void onCreate(Bundle b){super.onCreate(b);start();}
 void check(boolean ok,String text){if(!ok)throw new AssertionError(text);}
 void stage(String text){report.append(text).append('\n');Bundle b=new Bundle();b.putString("stream",text+"\n");sendStatus(1,b);}
 void idle()throws Exception{for(int i=0;i<100;i++){final boolean[] b={true};runOnMainSync(()->b[0]=a.busy);if(!b[0])return;Thread.sleep(300);}throw new AssertionError("task timeout");}
 public void onStart(){Bundle result=new Bundle();int status=0;try{
  ActivityMonitor m=addMonitor("cn.usbdoctor.MainActivity",null,false);getTargetContext().startActivity(new Intent().setClassName("cn.usbdoctor","cn.usbdoctor.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));a=(MainActivity)waitForMonitorWithTimeout(m,15000);check(a!=null,"launch");Thread.sleep(500);idle();
  runOnMainSync(()->{a.monitor.setChecked(false);a.selectMode(0);});idle();
  check(!a.elevated()&&!a.rootAccess(),"basic mode must ignore retained Root grant");check(a.latest.contains("Android USB API:")&&a.latest.contains("Android Ethernet:"),"ordinary Android diagnostics missing");check(!a.latest.contains("ROOT|yes"),"basic mode unexpectedly used Root");stage("PASS: ordinary app mode diagnostics, no Root used");
  MainActivity.Result denied=a.shell(a.script("recover.sh","host","port0",""),false,10);check(denied.code==10,"ordinary shell cannot write");
  runOnMainSync(()->{check(a.pageViews.size()==4&&a.navButtons.size()==4,"navigation count");for(int i=0;i<4;i++){a.navButtons.get(i).performClick();for(int j=0;j<4;j++)check(a.pageViews.get(j).getVisibility()==(i==j?android.view.View.VISIBLE:android.view.View.GONE),"page exclusivity");}a.showPage(2);a.connectShizuku();});
  stage("Waiting for Shizuku authorization and service binding");boolean ready=false;for(int i=0;i<240;i++){if(a.bridge.ready()){ready=true;break;}Thread.sleep(500);}check(ready,"Shizuku service not authorized/connected in 120s");
  MainActivity.Result uid=a.bridge.run("identity","","","");stage(uid.text);check(uid.code==0&&uid.text.equals("UID|2000"),"this regression requires ADB-mode Shizuku UID 2000");check(!a.rootAccess(),"ADB must not enable root-only actions");
  String actual=a.collectSnapshot();check(actual.contains("UID|2000"),"snapshot was not executed as ADB");check(actual.contains("USB MANAGER STATE")||actual.contains("port_manager"),"dumpsys USB missing");
  MainActivity.Result guard=a.bridge.run("recover","power","1-1","0bda:8156");check(guard.code==10,"kernel mutation must be rejected for shell");
  MainActivity.Result diagnostics=a.bridge.run("diagnose","","","");check(diagnostics.code==0&&diagnostics.text.contains("========== USB =========="),"extended diagnostics");
  MainActivity.Result unknown=a.bridge.run("arbitrary-command","id","","");check(unknown.code==125,"unknown service operations must be rejected");
  runOnMainSync(()->{a.latest=actual;a.render(actual);a.event("无 Root 实机测试通过：普通诊断 / Shizuku UID 2000 / 内核写入保护 / 四栏导航");});
  stage("PASS: Shizuku user service UID 2000, actual USB service snapshot, diagnostics, root-only guard, operation allowlist, four-page navigation");
  for(int i=0;i<4;i++){final int page=i;runOnMainSync(()->a.showPage(page));Thread.sleep(500);Bitmap bmp=getUiAutomation().takeScreenshot();if(bmp!=null){try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"v1.1-page-"+i+".png"))){bmp.compress(Bitmap.CompressFormat.PNG,100,out);}bmp.recycle();}}
  runOnMainSync(()->{a.showPage(0);a.monitor.setChecked(true);});status=-1;
 }catch(Throwable t){stage("FAIL: "+t);}finally{try(FileOutputStream out=new FileOutputStream(new File(getTargetContext().getExternalFilesDir(null),"noroot-test.txt"))){out.write(report.toString().getBytes(StandardCharsets.UTF_8));}catch(Exception ignored){}}
 result.putString("stream",report.toString());finish(status,result);
 }
}
