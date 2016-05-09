#import "CDVWeixin.h"
#import "WXUtil.h"
#import "WXHttpUtil.h"
#import  "PayOrder.h"

@implementation CDVWeixin



#pragma mark "API"

-(void)pluginInitialize{
    CDVViewController *viewController = (CDVViewController *)self.viewController;
    self.app_id = [viewController.settings objectForKey:@"weixinappid"];
    self.app_key = [viewController.settings objectForKey:@"app_key"];
    self.app_secret = [viewController.settings objectForKey:@"app_secret"];
    self.partner_key = [viewController.settings objectForKey:@"partner_key"];
    self.partner_id = [viewController.settings objectForKey:@"partner_id"];
}


-(void) prepareForExec:(CDVInvokedUrlCommand *)command{
    [WXApi registerApp:self.app_id];
    self.currentCallbackId = command.callbackId;
    if (![WXApi isWXAppInstalled])
    {
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"未安装微信"];
        [self.commandDelegate sendPluginResult:result callbackId:command.callbackId];
        [self endForExec];
        return;
    }
}

-(NSDictionary *)checkArgs:(CDVInvokedUrlCommand *) command{
    // check arguments
    NSDictionary *params = [command.arguments objectAtIndex:0];
    if (!params)
    {
        [self.commandDelegate sendPluginResult:[CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"参数错误"] callbackId:command.callbackId];
        
        [self endForExec];
        return nil;
    }
    return params;
}



-(void) endForExec{
    self.currentCallbackId = nil;
}


- (void)share:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *params = [self checkArgs:command];
    if(params == nil){
        return;
    }
    SendMessageToWXReq* req = [[SendMessageToWXReq alloc] init];
    // check the scene
    if ([params objectForKey:@"scene"])
    {
        req.scene = [[params objectForKey:@"scene"] integerValue];
    }else{
        req.scene = WXSceneTimeline;
    }
    // message or text?
    NSDictionary *message = [params objectForKey:@"message"];
    if (message){
        req.bText = NO;
        // async
        [self.commandDelegate runInBackground:^{
            req.message = [self buildSharingMessage:message];
            [WXApi sendReq:req];
        }];
    }else{
        req.bText = YES;
        req.text = [params objectForKey:@"text"];
        [WXApi sendReq:req];
    }
}


- (void)sendPayReq:(CDVInvokedUrlCommand *)command{
    [self prepareForExec:command];
    NSDictionary *params = [self checkArgs:command];
    if(params == nil){
        return;
    }
    NSString *prepayId = params[@"prepayid"];
    // 获取预支付订单id，调用微信支付sdk
    if (prepayId){
        NSLog(@"--- PrePayId: %@", prepayId);
        // 调起微信支付
        PayReq *request   = [[PayReq alloc] init];
        request.partnerId = self.partner_id;
        request.prepayId  = prepayId;
        request.package   = params[@"package"];
        request.nonceStr  = params[@"noncestr"];
        request.timeStamp =[params[@"timestamp"] intValue];
        request.sign = params[@"sign"];
        
        // 在支付之前，如果应用没有注册到微信，应该先调用 [WXApi registerApp:appId] 将应用注册到微信
        [WXApi sendReq:request];
    }
};




#pragma mark - 支付结果
- (void)getOrderPayResult:(NSNotification *)notification
{
    if ([notification.object isEqualToString:@"success"])
    {
        NSLog(@"success: 支付成功");
    }
    else
    {
        NSLog(@"fail: 支付失败");
    }
}


- (void)onResp:(BaseResp *)resp{
    CDVPluginResult *result = nil;
    BOOL success = NO;
    if([resp isKindOfClass:[SendMessageToWXResp class]]){
        switch (resp.errCode)
        {
            case WXSuccess:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK];
                success = YES;
                break;
                
            case WXErrCodeCommon:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"普通错误类型"];
                break;
                
            case WXErrCodeUserCancel:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"用户点击取消并返回"];
                break;
                
            case WXErrCodeSentFail:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"发送失败"];
                break;
                
            case WXErrCodeAuthDeny:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"授权失败"];
                break;
                
            case WXErrCodeUnsupport:
                result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"微信不支持"];
                break;
        }
        if (!result)
        {
            result = [CDVPluginResult resultWithStatus:CDVCommandStatus_ERROR messageAsString:@"Unknown"];
        }
        [self.commandDelegate sendPluginResult:result callbackId:self.currentCallbackId];
    }else if ([resp isKindOfClass:[PayResp class]])
    {
        PayResp *response = (PayResp *)resp;
        
        
        CDVPluginResult *result = [CDVPluginResult resultWithStatus:CDVCommandStatus_OK messageAsString:[NSString stringWithFormat:@"%d",response.errCode]];
        [self.commandDelegate sendPluginResult:result callbackId:[self currentCallbackId]];
    }
    [self endForExec];
}

#pragma mark "CDVPlugin Overrides"
- (void)handleOpenURL:(NSNotification *)notification{
    NSURL* url = [notification object];
    if ([url isKindOfClass:[NSURL class]] && [url.scheme isEqualToString:self.app_id])
    {
        [WXApi handleOpenURL:url delegate:self];
    }
}

- (WXMediaMessage *)buildSharingMessage:(NSDictionary *)message{
    WXMediaMessage *wxMediaMessage = [WXMediaMessage message];
    wxMediaMessage.title = [message objectForKey:@"title"];
    wxMediaMessage.description = [message objectForKey:@"description"];
    wxMediaMessage.mediaTagName = [message objectForKey:@"mediaTagName"];
    [wxMediaMessage setThumbImage:[self getUIImageFromURL:[message objectForKey:@"thumb"]]];
    
    // media parameters
    id mediaObject = nil;
    NSDictionary *media = [message objectForKey:@"media"];
    
    // check types
    NSInteger type = [[media objectForKey:@"type"] integerValue];
    switch (type)
    {
        case CDVWXSharingTypeApp:
            break;
            
        case CDVWXSharingTypeEmotion:
            break;
            
        case CDVWXSharingTypeFile:
            break;
            
        case CDVWXSharingTypeImage:
            break;
            
        case CDVWXSharingTypeMusic:
            break;
            
        case CDVWXSharingTypeVideo:
            break;
            
        case CDVWXSharingTypeWebPage:
        default:
            mediaObject = [WXWebpageObject object];
            ((WXWebpageObject *)mediaObject).webpageUrl = [media objectForKey:@"webpageUrl"];
    }
    
    wxMediaMessage.mediaObject = mediaObject;
    return wxMediaMessage;
}

- (UIImage *)getUIImageFromURL:(NSString *)thumb
{
    NSURL *thumbUrl = [NSURL URLWithString:thumb];
    NSData *data = nil;
    
    if ([thumbUrl isFileURL])
    {
        // local file
        data = [NSData dataWithContentsOfFile:thumb];
    }
    else
    {
        data = [NSData dataWithContentsOfURL:thumbUrl];
    }
    
    return [UIImage imageWithData:data];
}

@end
