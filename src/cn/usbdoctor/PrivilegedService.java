package cn.usbdoctor;

import android.content.Context;
import android.os.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** Shizuku user service: only bundled diagnostic/recovery operations, never arbitrary commands. */
public final class PrivilegedService extends Binder {
    static final String DESCRIPTOR="cn.usbdoctor.PrivilegedService";
    static final int RUN=IBinder.FIRST_CALL_TRANSACTION, DESTROY=16777115;
    private final Context context;
    private final int owner;
    public PrivilegedService(Context context){this.context=context;owner=context.getApplicationInfo().uid;attachInterface(null,DESCRIPTOR);}
    @Override protected boolean onTransact(int code,Parcel data,Parcel reply,int flags)throws RemoteException {
        if(code==INTERFACE_TRANSACTION){reply.writeString(DESCRIPTOR);return true;}
        if(code==DESTROY){int caller=Binder.getCallingUid();if(caller!=owner&&caller!=android.os.Process.myUid())throw new SecurityException("Unexpected destroy caller");System.exit(0);return true;}
        if(code!=RUN)return super.onTransact(code,data,reply,flags);
        data.enforceInterface(DESCRIPTOR);
        if(Binder.getCallingUid()!=owner)throw new SecurityException("Unexpected caller");
        String operation=data.readString(),action=data.readString(),name=data.readString(),identity=data.readString();
        try {
            if("identity".equals(operation)){reply.writeNoException();reply.writeInt(0);reply.writeString("UID|"+android.os.Process.myUid());return true;}
            String asset;
            switch(operation){case "snapshot":asset="snapshot.sh";break;case "diagnose":asset="diagnose.sh";break;case "recover":asset="recover.sh";break;default:throw new IllegalArgumentException("Unknown operation");}
            if(action==null||name==null||identity==null||action.length()>20||name.length()>128||identity.length()>20)throw new IllegalArgumentException("Invalid arguments");
            // shell UID cannot read app-private script files. Stream our packaged asset through stdin.
            java.lang.Process p=new ProcessBuilder("/system/bin/sh","-s","--",action,name,identity).redirectErrorStream(true).start();
            ByteArrayOutputStream output=new ByteArrayOutputStream();
            Thread reader=new Thread(()->{try(InputStream in=p.getInputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1){synchronized(output){if(output.size()<120000)output.write(buf,0,Math.min(n,120000-output.size()));}}}catch(IOException ignored){}});reader.setDaemon(true);reader.start();
            try(InputStream in=context.getAssets().open(asset);OutputStream out=p.getOutputStream()){byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)out.write(buf,0,n);}
            boolean ended=p.waitFor(40,TimeUnit.SECONDS);if(!ended){p.destroy();if(!p.waitFor(3,TimeUnit.SECONDS))p.destroyForcibly();}reader.join(1000);
            String text;synchronized(output){text=output.toString("UTF-8")+(output.size()>=120000?"\n[诊断输出已达到上限，末尾可能截断]":"");}
            reply.writeNoException();reply.writeInt(ended?p.exitValue():124);reply.writeString(text+(ended?"":"\n操作超时，请重新检查端口状态"));
        }catch(Exception e){reply.writeNoException();reply.writeInt(125);reply.writeString("Shizuku 执行失败："+e);}
        return true;
    }
}
