-- 如果redis中的线程标识(通过传入的key获取)与当前的线程标识(通过参数传入)相等
if(redis.call('get',KEYS[1]) == ARGV[1]) then
    return redis.call('del',KEYS[1])
end
return 0