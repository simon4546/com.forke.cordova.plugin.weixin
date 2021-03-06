package net.sourceforge.simcpux.wxapi;



import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;

import com.forke.cordova.Config;
import com.forke.cordova.plugin.weixin.Weixin;
import com.tencent.mm.sdk.constants.ConstantsAPI;
import com.tencent.mm.sdk.modelbase.BaseReq;
import com.tencent.mm.sdk.modelbase.BaseResp;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.IWXAPIEventHandler;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

import org.json.JSONObject;

public class WXPayEntryActivity extends Activity implements IWXAPIEventHandler{
    
    private static final String TAG = "Weixin";
    
    private IWXAPI api;
    
    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Class<?> klass;
        try {
            klass = WXPayEntryActivity.class.getClassLoader().loadClass("com.forke.cordova.plugin.weixin.R$layout");
            Integer layoutNum = (Integer) klass.getDeclaredField("pay_result").get(klass);
            setContentView(layoutNum);
        } catch (ClassNotFoundException e) {
            e.printStackTrace();
        } catch (IllegalAccessException e) {
            e.printStackTrace();
        } catch (IllegalArgumentException e) {
            e.printStackTrace();
        } catch (NoSuchFieldException e) {
            e.printStackTrace();
        }
        Config.init();
        api = WXAPIFactory.createWXAPI(this,Config.getPreferences().getString("weixinappid", ""));
        api.handleIntent(getIntent(), this);
    }
    
    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        setIntent(intent);
        api.handleIntent(intent, this);
    }
    
    @Override
    public void onReq(BaseReq req) {
    }
    
    @Override
    public void onResp(BaseResp resp) {
        Log.d(TAG, "onPayFinish, errCode = " + resp.errCode);
        if (resp.getType() == ConstantsAPI.COMMAND_PAY_BY_WX) {
            try {
                JSONObject res=new JSONObject();
                res.put("type", "pay");
                res.put("result",resp.errCode);
                Weixin.currentCallbackContext.success(res);
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        this.finish();
    }
}