package com.nowcoder.community.controller;

import com.nowcoder.community.service.DataService;
import com.nowcoder.community.util.CommunityUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
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
        return "/site/admin/data";
    }

    // 统计uv
    @RequestMapping(path = "/data/uv", method = RequestMethod.POST)
    public String getUV(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,   // 指定日期格式
                        @DateTimeFormat(pattern = "yyyy-MM-dd")Date end, Model model){
        long uv = dataService.calculateUV(start, end);
        model.addAttribute("uvResult", uv);

        // 返回/site/admin/data时，要保留查询的条件start和end
        model.addAttribute("uvStartDate", start);
        model.addAttribute("uvEndDate", end);

        return "forward:/data";  // forward:声明我们当前这个方法只能处理一半的请求，剩余的请求交由/data处理，即返回data页面
    }

    // 统计dau
    @RequestMapping(path = "/data/dau", method = RequestMethod.POST)
    public String getDAU(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                        @DateTimeFormat(pattern = "yyyy-MM-dd")Date end, Model model){
        long dau = dataService.calculateDAU(start, end);
        model.addAttribute("dauResult", dau);

        model.addAttribute("dauStartDate", start);
        model.addAttribute("dauEndDate", end);

        return "forward:/data";
    }

    @RequestMapping(path = "/data/uv/chart", method = RequestMethod.POST)
    @ResponseBody
    public String getUVChartData(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                                 @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        List<Long> uvData = dataService.getUVChartData(start, end);
        return CommunityUtil.getJSONString(0, "success", Map.of("uvData", uvData));
    }

    // 获取 DAU 数据
    @RequestMapping(path = "/data/dau/chart", method = RequestMethod.POST)
    @ResponseBody
    public String getDAUChartData(@DateTimeFormat(pattern = "yyyy-MM-dd") Date start,
                                  @DateTimeFormat(pattern = "yyyy-MM-dd") Date end) {
        List<Long> dauData = dataService.getDAUChartData(start, end);
        System.out.println(dauData);
        return CommunityUtil.getJSONString(0, "success", Map.of("dauData", dauData));
    }
}
