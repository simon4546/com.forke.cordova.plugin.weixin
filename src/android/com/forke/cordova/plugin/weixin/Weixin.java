package com.forke.cordova.plugin.weixin;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.HashMap;


import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaInterface;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.CordovaWebView;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.util.Log;

import com.tencent.mm.sdk.modelmsg.SendMessageToWX;
import com.tencent.mm.sdk.modelmsg.WXMediaMessage;
import com.tencent.mm.sdk.modelmsg.WXTextObject;
import com.tencent.mm.sdk.modelmsg.WXWebpageObject;
import com.tencent.mm.sdk.modelpay.PayReq;
import com.tencent.mm.sdk.openapi.IWXAPI;
import com.tencent.mm.sdk.openapi.WXAPIFactory;

public class Weixin extends CordovaPlugin {
    public static final String TAG = "Weixin";

    public static final String ERROR_WX_NOT_INSTALLED = "未安装微信";
    public static final String ERROR_ARGUMENTS = "参数错误";

    public static final String KEY_ARG_MESSAGE = "message";
    public static final String KEY_ARG_SCENE = "scene";
    public static final String KEY_ARG_MESSAGE_TITLE = "title";
    public static final String KEY_ARG_MESSAGE_DESCRIPTION = "description";
    public static final String KEY_ARG_MESSAGE_THUMB = "thumb";
    public static final String KEY_ARG_MESSAGE_MEDIA = "media";
    public static final String KEY_ARG_MESSAGE_MEDIA_TYPE = "type";
    public static final String KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL = "webpageUrl";
    public static final String KEY_ARG_MESSAGE_MEDIA_TEXT = "text";

    public static final int TYPE_WX_SHARING_APP = 1;
    public static final int TYPE_WX_SHARING_EMOTION = 2;
    public static final int TYPE_WX_SHARING_FILE = 3;
    public static final int TYPE_WX_SHARING_IMAGE = 4;
    public static final int TYPE_WX_SHARING_MUSIC = 5;
    public static final int TYPE_WX_SHARING_VIDEO = 6;
    public static final int TYPE_WX_SHARING_WEBPAGE = 7;
    public static final int TYPE_WX_SHARING_TEXT = 8;

    protected IWXAPI api;

    public static CallbackContext currentCallbackContext;

    private String app_id;
    private static String partner_key;
    private static String partner_id;
    private static String app_secret;
    private static String app_key;
    private HashMap<String, PayOrder> payOrderList = new HashMap<String, PayOrder>();

    @Override
    public boolean execute(String action, JSONArray args,
                           CallbackContext callbackContext) throws JSONException {
        // save the current callback context
        currentCallbackContext = callbackContext;
        // check if installed
        if (!api.isWXAppInstalled()) {
            callbackContext.error(ERROR_WX_NOT_INSTALLED);
            return true;
        }
        if (action.equals("share")) {
            // sharing
            return share(args, callbackContext);
        } else if (action.equals("sendPayReq")) {
            return sendPayReq(args);
        }
        return false;
    }

    protected boolean sendPayReq(JSONArray args) {
        Log.i(TAG, "pay begin");
        try {
            JSONObject prepayIdObj = args.getJSONObject(0);
            sendPayReq(prepayIdObj);
        } catch (JSONException e) {
            e.printStackTrace();
            currentCallbackContext.error("参数错误");
            return false;
        }
        return true;
    }

    protected void getWXAPI() {
        if (api == null) {
            app_id = webView.getPreferences().getString("weixinappid", "");
            api = WXAPIFactory.createWXAPI(webView.getContext(), app_id, true);
            Boolean registered = api.registerApp(webView.getPreferences().getString("weixinappid", ""));
        }
    }

    protected boolean share(JSONArray args, CallbackContext callbackContext)
            throws JSONException {
        // check if # of arguments is correct
        if (args.length() != 1) {
            callbackContext.error(ERROR_ARGUMENTS);
        }

        final JSONObject params = args.getJSONObject(0);
        final SendMessageToWX.Req req = new SendMessageToWX.Req();
        req.transaction = String.valueOf(System.currentTimeMillis());

        if (params.has(KEY_ARG_SCENE)) {
            req.scene = params.getInt(KEY_ARG_SCENE);
        } else {
            req.scene = SendMessageToWX.Req.WXSceneTimeline;
        }

        // run in background
        cordova.getThreadPool().execute(new Runnable() {
            @Override
            public void run() {
                try {
                    req.message = buildSharingMessage(params.getJSONObject(KEY_ARG_MESSAGE));
                } catch (JSONException e) {
                    e.printStackTrace();
                    currentCallbackContext.error(e.getMessage());
                }
                Boolean sended = api.sendReq(req);
                if (sended) {
                    currentCallbackContext.success();
                } else {
                    currentCallbackContext.error("发送失败");
                }
            }
        });
        return true;
    }

