package cn.usbdoctor;

import android.content.*;
import android.content.pm.PackageManager;
import android.os.*;
import rikka.shizuku.Shizuku;

/** Lifecycle and permission handling; app preferences never imply a live privileged connection. */
final class ShizukuBridge {
    final Context context; final Runnable changed; final Handler main=new Handler(Looper.getMainLooper());
    volatile IBinder service; boolean binding=false,closed=false;
    final Shizuku.UserServiceArgs args;
    final Shizuku.OnBinderReceivedListener received=()->notifyChanged();
    final Shizuku.OnBinderDeadListener died=()->{service=null;binding=false;notifyChanged();};
    final Shizuku.OnRequestPermissionResultListener permission=(request,result)->{if(request==42){if(result==PackageManager.PERMISSION_GRANTED)bind();notifyChanged();}};
    final ServiceConnection connection=new ServiceConnection(){
        public void onServiceConnected(ComponentName name,IBinder binder){if(closed)return;service=binder;binding=false;notifyChanged();}
        public void onServiceDisconnected(ComponentName name){service=null;binding=false;notifyChanged();}
    };
    ShizukuBridge(Context context,Runnable changed){this.context=context;this.changed=changed;args=new Shizuku.UserServiceArgs(new ComponentName(context,PrivilegedService.class)).daemon(false).processNameSuffix("usb_worker").version(2);
        Shizuku.addBinderReceivedListenerSticky(received);Shizuku.addBinderDeadListener(died);Shizuku.addRequestPermissionResultListener(permission);
    }
    void notifyChanged(){main.post(()->{if(!closed)changed.run();});}
    boolean available(){return Shizuku.pingBinder();}
    boolean permitted(){try{return available()&&Shizuku.getVersion()>=13&&Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED;}catch(Exception e){return false;}}
    boolean ready(){return permitted()&&service!=null&&service.isBinderAlive();}
    int uid(){try{return permitted()?Shizuku.getUid():-1;}catch(Exception e){return -1;}}
    void connect(){if(!available())throw new IllegalStateException("请先在 Shizuku 中通过无线调试启动服务");if(Shizuku.getVersion()<13)throw new IllegalStateException("请更新 Shizuku 至 v13 或更新版本");if(permitted())bind();else if(Shizuku.shouldShowRequestPermissionRationale())throw new IllegalStateException("请在 Shizuku 的已授权应用中允许 USB网卡医生");else Shizuku.requestPermission(42);}
    void bind(){if(closed||binding||ready()||!permitted())return;binding=true;try{Shizuku.bindUserService(args,connection);main.postDelayed(()->{if(binding&&!ready()){binding=false;notifyChanged();}},12000);}catch(RuntimeException e){binding=false;throw e;}}
    MainActivity.Result run(String op,String action,String name,String identity)throws Exception{
        IBinder remote=service;if(!ready()||remote==null)throw new IllegalStateException("Shizuku 已断开，请在权限栏目重新连接");
        Parcel data=Parcel.obtain(),reply=Parcel.obtain();try{data.writeInterfaceToken(PrivilegedService.DESCRIPTOR);data.writeString(op);data.writeString(action);data.writeString(name);data.writeString(identity);if(!remote.transact(PrivilegedService.RUN,data,reply,0))throw new RemoteException("Unsupported service transaction");reply.readException();return new MainActivity.Result(reply.readInt(),reply.readString());}finally{data.recycle();reply.recycle();}
    }
    void close(){closed=true;Shizuku.removeBinderReceivedListener(received);Shizuku.removeBinderDeadListener(died);Shizuku.removeRequestPermissionResultListener(permission);try{Shizuku.unbindUserService(args,connection,false);}catch(Exception ignored){}service=null;}
}
