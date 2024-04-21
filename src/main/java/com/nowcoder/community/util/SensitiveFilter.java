package com.nowcoder.community.util;

import jakarta.annotation.PostConstruct;
import org.apache.commons.lang3.CharUtils;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.HashMap;
import java.util.Map;

//为了方便交给容器托管
@Component
public class SensitiveFilter {

    private static final Logger logger = LoggerFactory.getLogger(SensitiveFilter.class);
    private static final String REPLACEMENT = "***";  //替换符

    // 定义前缀树结构
    private class TrieNode{

        // 1.关键词结束标志
        private boolean isKeyWordEnd = false;

        public boolean isKeyWordEnd() {
            return isKeyWordEnd;
        }

        public void setKeyWordEnd(boolean keyWordEnd) {
            isKeyWordEnd = keyWordEnd;
        }

        // 2.儿子,key是下级字符，value是下级节点
        private Map<Character, TrieNode> subNodes = new HashMap<>();

        //3.添加子节点
        public void addSubNode(Character c, TrieNode node){
            subNodes.put(c,node);
        }

        //4.获取子节点
        public  TrieNode getSubNode(Character c){
            return subNodes.get(c);
        }
    }

    // 添加敏感词到树中
    private void addKeyWord(String keyword){
        TrieNode tmp = rootNode;
        for(int i = 0; i < keyword.length(); i ++){
            char c = keyword.charAt(i);
            TrieNode subNode = tmp.getSubNode(c);
            if(subNode == null){  //若c这个字符不存在
                subNode = new TrieNode();
                tmp.addSubNode(c,subNode);  //挂载到父节点下
            }
            tmp = subNode;

            //设置结束符
            if(i == keyword.length() -1){
                tmp.setKeyWordEnd(true);
            }
        }
    }

    //跳过符号，ex.敏感词为ab ，用户写 ※a※b※ 规避检测
    private boolean isSymbol(Character c){
        return !CharUtils.isAsciiAlphanumeric(c)  //是字符ture，是符号false，故取反
                && ( c < 0x2E80 || c > 0x9FFF);   // 0x2E80 - 0x9FFF 是东亚文字符号（日文、韩文等），无需跳过
    }

    //初始化前缀树
    private TrieNode rootNode = new TrieNode();
    //仅第一次调用有效，且在服务启动时会自动初始化
    @PostConstruct
    public void init(){
        try(
                InputStream is =  this.getClass().getClassLoader().getResourceAsStream("sensitive-words.txt");//从编译后的 target/classes/下读取
                //把字节流转成缓冲流
                BufferedReader reader = new BufferedReader(new InputStreamReader(is));
        ){
            String keyword;
            while((keyword = reader.readLine()) != null){
                this.addKeyWord(keyword);
            }
        }catch (IOException e){
            logger.error("加载敏感词文件失败：" + e.getMessage());
        }


    }

    // 检索敏感词，利用3个指针
    public String filter(String text){  //返回加完***的text
        if(StringUtils.isBlank(text)){
            return null;
        }

        TrieNode tmp1 = rootNode;
        int tmp2 = 0;
        int tmp3 = 0;
        StringBuilder res = new StringBuilder();

        while(tmp3 < text.length()){
            char c =text.charAt(tmp3);
            if(isSymbol(c)){
                // 若tmp1处于root，将此符号计入结果，让tmp2后移一步
                if(tmp1 == rootNode){
                    res.append(c);
                    tmp2 ++;
                }
                /*
                 这里的逻辑是：若敏感词为ab，用户输入a※b
                 比如，tmp2指向a，tmp3指向※。此时tmp1指向a，发现这疑似敏感词，tmp2不能动，因为要标记开始位置
                */
                tmp3 ++;
                continue;  //符号无需处理
            }

            // 检测下级节点
            tmp1 = tmp1.getSubNode(c);
            if(tmp1 == null){
                //以tmp2开头的词不是敏感词
                res.append(text.charAt(tmp2));
                tmp2 ++;
                tmp3 = tmp2;
                tmp1 = rootNode;
            }
            else if(tmp1.isKeyWordEnd()){
                // 检测完毕，发现敏感词
                res.append(REPLACEMENT);
                tmp3 ++;
                tmp2 = tmp3;
                tmp1 = rootNode;
            }else{
                // 尚未检测到 标记 时
                tmp3 ++;
            }
        }
        // 把没加进来的字符全加进来
        res.append(text.substring(tmp2));

        return res.toString();
    }
}