    protected WXMediaMessage buildSharingMessage(JSONObject message)
            throws JSONException {
        URL thumbnailUrl = null;
        Bitmap thumbnail = null;

        try {
            thumbnailUrl = new URL(message.getString(KEY_ARG_MESSAGE_THUMB));
            thumbnail = BitmapFactory.decodeStream(thumbnailUrl
                    .openConnection().getInputStream());

        } catch (MalformedURLException e1) {
            e1.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }

        WXMediaMessage wxMediaMessage = new WXMediaMessage();
        wxMediaMessage.title = message.getString(KEY_ARG_MESSAGE_TITLE);
        wxMediaMessage.description = message
                .getString(KEY_ARG_MESSAGE_DESCRIPTION);
        if (thumbnail != null) {
            wxMediaMessage.setThumbImage(thumbnail);
        }

        // media parameters
        WXMediaMessage.IMediaObject mediaObject = null;
        JSONObject media = message.getJSONObject(KEY_ARG_MESSAGE_MEDIA);

        // check types
        int type = media.has(KEY_ARG_MESSAGE_MEDIA_TYPE) ? media
                .getInt(KEY_ARG_MESSAGE_MEDIA_TYPE) : TYPE_WX_SHARING_WEBPAGE;
        switch (type) {
            case TYPE_WX_SHARING_APP:
                break;

            case TYPE_WX_SHARING_EMOTION:
                break;

            case TYPE_WX_SHARING_FILE:
                break;

            case TYPE_WX_SHARING_IMAGE:
                break;

            case TYPE_WX_SHARING_MUSIC:
                break;

            case TYPE_WX_SHARING_VIDEO:
                break;

            case TYPE_WX_SHARING_TEXT:
                mediaObject = new WXTextObject();
                ((WXTextObject) mediaObject).text = media.getString(KEY_ARG_MESSAGE_MEDIA_TEXT);
                break;

            case TYPE_WX_SHARING_WEBPAGE:
            default:
                mediaObject = new WXWebpageObject();
                ((WXWebpageObject) mediaObject).webpageUrl = media
                        .getString(KEY_ARG_MESSAGE_MEDIA_WEBPAGEURL);
        }
        wxMediaMessage.mediaObject = mediaObject;
        return wxMediaMessage;
    }


    private void sendPayReq(JSONObject args) {
        try {
            PayReq req = new PayReq();
            req.appId = app_id;
            req.partnerId = partner_id;
            req.prepayId = args.get("prepayid").toString();
            req.nonceStr = args.get("noncestr").toString();
            req.timeStamp = args.get("timestamp").toString();
            req.packageValue = args.get("package").toString();
            req.sign = args.get("sign").toString();
            // 在支付之前，如果应用没有注册到微信，应该先调用IWXMsg.registerApp将应用注册到微信
            api.sendReq(req);
            currentCallbackContext.success("{\"partnerId\":\""+req.partnerId+"\",\"prepayId\":\""+req.prepayId+"\"}");
        } catch (Exception x) {

        }
    }

    @Override
    public void initialize(CordovaInterface cordova, CordovaWebView webView) {
        super.initialize(cordova, webView);
        partner_key = webView.getPreferences().getString("partner_key", "");
        partner_id = webView.getPreferences().getString("partner_id", "");
        app_secret = webView.getPreferences().getString("app_secret", "");
        app_key = webView.getPreferences().getString("app_key", "");
        getWXAPI();
        this.onWeixinResp(cordova.getActivity().getIntent());
    }

    private void onWeixinResp(Intent intent) {
        Bundle extras = intent.getExtras();
        if (extras != null) {
            String intentType = extras.getString("intentType");
            if ("com.forke.cordova.plugin.weixin.Weixin".equals(intentType)) {
                if (currentCallbackContext != null) {
                    currentCallbackContext.success(extras.getInt("weixinPayRespCode"));
                }
            }
        }
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        Log.i(TAG, "onNewIntent");
        this.onWeixinResp(intent);
    }
}
