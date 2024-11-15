# 网站数据统计

- UV - Unique Visitor
  - 独立访客，需要通过用户的`IP（匿名游客也要统计）`去重统计数据
  - 每次访问都要进行统计
  - 使用HyperLogLog，性能好，存储空间小
- DAU - Daily Active User
  - 日活跃用户，需要通过用户`ID（仅统计登录用户）`去重统计数据
  - 访问过一次 == 活跃
  - 使用Bitmap，性能好，且可以统计精确结果

## 代码实现

1. 定义RedisKey

在RedisKeyUtil中新增：

```java
private static final String PREFIX_UV = "uv";
private static final String PREFIX_DAU = "dau";

// 单日uv
public static String getUVKey(String date){
  return PREFIX_UV + SPLIT + date;
}

// 区间uv
public static String getUVKey(String startDate, String endDate){
  return PREFIX_UV + SPLIT + startDate + SPLIT + endDate;
}

// 单日dau
public static String getDAUKey(String date){
  return PREFIX_DAU + SPLIT + date;
}

// 区间dau
public static String getDAUKey(String startDate, String endDate){
  return PREFIX_DAU + SPLIT + startDate + SPLIT + endDate;
}
```

2. 和之前一样，redis部分不用单独写dao，直接写service即可

```java
package com.nowcoder.community.service;

import com.nowcoder.community.util.RedisKeyUtil;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessException;
import org.springframework.data.redis.connection.RedisConnection;
import org.springframework.data.redis.connection.RedisStringCommands;
import org.springframework.data.redis.core.RedisCallback;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

@Service
public class DataService {
    @Autowired
    private RedisTemplate redisTemplate;
    
    // 日期格式化形式
    private SimpleDateFormat df = new SimpleDateFormat("yyyyMMdd");
    
    // 在每次请求中，截获相关数据，计入redis
    // 并提供查询方法
    
    // uv - 将指定ip计入uv
    public void recordUV(String ip){
        String redisKey = RedisKeyUtil.getUVKey(df.format(new Date()));
        redisTemplate.opsForHyperLogLog().add(redisKey, ip);
    }
    
    // uv - 统计指定日期内的uv
    public long calculateUV(Date start, Date end){
        if(start == null || end == null){
            throw new IllegalArgumentException("参数不能为空！");
        }
        
        // 聚合 start - end 的key
        List<String> keyList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start); // 设置循环的初值
        while (!calendar.getTime().after(end)){  // 如果时间不晚于end
            String key = RedisKeyUtil.getUVKey(df.format(calendar.getTime()));
            keyList.add(key);
            calendar.add(Calendar.DATE, 1); // 日期 ++ 
        }
        String redisKey = RedisKeyUtil.getUVKey(df.format(start), df.format(end));
        redisTemplate.opsForHyperLogLog().union(redisKey, keyList.toArray());
        
        // 返回统计结果
        return redisTemplate.opsForHyperLogLog().size(redisKey);
    }
    
    // dau - 将指定id计入dau
    public void recordDAU(int userId){
        String redisKey = RedisKeyUtil.getDAUKey(df.format(new Date()));
        redisTemplate.opsForValue().setBit(redisKey, userId, true);
    }
    
    // dau - 统计指定日期内的dau
    public long calculateDAU(Date start, Date end){
        if(start == null || end == null){
            throw new IllegalArgumentException("参数不能为空！");
        }

        // 整理 start - end 的key，使用or运算统计（某段时间内登录一次就是活跃）
        List<byte[]> keyList = new ArrayList<>();
        Calendar calendar = Calendar.getInstance();
        calendar.setTime(start); // 设置循环的初值
        while (!calendar.getTime().after(end)){
            String key = RedisKeyUtil.getDAUKey(df.format(calendar.getTime()));
            keyList.add(key.getBytes());
            calendar.add(Calendar.DATE, 1);
        }
        return (long) redisTemplate.execute(new RedisCallback() {
            @Override
            public Object doInRedis(RedisConnection connection) throws DataAccessException {
                String redisKey = RedisKeyUtil.getDAUKey(df.format(start), df.format(end));
                connection.bitOp(RedisStringCommands.BitOperation.OR,
                        redisKey.getBytes(), keyList.toArray(new byte[0][0]));
                return connection.bitCount(redisKey.getBytes());
            }
        });
    }
}
```

