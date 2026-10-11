package com.example.campusworkorder.mapper;

import com.example.campusworkorder.domain.enums.UserRole;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;

@Mapper
public interface AppUserMapper {
    UserRole selectRoleById(@Param("id") Long id);
}
