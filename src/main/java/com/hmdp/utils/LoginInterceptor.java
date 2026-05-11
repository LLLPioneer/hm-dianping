package com.hmdp.utils;

import org.springframework.stereotype.Component;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginInterceptor implements HandlerInterceptor {



    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {

        if (UserHolder.getUser()==null){
            response.setStatus(401);
            return false;
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) throws Exception {
        //删除ThreadLocal中的user信息防止内存泄漏
        UserHolder.removeUser();
    }
}

//    private final StringRedisTemplate stringRedisTemplate;
//
//    @Autowired
//    public LoginInterceptor(StringRedisTemplate stringRedisTemplate){
//        this.stringRedisTemplate=stringRedisTemplate;
//    }

//        //HttpSession session = request.getSession();
//        //User user = (User) session.getAttribute("user");
//
//        //通过检验token判断登陆是否有效
//        String token = request.getHeader("authorization");
//        if (token==null){
//            response.setStatus(401);
//            return false;
//        }
//        Map<Object, Object> entries = stringRedisTemplate.opsForHash().entries(RedisConstants.LOGIN_USER_KEY + token);
//        if (entries.isEmpty()){
//            response.setStatus(401);
//            return false;
//        }
//
//        UserDTO userDTO = BeanUtil.fillBeanWithMap(entries,new UserDTO(),false);
//
//        //将user信息存入ThreadLocal
//        UserHolder.saveUser(userDTO);
//前一个拦截器已经完成了token的校验并将用户信息存入ThreadLocal,这个拦截器只需要检查ThreadLocal中是否有信息