3. 为了截获相关数据，需要写一个专门的拦截器

新建DataInterceptor

```java
package com.nowcoder.community.controller.interceptor;

import com.nowcoder.community.entity.User;
import com.nowcoder.community.service.DataService;
import com.nowcoder.community.util.HostHolder;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

@Component
public class DataInterceptor implements HandlerInterceptor {
    @Autowired
    private DataService dataService;
    
    @Autowired
    private HostHolder hostHolder;

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // uv
        String ip = request.getRemoteHost();
        dataService.recordUV(ip);
        
        // dau
        User user = hostHolder.getUser();
        if(user != null){
            dataService.recordDAU(user.getId());
        }
        
        return true; // 统计完成后放行请求
    }
}
```

在WebMvcConfig配置，使拦截器生效

```java
@Autowired
private DataInterceptor dataInterceptor;

registry.addInterceptor(dataInterceptor).
excludePathPatterns("/**/*.css", "/**/*.js", "/**/*.png", "/**/*.jpg","/**/*.jpeg");
```

4. controller

新建DataController

```java
package com.nowcoder.community.controller;

import com.nowcoder.community.service.DataService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;

import java.util.Date;

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
}
```

5. 前端 /site/admin/data.html

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org">

<link rel="stylesheet" th:href="@{/css/global.css}" />

<header class="bg-dark sticky-top" th:replace="index::header">

  <!-- 网站UV -->
  <div class="container pl-5 pr-5 pt-3 pb-3 mt-3">
    <h6 class="mt-3"><b class="square"></b> 网站 UV</h6>
    <form class="form-inline mt-3" method="post" th:action="@{/data/uv}">
      <input type="date" class="form-control" required name="start" th:value="${#dates.format(uvStartDate, 'yyyy-MM-dd')}"/>
      <input type="date" class="form-control ml-3" required name="end" th:value="${#dates.format(uvEndDate, 'yyyy-MM-dd')}"/>
      <button type="submit" class="btn btn-primary ml-3">开始统计</button>
    </form>
    <ul class="list-group mt-3 mb-3">
      <li class="list-group-item d-flex justify-content-between align-items-center">
        统计结果
        <span class="badge badge-primary badge-danger font-size-14" th:text="${uvResult}">0</span>
      </li>
    </ul>
  </div>
  <!-- 活跃用户 -->
  <div class="container pl-5 pr-5 pt-3 pb-3 mt-4">
    <h6 class="mt-3"><b class="square"></b> 活跃用户</h6>
    <form class="form-inline mt-3" method="post" th:action="@{/data/dau}">
      <input type="date" class="form-control" required name="start" th:value="${#dates.format(dauStartDate, 'yyyy-MM-dd')}"/>
      <input type="date" class="form-control ml-3" required name="end" th:value="${#dates.format(dauEndDate, 'yyyy-MM-dd')}"/>
      <button type="submit" class="btn btn-primary ml-3">开始统计</button>
    </form>
    <ul class="list-group mt-3 mb-3">
      <li class="list-group-item d-flex justify-content-between align-items-center">
        统计结果
        <span class="badge badge-primary badge-danger font-size-14" th:text="${dauResult}">0</span>
      </li>
    </ul>
  </div>
  </div>

<script th:src="@{/js/global.js}"></script>
```

6. 权限设置——管理员

在SecurityConfig中配置：

```java
.requestMatchers(
                        "/discuss/delete",
                        "/data/**"
                ).hasAnyAuthority(
                        AUTHORITY_ADMIN
                )
```

7. 给前端加个“网站数据统计”按钮，否则admin想访问数据统计页面必须敲url，非常不方便

在index的导航栏新增：

```html
<html lang="en" xmlns:th="http://www.thymeleaf.org" xmlns:sec="http://www.thymeleaf.org/extras/spring-security">

