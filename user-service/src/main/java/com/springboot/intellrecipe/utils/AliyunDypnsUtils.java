package com.springboot.intellrecipe.utils;

import com.aliyuncs.CommonRequest;
import com.aliyuncs.CommonResponse;
import com.aliyuncs.DefaultAcsClient;
import com.aliyuncs.IAcsClient;
import com.aliyuncs.exceptions.ClientException;
import com.aliyuncs.http.MethodType;
import com.aliyuncs.profile.DefaultProfile;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class AliyunDypnsUtils {

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    @Value("${aliyun.sms.sign-name}")
    private String signName;

    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    @Value("${aliyun.sms.template-param-min}")
    private String templateParamMin;

    /**
     * 发送短信验证码 (使用 CommonRequest 通用接口调用 Dypnsapi)
     * @param phone 手机号
     * @param code 验证码
     * @return 是否发送成功
     */
    public boolean sendVerifyCode(String phone, String code) {
        // 创建 DefaultAcsClient 实例并初始化
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        // 使用 CommonRequest 构造请求，避开特定 SDK 类缺失的问题
        CommonRequest request = new CommonRequest(); 
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dypnsapi.aliyuncs.com"); // 指定域名为号码认证服务
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSmsVerifyCode"); // 指定动作为发送短信验证码
        
        request.putQueryParameter("PhoneNumber", phone);
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateCode);
        
        // 这里的 TemplateParam 是 JSON 格式，注意 key 要与模板中的 ${code}, ${min} 对应
        String param = String.format("{\"code\":\"%s\", \"min\":\"%s\"}", code, templateParamMin);
        request.putQueryParameter("TemplateParam", param);
        
        // 发送请求
        try {
            CommonResponse response = client.getCommonResponse(request);
            // 只要不是 ClientException，通常 HTTP 状态码 200 即为调用成功
            // 但仍需检查业务层面的 Code 是否为 OK
            // 注意：CommonResponse.getData() 返回的是 JSON 字符串，这里简化判断
            if (response.getHttpStatus() == 200 && response.getData().contains("\"Code\":\"OK\"")) {
                log.info("阿里云短信发送成功: phone={}, code={}", phone, code);
                return true;
            } else {
                log.error("阿里云短信发送失败: HTTP Status={}, Data={}", response.getHttpStatus(), response.getData());
                return false;
            }
        } catch (ClientException e) {
            log.error("阿里云短信发送异常: ErrCode={}, ErrMsg={}", e.getErrCode(), e.getErrMsg());
            return false;
        }
    }
}