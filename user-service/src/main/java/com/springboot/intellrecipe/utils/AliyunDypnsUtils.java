package com.springboot.intellrecipe.utils;

import cn.hutool.json.JSONObject;
import cn.hutool.json.JSONUtil;
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

/**
 * 阿里云号码认证服务（DYPNS）工具类
 * 调用 SendSmsVerifyCode 接口，验证码由阿里云生成并通过短信下发，同时返回给服务端保存。
 * 签名与模板必须使用号码认证控制台的「赠送签名/模板」，自定义签名已被运营商限制。
 */
@Slf4j
@Component
public class AliyunDypnsUtils {

    @Value("${aliyun.sms.access-key-id}")
    private String accessKeyId;

    @Value("${aliyun.sms.access-key-secret}")
    private String accessKeySecret;

    /** 赠送签名，在号码认证控制台「赠送签名配置」页面获取 */
    @Value("${aliyun.sms.sign-name}")
    private String signName;

    /** 赠送模板 CODE，在号码认证控制台「赠送模板配置」页面获取（数字形式，如 100001） */
    @Value("${aliyun.sms.template-code}")
    private String templateCode;

    /** 验证码有效期（分钟），写入模板参数 min 字段 */
    @Value("${aliyun.sms.template-param-min}")
    private String templateParamMin;

    /**
     * 凭证是否已配置（AccessKey 非空）。
     * 未配置时自动进入开发模式，验证码直接在接口响应中返回，无需真实短信。
     */
    public boolean isConfigured() {
        return accessKeyId != null && !accessKeyId.isBlank()
                && accessKeySecret != null && !accessKeySecret.isBlank();
    }

    /**
     * 调用 DYPNS SendSmsVerifyCode 接口发送短信验证码。
     * 验证码由阿里云生成（##code## 占位符），接口返回后由本方法返回给调用方保存至 Redis。
     *
     * @param phone 手机号
     * @return 阿里云生成的验证码字符串；发送失败时返回 null
     */
    public String sendVerifyCode(String phone) {
        DefaultProfile profile = DefaultProfile.getProfile("cn-hangzhou", accessKeyId, accessKeySecret);
        IAcsClient client = new DefaultAcsClient(profile);

        CommonRequest request = new CommonRequest();
        request.setSysMethod(MethodType.POST);
        request.setSysDomain("dypnsapi.aliyuncs.com");
        request.setSysVersion("2017-05-25");
        request.setSysAction("SendSmsVerifyCode");

        request.putQueryParameter("PhoneNumber", phone);
        request.putQueryParameter("SignName", signName);
        request.putQueryParameter("TemplateCode", templateCode);
        // ##code## 由阿里云随机生成；ReturnVerifyCode=true 让接口将生成的码返回给我们
        request.putQueryParameter("TemplateParam",
                String.format("{\"code\":\"##code##\",\"min\":\"%s\"}", templateParamMin));
        request.putQueryParameter("ReturnVerifyCode", "true");
        request.putQueryParameter("CodeType", "1"); // 1=纯数字

        try {
            CommonResponse response = client.getCommonResponse(request);
            log.debug("DYPNS 响应: HTTP={}, body={}", response.getHttpStatus(), response.getData());

            if (response.getHttpStatus() == 200) {
                JSONObject json = JSONUtil.parseObj(response.getData());
                if (Boolean.TRUE.equals(json.getBool("Success"))) {
                    String code = json.getByPath("Model.VerifyCode", String.class);
                    log.info("DYPNS 短信发送成功: phone={}", phone);
                    return code;
                }
                log.error("DYPNS 业务失败: Code={}, Message={}", json.getStr("Code"), json.getStr("Message"));
            } else {
                log.error("DYPNS HTTP 异常: status={}, body={}", response.getHttpStatus(), response.getData());
            }
        } catch (ClientException e) {
            log.error("DYPNS 调用异常: ErrCode={}, ErrMsg={}", e.getErrCode(), e.getErrMsg());
        }
        return null;
    }
}