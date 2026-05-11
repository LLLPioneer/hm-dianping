-- 获取优惠券id和用户id
local voucherId = ARGV[1]
local userId = ARGV[2]

-- 创建库存key
local stockKey = 'stock:seckillvoucher:' .. voucherId
-- 创建订单key
local orderKey = 'order:seckillvoucher:' .. voucherId

-- 判断库存是否充足
local stock = redis.call('get',stockKey)
if(not stock or tonumber(stock) <= 0) then
    return 1
end

-- 检查是否重复下单
if(redis.call('sismember',orderKey,userId) == 1) then
    return 2
end

-- 扣减库存并下单
redis.call('incrby',stockKey,-1)
redis.call('sadd',orderKey,userId)

return 0



