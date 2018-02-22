package com.codeboy.fengxingqiangdan.job;

import android.annotation.TargetApi;
import android.app.Notification;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.os.Parcelable;
import android.util.Log;
import android.view.accessibility.AccessibilityEvent;
import android.view.accessibility.AccessibilityNodeInfo;

import com.codeboy.fengxingqiangdan.Config;
import com.codeboy.fengxingqiangdan.IStatusBarNotification;
import com.codeboy.fengxingqiangdan.FXQDApplication;
import com.codeboy.fengxingqiangdan.QiangHongBaoService;
import com.codeboy.fengxingqiangdan.util.AccessibilityHelper;
import com.codeboy.fengxingqiangdan.util.NotifyHelper;

import java.util.List;
import java.util.Random;

/**
 * <p>Created 16/1/16 上午12:40.</p>
 * <p><a href="mailto:codeboy2013@gmail.com">Email:codeboy2013@gmail.com</a></p>
 * <p><a href="http://www.happycodeboy.com">LeonLee Blog</a></p>
 *
 * @author LeonLee
 */
public class FengxingAccessbilityJob extends BaseAccessbilityJob {

    private static final String TAG = "FengxingAccessbilityJob";

    /** 包名*/
    public static final String FENGXING_PACKAGENAME = "com.fx.milk";

    /** 消息的关键字*/
    private static final String ORDER_TEXT_KEY = "您有新订单";

    private static final String BUTTON_CLASS_NAME = "android.widget.Button";


    /** 不能再使用文字匹配的最小版本号 */
    private static final int USE_ID_MIN_VERSION = 700;// 6.3.8 对应code为680,6.3.9对应code为700

    private static final int WINDOW_NONE = 0;
    private static final int WINDOW_WAIT_FOR_CONFIRM = 1;

    private int mCurrentWindow = WINDOW_NONE;

    private boolean isReceivingOrder;
    private PackageInfo mFengxingPackageInfo = null;
    private Handler mHandler = null;

