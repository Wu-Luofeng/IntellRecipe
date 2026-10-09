-- 秒杀放弃补偿：归还库存 + 移除已购记录（必须原子——半补偿比不补偿更糟）
-- ARGV[1] = voucherId, ARGV[2] = userId
-- 库存 key 不存在（Redis 整体丢失）时只 SREM 不 INCRBY：
--   下次秒杀会从 DB 重新初始化库存，DB 值已是准的，避免凭空造出 "1"

local stockKey = 'seckill:stock:' .. ARGV[1]
local orderKey = 'seckill:order:' .. ARGV[1]
local userId   = ARGV[2]

if redis.call('exists', stockKey) == 1 then
    redis.call('incrby', stockKey, 1)
end

redis.call('srem', orderKey, userId)
return 1
