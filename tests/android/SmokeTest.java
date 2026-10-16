package cn.usbdoctor;
import android.app.*;
import android.os.*;
import android.content.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class SmokeTest extends Instrumentation {
    public void onCreate(Bundle args){super.onCreate(args);start();}
    MainActivity activity;
    void check(boolean b,String s){if(!b)throw new AssertionError(s);}
    boolean busy(){final boolean[] b={true};runOnMainSync(()->b[0]=activity.busy);return b[0];}
    void awaitIdle()throws Exception{for(int i=0;i<120 && busy();i++)Thread.sleep(500);check(!busy(),"operation timed out");}
    @Override public void onStart(){Bundle result=new Bundle();try{
        Intent i=new Intent(Intent.ACTION_MAIN).setClassName("cn.usbdoctor","cn.usbdoctor.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        ActivityMonitor monitor=addMonitor("cn.usbdoctor.MainActivity",null,false);getTargetContext().startActivity(i);activity=(MainActivity)waitForMonitorWithTimeout(monitor,15000);check(activity!=null,"launch");Thread.sleep(500);awaitIdle();runOnMainSync(()->{activity.monitor.setChecked(false);activity.mode=2;});
        check(activity.root,"Root authorization missing");
        MainActivity.Result uid=activity.shell("id -u",true,10);check(uid.code==0 && uid.text.trim().equals("0"),"Root uid != 0");
        String real=activity.collectSnapshot();check(real.contains("ROOT|yes"),"root snapshot missing");
        runOnMainSync(()->{
            activity.render("ROOT|yes\nport_manager={\n id=port0\n data_role=device\n role_combinations=[\n data_role=host\n]\n}");
            check(activity.detail.getText().toString().contains("DEVICE"),"possible roles confused with active role");
            check(activity.ports.size()==1 && activity.ports.get(0).equals("port0"),"port parser");
            activity.render("DEV|1-1|0bda|8156|RTL8156|480|r8152|eth0|on\nNET|eth0|up|1|2500|full\nROOT|yes");
            check(activity.headline.getText().toString().contains("链路在线"),"connected state");
            check(activity.deviceText.getText().toString().contains("480 Mb/s"),"USB bus speed");
            check(activity.networkText.getText().toString().contains("2500 Mb/s"),"Ethernet negotiated speed");
            activity.render("DEV|1-1|0bda|8156|RTL8156|480|||auto\nROOT|yes");
            check(activity.headline.getText().toString().contains("尚无网络接口"),"unbound state");
            activity.render(real);
        });
        check(!activity.ports.isEmpty(),"no actual port discovered");
        MainActivity.Result invalid=activity.shell(activity.script("recover.sh","reauth","../../invalid","0bda:8156"),true,10);
        check(invalid.code==17,"device guard failed on actual Android shell");
        runOnMainSync(()->activity.diagnose());awaitIdle();
        check(activity.latest.contains("========== KERNEL_USB =========="),"diagnostic script failed");
        check(activity.latest.contains("r8152"),"installed driver not captured");
        File report=new File(getTargetContext().getExternalFilesDir(null),"smoke-report.txt");
        try(FileOutputStream f=new FileOutputStream(report)){f.write(activity.latest.getBytes(StandardCharsets.UTF_8));}
        result.putString("stream","PASS: Root uid=0; real USB port; current role parsing; RTL8156 bound/unbound; USB vs Ethernet speeds; actual-shell input guard; full diagnostics. Report: "+report.getAbsolutePath());
        runOnMainSync(()->{activity.mode=1;activity.saveMode();activity.monitor.setChecked(true);activity.event("实机测试通过：Root / 端口 / 状态解析 / 完整诊断 / 参数保护");});
        finish(Activity.RESULT_OK,result);
    }catch(Throwable t){result.putString("stream","FAIL: "+t);finish(Activity.RESULT_CANCELED,result);}}
}
