package jkorpics.util.concurrent.locks;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Wrapper around ReentrantLock that sets an upper bound on the number of ReadLocks handed out at any given time.
 * WriteLocks take priority, so no new ReadLocks are handed out if any thread is polling for a WriteLock.
 * Only one WriteLock at a time is issued.  THe number of max read locks can be updated duriing runtime.  Reducing
 * the max number of read locks has no effect on the number of ReadLocks held at the time.
 * Created by jkorpics on 3/18/17.
 */

public class BoundedReadWriteLock {

    private final ReentrantLock lock;
    private final Condition writeCondition;
    private final Condition readCondition;

    private int maxReadLocks;
    private int writesWaiting;
    private boolean writeLockIssued;
    private int readLockCount;
    private ThreadLockCounter threadReadLockCount;
    private ThreadLockCounter threadWriteLockCount;


    public BoundedReadWriteLock(final int maxReadLocks) {
        this.maxReadLocks = maxReadLocks > 0 ? maxReadLocks : 0;
        this.writeLockIssued = false;
        this.readLockCount = 0;
        this.threadReadLockCount = new ThreadLockCounter();
        this.threadWriteLockCount = new ThreadLockCounter();
        this.lock = new ReentrantLock();
        writeCondition = lock.newCondition();
        readCondition = lock.newCondition();
    }

    public int getMaxReadLocks() {
        return maxReadLocks;
    }

    public void setMaxReadLocks(final int maxReadLocks) {
        lock.lock();
        this.maxReadLocks = maxReadLocks > 0 ? maxReadLocks : 0;
        readCondition.signalAll();
        lock.unlock();
    }

    public int getNumReadLocksIssued() {
        return readLockCount;
    }

    public boolean isReadLockIssued() { return readLockCount > 0; }

    public boolean isWriteLockIssued() {
        return writeLockIssued;
    }

    public void reset() {
        lock.lock();
        readLockCount = 0;
        writesWaiting = 0;
        writeLockIssued = false;
        threadReadLockCount = new ThreadLockCounter();
        threadWriteLockCount = new ThreadLockCounter();
        lock.unlock();
    }

    public void acquireWrite() throws InterruptedException {
        lock.lock();
        try {
            writesWaiting++;
            while (! canIssueWrite()) {
                writeCondition.await();
            }
            increment(threadWriteLockCount);
            writeLockIssued = true;
        } finally {
            writesWaiting--;
            lock.unlock();
        }
    }

    public void acquireRead() throws InterruptedException {
        lock.lock();
        try {
            while (! canIssueRead() )   {
                readCondition.await();
            }
            if(increment(threadReadLockCount) == 1) {
                readLockCount++;
            }
        } finally {
            lock.unlock();
        }
    }

    public void releaseWrite() {
        lock.lock();
        try {
            if(decrement(threadWriteLockCount) == 0) {
                writeLockIssued = false;
                writeCondition.signal();
                readCondition.signalAll();
            }
        } finally {
            lock.unlock();
        }
    }

    public void releaseRead() {
        lock.lock();
        try{
            if(decrement(threadReadLockCount) == 0) {
                if(readLockCount > 0) {
                    readLockCount--;
                    writeCondition.signal();
                    readCondition.signalAll();
                }
            }

        } finally {
            lock.unlock();
        }
    }

    private boolean canIssueRead() {
        return holdsReadLock() || (isReadLockAvailable() && !blockForWrite());
    }

    private boolean canIssueWrite() {
        return holdsWriteLock() || ! (isReadLockIssued() || isWriteLockIssued());
    }

    private boolean holdsReadLock() {
        return threadReadLockCount.get().intValue() > 0;
    }

    private boolean holdsWriteLock() {
        return threadWriteLockCount.get().intValue() > 0;
    }

    private boolean blockForWrite() {
        return writesWaiting > 0 || isWriteLockIssued();
    }

    private boolean isReadLockAvailable() {
        return readLockCount < maxReadLocks;
    }

    private int increment(final ThreadLockCounter counter) {
        int i = counter.get().intValue();
        i++;
        counter.set(i);
        return i;
    }

    private int decrement(final ThreadLockCounter counter) {
        int i = counter.get().intValue();
        if(i > 0)
            i--;
        return i;
    }

    private static class ThreadLockCounter extends ThreadLocal<Integer> {
        @Override
        protected Integer initialValue() {
            return 0;
        }
    }


}