    private BroadcastReceiver broadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            //更新安装包信息
            updatePackageInfo();
        }
    };

    @Override
    public void onCreateJob(QiangHongBaoService service) {
        super.onCreateJob(service);

        updatePackageInfo();

        IntentFilter filter = new IntentFilter();
        filter.addDataScheme("package");
        filter.addAction("android.intent.action.PACKAGE_ADDED");
        filter.addAction("android.intent.action.PACKAGE_REPLACED");
        filter.addAction("android.intent.action.PACKAGE_REMOVED");

        getContext().registerReceiver(broadcastReceiver, filter);
    }

    @Override
    public void onStopJob() {
        try {
            getContext().unregisterReceiver(broadcastReceiver);
        } catch (Exception e) {}
    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN_MR2)
    @Override
    public void onNotificationPosted(IStatusBarNotification sbn) {
        Notification nf = sbn.getNotification();
        String text = String.valueOf(sbn.getNotification().tickerText);
        notificationEvent(text, nf);
    }

    @Override
    public boolean isEnable() {
        return getConfig().isEnableFengxing();
    }

    @Override
    public String getTargetPackageName() {
        return FENGXING_PACKAGENAME;
    }

    @Override
    public void onReceiveJob(AccessibilityEvent event) {
        final int eventType = event.getEventType();
        Log.i(TAG, "Received accessibility event. Type is " + event.getEventType() +
                ", classname is " + event.getClassName());

        //通知栏事件
        if(eventType == AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED) {
            Parcelable data = event.getParcelableData();
            if(data == null || !(data instanceof Notification)) {
                return;
            }
            List<CharSequence> texts = event.getText();
            if(!texts.isEmpty()) {
                String text = String.valueOf(texts.get(0));
                notificationEvent(text, (Notification) data);
            }
        }
        else if(eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) {
            openOrder(event);
        }
//      else if(eventType == AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED) {
//            if(mCurrentWindow != WINDOW_LAUNCHER) { //不在聊天界面或聊天列表，不处理
//                return;
//            }
//            if(isReceivingOrder) {
//                handleChatListHongBao();
//            }
//        }
    }

    /** 通知栏事件*/
    private void notificationEvent(String ticker, Notification nf) {
        String text = ticker;
        int index = text.indexOf(":");
        if(index != -1) {
            text = text.substring(index + 1);
        }
        text = text.trim();
        if(text.contains(ORDER_TEXT_KEY)) { //新订单消息
            newOrderNotification(nf);
        }
    }

    /** 打开通知栏消息*/
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void newOrderNotification(Notification notification) {
        isReceivingOrder = true;
        //以下是精华，将通知栏消息打开
        PendingIntent pendingIntent = notification.contentIntent;
        boolean lock = NotifyHelper.isLockScreen(getContext());

        if(!lock) {
            NotifyHelper.send(pendingIntent);
        } else {
            NotifyHelper.showNotify(getContext(), String.valueOf(notification.tickerText), pendingIntent);
        }

    }

    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void openOrder(AccessibilityEvent event) {
        if("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyReceiveUI".equals(event.getClassName())) {
            mCurrentWindow = WINDOW_WAIT_FOR_CONFIRM;
            //点中了订单，下一步是点击 接单
            handleOrderReceive();
        }
//        else if("com.tencent.mm.plugin.luckymoney.ui.LuckyMoneyDetailUI".equals(event.getClassName())) {
//            mCurrentWindow = WINDOW_LUCKYMONEY_DETAIL;
//            //拆完红包后看详细的纪录界面
//            if(getConfig().getWechatAfterGetHongBaoEvent() == Config.WX_AFTER_GET_GOHOME) { //返回主界面，以便收到下一次的红包通知
//                AccessibilityHelper.performHome(getService());
//            }
//        } else if("com.tencent.mm.ui.LauncherUI".equals(event.getClassName())) {
//            mCurrentWindow = WINDOW_LAUNCHER;
//            //在聊天界面,去点中红包
//            handleChatListHongBao();
//        } else {
//            mCurrentWindow = WINDOW_OTHER;
//        }
    }

    /**
     * 点击通知栏消息后，打开的界面
     * */
    @TargetApi(Build.VERSION_CODES.JELLY_BEAN)
    private void handleOrderReceive() {
        AccessibilityNodeInfo nodeInfo = getService().getRootInActiveWindow();
        if(nodeInfo == null) {
            Log.w(TAG, "rootWindow为空");
            return;
        }

        AccessibilityNodeInfo targetNode = null;

        String buttonId = "com.fx.milk:id/but_get_order";

        targetNode = AccessibilityHelper.findNodeInfosById(nodeInfo, buttonId);

        if(targetNode == null) { //通过组件查找
            targetNode = AccessibilityHelper.findNodeInfosByClassName(nodeInfo, BUTTON_CLASS_NAME);
        }

        if(targetNode != null) {
            final AccessibilityNodeInfo n = targetNode;
            Random rand = new Random();
            long sDelayTime = rand.nextInt(1000); // 延迟100毫秒点击
            getHandler().postDelayed(new Runnable() {
                @Override
                public void run() {
                    AccessibilityHelper.performClick(n);
                }
            }, sDelayTime);

            // 统计
            FXQDApplication.eventStatistics(getContext(), "open_order");

        }
    }

    private Handler getHandler() {
        if(mHandler == null) {
            mHandler = new Handler(Looper.getMainLooper());
        }
        return mHandler;
    }


    /** 更新包信息*/
    private void updatePackageInfo() {
        try {
            mFengxingPackageInfo = getContext().getPackageManager().getPackageInfo(FENGXING_PACKAGENAME, 0);
        } catch (PackageManager.NameNotFoundException e) {
            e.printStackTrace();
        }
    }
}