<li class="nav-item ml-3 btn-group-vertical" sec:authorize="hasAnyAuthority('admin')">
  <a class="nav-link" th:href="@{/data}">网站数据统计</a>
</li>
```

# 数据统计功能改进——可视化图表

现在统计的结果比较寒酸，我们用折线图展示dau和uv，使用echarts包

1. DataService返回多日数据列表

```java
public List<Long> getUVChartData(Date start, Date end) {
  List<Long> uvData = new ArrayList<>();
  Calendar calendar = Calendar.getInstance();
  calendar.setTime(start);

  while (!calendar.getTime().after(end)) {
    String key = RedisKeyUtil.getUVKey(df.format(calendar.getTime()));
    uvData.add(redisTemplate.opsForHyperLogLog().size(key));
    calendar.add(Calendar.DATE, 1);
  }
  return uvData;
}

public List<Long> getDAUChartData(Date start, Date end) {
  if (start == null || end == null) {
    throw new IllegalArgumentException("参数不能为空！");
  }

  List<Long> dauData = new ArrayList<>();
  Calendar calendar = Calendar.getInstance();
  calendar.setTime(start);

  while (!calendar.getTime().after(end)) {
    String key = RedisKeyUtil.getDAUKey(df.format(calendar.getTime()));
    Long dailyActiveCount = (Long) redisTemplate.execute(new RedisCallback<Long>() {
      @Override
      public Long doInRedis(RedisConnection connection) throws DataAccessException {
        return connection.bitCount(key.getBytes());
      }
    });
    if (dailyActiveCount == null) {
      dailyActiveCount = 0L;
    }
    dauData.add(dailyActiveCount);
    calendar.add(Calendar.DATE, 1);
  }
  return dauData;
}
```

2. DataController改动：同时显示总数和图表

```java
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
```

3. data.html最终版：

```html
<!doctype html>
<html lang="en" xmlns:th="http://www.thymeleaf.org">
<head>
	<meta charset="utf-8">
	<meta name="viewport" content="width=device-width, initial-scale=1, shrink-to-fit=no">
	<meta name="_csrf" th:content="${_csrf.token}">
	<meta name="_csrf_header" th:content="${_csrf.headerName}">
	<link rel="icon" th:href="@{/img/icon.png}"/>
	<link rel="stylesheet" type="text/css" th:href="@{/css/bootstrap.min.css}" />
	<link rel="stylesheet" th:href="@{/css/global.css}" />
	<title>数据统计</title>
</head>
<body>
<div class="nk-container">
	<!-- 头部 -->
	<header class="bg-dark sticky-top" th:replace="index::header">
	</header>

	<!-- 内容 -->
	<div class="main">
		<!-- 网站UV -->
		<div class="container pl-5 pr-5 pt-3 pb-3 mt-3">
			<h6 class="mt-3"><b class="square"></b> 网站 UV</h6>
			<form class="form-inline mt-3" method="post">
				<input type="date" class="form-control" required name="start" th:value="${#dates.format(uvStartDate, 'yyyy-MM-dd')}" id="uvStartDate" />
				<input type="date" class="form-control ml-3" required name="end" th:value="${#dates.format(uvEndDate, 'yyyy-MM-dd')}" id="uvEndDate" />
				<button type="button" class="btn btn-primary ml-3" id="uvButton">开始统计</button>
			</form>
			<ul class="list-group mt-3 mb-3">
				<li class="list-group-item d-flex justify-content-between align-items-center">
					统计结果
					<span class="badge badge-primary badge-danger font-size-14" id="uvResult">0</span>
				</li>
			</ul>
			<div id="uv-chart" style="width: 100%;height:400px;"></div>
		</div>

		<div class="container pl-5 pr-5 pt-3 pb-3 mt-4">
			<h6 class="mt-3"><b class="square"></b> 活跃用户</h6>
			<form class="form-inline mt-3" method="post">
				<input type="date" class="form-control" required name="start" th:value="${#dates.format(dauStartDate, 'yyyy-MM-dd')}" id="dauStartDate" />
				<input type="date" class="form-control ml-3" required name="end" th:value="${#dates.format(dauEndDate, 'yyyy-MM-dd')}" id="dauEndDate" />
				<button type="button" class="btn btn-primary ml-3" id="dauButton">开始统计</button>
			</form>
			<ul class="list-group mt-3 mb-3">
				<li class="list-group-item d-flex justify-content-between align-items-center">
					统计结果
					<span class="badge badge-primary badge-danger font-size-14" id="dauResult">0</span>
				</li>
			</ul>
			<div id="dau-chart" style="width: 100%;height:400px;"></div>
		</div>
	</div>

	<!-- 尾部 -->
	<footer class="bg-dark" th:replace="index::footer">
	</footer>
