package com.hyt.test.demo.common;

/**
 * 缓存 key 统一命名，避免各处手拼字符串导致写入与淘汰对不上。
 */
public final class CacheKeys {

    /** 车次详情缓存（TrainServiceImpl 手写 L1+L2） */
    private static final String TRAIN = "train:";
    /** 座位库存，扣减的事实来源，永不过期 */
    private static final String SEAT_STOCK = "seat:stock:";
    /** 车次缓存重建互斥锁 */
    private static final String TRAIN_LOCK = "lock:train:";

    private CacheKeys() {
    }

    public static String train(Long trainId) {
        return TRAIN + trainId;
    }

    public static String seatStock(Long seatId) {
        return SEAT_STOCK + seatId;
    }

    public static String trainLock(Long trainId) {
        return TRAIN_LOCK + trainId;
    }
}