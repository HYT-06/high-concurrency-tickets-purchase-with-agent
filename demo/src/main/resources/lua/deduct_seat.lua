-- KEYS[1]: 座位库存的 Redis key，格式 seat:stock:{seatId}
-- ARGV[1]: 扣减数量（固定传 1）

local stock = tonumber(redis.call('GET', KEYS[1]))
if stock == nil then
    return -1  -- 库存 key 不存在，需要先预热
end
if stock <= 0 then
    return 0   -- 库存不足
end
redis.call('DECRBY', KEYS[1], tonumber(ARGV[1]))
return 1       -- 扣减成功
