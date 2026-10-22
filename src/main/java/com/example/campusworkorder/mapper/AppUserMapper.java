package com.example.campusworkorder.mapper;

import com.example.campusworkorder.domain.enums.UserRole;
import com.example.campusworkorder.entity.AppUser;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper {
    UserRole selectRoleById(@Param("id") Long id);
    AppUser selectByUsername(@Param("username") String username);
}
