package com.hmdp.utils;

public interface Lock {

    public boolean tryLock(String keyName,Long ttl);

    public void releaseLock(String keyName);


}
