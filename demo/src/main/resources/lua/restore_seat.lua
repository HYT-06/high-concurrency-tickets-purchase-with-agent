-- KEYS[1]: 座位库存的 Redis key，格式 seat:stock:{seatId}
-- ARGV[1]: 恢复数量（固定传 1）

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1  -- key 不存在
end
redis.call('INCRBY', KEYS[1], tonumber(ARGV[1]))
return 1       -- 恢复成功
