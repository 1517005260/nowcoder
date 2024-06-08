package com.nowcoder.community.util;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.JSONObject;
import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

import java.util.HashMap;
import java.util.Map;
import java.util.SplittableRandom;
import java.util.UUID;

// 都是些简单方法，不需要容器托管，故不要注解
public class CommunityUtil {

    // 生成随机字符串，可用于：邮件激活链接、用户上传文件命名等
    public static String genUUID(){
        return UUID.randomUUID().toString().replaceAll("-","");  //去除横线
    }

    // 加密算法，可用于：用户密码加密等。
    // MD5加密：加密容易解密难。并且为了防止黑客用对照表解密（比如hello固定被加密为abcde），加入“salt”随机字符串
    public static String md5(String key){
        if(StringUtils.isBlank(key)){
            // 判定空值
            return null;
        }
        return DigestUtils.md5DigestAsHex(key.getBytes());
    }

    // 取得json串，转化成字符串
    public static String getJSONString(int code, String message, Map<String, Object> map){
        JSONObject json = new JSONObject();
        json.put("code", code);
        json.put("message", message);
        if(map != null){
            for(String key : map.keySet()){
                json.put(key,map.get(key));
            }
        }
        return json.toJSONString();
    }

    // 有时可能以上三个数据只有两个甚至一个，为了方便起见，我们进行重载
    public static String getJSONString(int code, String message){
        return getJSONString(code, message, null);
    }
    public static String getJSONString(int code){
        return getJSONString(code, null, null);
    }

    // editor.md 要求返回的 JSON 字符串格式
    public static String getEditorMdJSONString(int success, String message, String url) {
        JSONObject json = new JSONObject();
        json.put("success", success);
        json.put("message", message);
        json.put("url", url);
        return json.toJSONString();
    }

    // test-json
    public static void main(String[] args) {
        Map<String, Object> map =new HashMap<>();
        map.put("name", "peter");
        map.put("age", 25);
        System.out.println(getJSONString(0, "ok", map));
    }
}
