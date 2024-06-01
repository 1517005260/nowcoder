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

}
