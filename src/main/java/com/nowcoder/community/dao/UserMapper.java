package com.nowcoder.community.dao;

import com.nowcoder.community.entity.User;
import jakarta.jws.soap.SOAPBinding;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface UserMapper {
    //业务需要什么写什么

    User selectById(int id);

    User selectByName(String username);

    User selectByEmail(String email);

    int insertUser(User user);  //返回修改了几行记录

    int updateStatus(int id, int status);

    int updateHeader(int id, String headerUrl);

    int updatePassword(int id, String password);

    int updateUsername(int id, String username);

    int updateType(int id, int type);
}
