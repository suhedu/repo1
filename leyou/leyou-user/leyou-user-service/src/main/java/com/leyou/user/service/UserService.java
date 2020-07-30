package com.leyou.user.service;

import com.leyou.common.utils.NumberUtils;
import com.leyou.user.mapper.UserMapper;
import com.leyou.user.pojo.User;
import com.leyou.user.utils.CodecUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Service;

import java.util.Date;
import java.util.HashMap;
import java.util.concurrent.TimeUnit;

@Service
public class UserService {

    @Autowired
    private UserMapper userMapper;

    @Autowired
    private AmqpTemplate amqpTemplate;

    @Autowired
    private StringRedisTemplate redisTemplate;

    private static final String KEY_PREFIX = "user:verify:";

    /**
     * 校验用户名或密码是否存在，type的值决定了data中是用户名或密码
     * @param data
     * @param type
     * @return
     */
    public Boolean checkUser(String data, Integer type) {
        User record = new User();
        if (type == 1){
            record.setUsername(data);
        } else if (type == 2){
            record.setPhone(data);
        }else {
            return null;
        }
        int count = this.userMapper.selectCount(record);
        return count == 0;
    }

    /**
     * 发送手机验证码
     * @param phone
     */
    public void sendVerifyCode(String phone) {
        if (StringUtils.isBlank(phone)){
            return;

        }
        //生成验证码
        String code = NumberUtils.generateCode(6);

        //发送消息到rabbitMQ
        HashMap<String, String> map = new HashMap<>();
        map.put("phone" , phone);
        map.put("code" , code);
        this.amqpTemplate.convertAndSend("LEYOU.SMS.EXCHANGE" , "sms.verify.code" , map);

        //保存验证码到redis
        this.redisTemplate.opsForValue().set(KEY_PREFIX + phone , code , 5 , TimeUnit.MINUTES);
    }

    /**
     * 注册
     * @param user
     * @param code
     */
    public void register(User user, String code) {

        //查询redis的验证码
        String redisCode = this.redisTemplate.opsForValue().get(KEY_PREFIX + user.getPhone());
        //校验验证码
        if (!StringUtils.equals(code , redisCode)){
            return;
        }
        //生成盐
        String salt = CodecUtils.generateSalt();
        user.setSalt(salt);
        //加盐加密
        String s = CodecUtils.md5Hex(user.getPassword(), salt);
        //将加密后的密码传给参数
        user.setPassword(s);
        //新增用户
        user.setId(null);
        user.setCreated(new Date());
        this.userMapper.insert(user);
        //删除验证码
    }

    /**
     * 根据用户名和密码查询用户
     * @param username
     * @param password
     * @return
     */
    public User queryUser(String username, String password) {
        // 查询
        User record = new User();
        record.setUsername(username);
        User user = this.userMapper.selectOne(record);
        // 校验用户名
        if (user == null) {
            return null;
        }
        // 校验密码
        if (!user.getPassword().equals(CodecUtils.md5Hex(password, user.getSalt()))) {
            return null;
        }
        // 用户名密码都正确
        return user;
    }
}
