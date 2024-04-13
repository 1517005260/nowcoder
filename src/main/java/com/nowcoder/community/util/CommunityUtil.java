package com.nowcoder.community.util;

import org.apache.commons.lang3.StringUtils;
import org.springframework.util.DigestUtils;

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
}
