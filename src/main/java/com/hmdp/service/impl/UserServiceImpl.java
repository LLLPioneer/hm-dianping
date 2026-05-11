package com.hmdp.service.impl;

import cn.hutool.core.bean.BeanUtil;
import cn.hutool.core.bean.copier.CopyOptions;
import cn.hutool.core.lang.UUID;
import cn.hutool.core.util.RandomUtil;
import com.baomidou.mybatisplus.extension.service.impl.ServiceImpl;
import com.hmdp.dto.LoginFormDTO;
import com.hmdp.dto.Result;
import com.hmdp.dto.UserDTO;
import com.hmdp.entity.User;
import com.hmdp.mapper.UserMapper;
import com.hmdp.service.IUserService;
import com.hmdp.utils.RedisConstants;
import com.hmdp.utils.RegexUtils;
import com.hmdp.utils.SystemConstants;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.BeanUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import javax.servlet.http.HttpSession;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.TimeUnit;

/**
 * <p>
 * 服务实现类
 * </p>
 *
 * @author 虎哥
 * @since 2021-12-22
 */

@Service
@Slf4j
public class UserServiceImpl extends ServiceImpl<UserMapper, User> implements IUserService {

    private StringRedisTemplate stringRedisTemplate;


    @Autowired
    public UserServiceImpl(StringRedisTemplate stringRedisTemplate){
        this.stringRedisTemplate = stringRedisTemplate;
    }


    @Override
    public Result sendCode(String phone, HttpSession session) {
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(SystemConstants.PHONE_ONT_INVALID);
        }
        String code = RandomUtil.randomNumbers(6);
        //保存在session中
        //session.setAttribute("code",code);
        //保存在redis中
        stringRedisTemplate.opsForValue().set(RedisConstants.LOGIN_CODE_KEY+phone,code,RedisConstants.LOGIN_CODE_TTL, TimeUnit.MINUTES);
        log.debug("模拟短信验证码发送:{}",code);
        return Result.ok();
    }

    @Override
    public Result loginService(LoginFormDTO loginForm, HttpSession session) {
        String phone = loginForm.getPhone();
        String code = loginForm.getCode();

        //校验手机号是否合法
        if(RegexUtils.isPhoneInvalid(phone)){
            return Result.fail(SystemConstants.PHONE_ONT_INVALID);
        }
        //校验验证码
        //因为session是基于cookie的,每次请求都会在cookie中带上sessionID,所以不需要判断code和phone是否匹配
        //String cacheCode = (String) session.getAttribute("code");
        String cacheCode = stringRedisTemplate.opsForValue().get(RedisConstants.LOGIN_CODE_KEY+phone);
        if (cacheCode==null||!cacheCode.equals(code)){
            return Result.fail(SystemConstants.CODE_ERROR);
        }

        //校验通过查询用户是否注册,已注册返回ok,未注册自动注册返回ok,并将用户数据存入session
        User user = query().eq("phone",phone).one();
        if (user==null){
            user = User.builder()
                    .phone(phone)
                    .nickName(SystemConstants.USER_NICK_NAME_PREFIX + RandomUtil.randomString(10))
                    .build();
            save(user);
        }
        //session.setAttribute("user",user);
        //将用户dto存入redis中并发token设置有效期
        UserDTO userDTO = BeanUtil.copyProperties(user,UserDTO.class);
        String token = UUID.randomUUID().toString(true);
        stringRedisTemplate.opsForHash().putAll(RedisConstants.LOGIN_USER_KEY + token,
                BeanUtil.beanToMap(userDTO,new HashMap<>(), CopyOptions.create().setIgnoreNullValue(true).setFieldValueEditor((fieldName,fieldVale)-> fieldVale.toString())));
        stringRedisTemplate.expire(RedisConstants.LOGIN_USER_KEY + token,RedisConstants.LOGIN_USER_TTL,TimeUnit.MINUTES);

        return Result.ok(token);
    }


}
