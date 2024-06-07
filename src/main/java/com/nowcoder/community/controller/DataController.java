package com.nowcoder.community.controller;

import com.nowcoder.community.service.DataService;
import com.nowcoder.community.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.ResponseBody;

import java.util.*;

@Controller
public class DataController {
    @Autowired
    private DataService dataService;

    // 访问统计页面
    @RequestMapping(path = "/data", method = {RequestMethod.GET, RequestMethod.POST}) // post接收forward转来的请求
    public String getDataPage(){
        return "site/admin/data";
    }

    // 统计 UV 并获取图表数据
    @RequestMapping(path = "/data/uv", method = RequestMethod.POST)
    @ResponseBody
    public String getUVData(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                            @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        if (start == null || end == null || start.after(end)) {
            return CommunityUtil.getJSONString(1, "参数不能为空且开始日期不能晚于结束日期！");
        }

        long uv = dataService.calculateUV(start, end);
        List<Long> uvData = dataService.getUVChartData(start, end);

        return CommunityUtil.getJSONString(0, "success", Map.of("uvResult", uv, "uvData", uvData));
    }

    // 统计 DAU 并获取图表数据
    @RequestMapping(path = "/data/dau", method = RequestMethod.POST)
    @ResponseBody
    public String getDAUData(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                             @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        if (start == null || end == null || start.after(end)) {
            return CommunityUtil.getJSONString(1, "参数不能为空且开始日期不能晚于结束日期！");
        }

        long dau = dataService.calculateDAU(start, end);
        List<Long> dauData = dataService.getDAUChartData(start, end);

        return CommunityUtil.getJSONString(0, "success", Map.of("dauResult", dau, "dauData", dauData));
    }
}
