package com.example.campusworkorder.security;

import com.example.campusworkorder.entity.AppUser;
import com.example.campusworkorder.mapper.AppUserMapper;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class AppUserDetailsService implements UserDetailsService {
    private final AppUserMapper appUserMapper;
    public AppUserDetailsService(AppUserMapper appUserMapper) {
        this.appUserMapper = appUserMapper;
    }

    @Override
    public UserDetails loadUserByUsername(String username){
        AppUser appUser=appUserMapper.selectByUsername(username);
        if(appUser==null)
            throw new UsernameNotFoundException("user not found: "+username);
        return new AppUserPrincipal(
                appUser.getId(),
                appUser.getUsername(),
                appUser.getPasswordHash(),
                appUser.getRole()
        );
    }

}