</div>

<script th:src="@{/js/jquery-3.1.0.min.js}"></script>
<script type="module" th:src="@{/js/popper.min.js}"></script>
<script th:src="@{/js/bootstrap.min.js}"></script>
<script th:src="@{/js/global.js}"></script>
<script th:src="@{/js/echarts.min.js}"></script>
<script th:inline="javascript">
	$(document).ready(function () {
		var uvChart = echarts.init(document.getElementById('uv-chart'));
		var dauChart = echarts.init(document.getElementById('dau-chart'));

		let token = $("meta[name= '_csrf']").attr("content");
		let header = $("meta[name= '_csrf_header']").attr("content");
		$(document).ajaxSend(function (e, xhr, options){
			xhr.setRequestHeader(header, token);
		});

		// 验证日期
		function validateDates(start, end) {
			return new Date(start) <= new Date(end);
		}

		// 加载 UV 图表和总数
		function loadUVChart(start, end) {
			if (!validateDates(start, end)) {
				alert("开始日期不能晚于结束日期！");
				return;
			}
			$.post(
					CONTEXT_PATH + "/data/uv",
					{"start": start, "end": end},
					function (response) {
						var dates = getDates(start, end);
						response = JSON.parse(response);
						uvChart.setOption({
							title: { text: '网站 UV 统计' },
							tooltip: { trigger: 'axis' },
							xAxis: { type: 'category', data: dates },
							yAxis: { type: 'value' },
							series: [{ data: response["uvData"], type: 'line' }]
						});
						uvChart.resize(); // 调整图表大小
						// 更新总数
						$("#uvResult").text(response["uvResult"]);
					}
			).fail(function(jqXHR, textStatus, errorThrown) {
				console.log("AJAX 请求失败:", textStatus, errorThrown);
			});
		}

		// 加载 DAU 图表和总数
		function loadDAUChart(start, end) {
			if (!validateDates(start, end)) {
				alert("开始日期不能晚于结束日期！");
				return;
			}
			$.post(
					CONTEXT_PATH + "/data/dau",
					{"start": start, "end": end},
					function (response) {
						var dates = getDates(start, end);
						response = JSON.parse(response)
						dauChart.setOption({
							title: { text: '活跃用户统计' },
							tooltip: { trigger: 'axis' },
							xAxis: { type: 'category', data: dates },
							yAxis: { type: 'value' },
							series: [{ data: response["dauData"], type: 'line' }]
						});
						dauChart.resize(); // 调整图表大小
						// 更新总数
						$("#dauResult").text(response["dauResult"]);
					}
			).fail(function(jqXHR, textStatus, errorThrown) {
				console.log("AJAX 请求失败:", textStatus, errorThrown);
			});
		}

		// 获取日期数组
		function getDates(start, end) {
			var dates = [];
			var currentDate = new Date(start);
			var endDate = new Date(end);
			while (currentDate <= endDate) {
				dates.push(currentDate.toISOString().split('T')[0]);
				currentDate.setDate(currentDate.getDate() + 1);
			}
			return dates;
		}

		// 按钮点击事件绑定
		$("#uvButton").click(function () {
			var uvStartDate = $("#uvStartDate").val();
			var uvEndDate = $("#uvEndDate").val();
			loadUVChart(uvStartDate, uvEndDate);
		});

		$("#dauButton").click(function () {
			var dauStartDate = $("#dauStartDate").val();
			var dauEndDate = $("#dauEndDate").val();
			loadDAUChart(dauStartDate, dauEndDate);
		});
	});
</script>
</body>
</html>
```