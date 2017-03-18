package jkorpics.util.concurrent.locks;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.Condition;
import java.util.concurrent.locks.ReentrantLock;


/**
 * Wrapper around ReentrantLock that sets an upper bound on the number of ReadLocks handed out at any given time.
 * WriteLocks take priority, so no new ReadLocks are handed out if any thread is waiting for a WriteLock.
 * Only one WriteLock at a time is issued.  THe number of max read locks can be updated duriing runtime.  Reducing
 * the max number of read locks has no effect on the number of ReadLocks held at the time.
 * Created by jkorpics on 3/18/17.
 */

public class BoundedReadWriteLock {

    private int maxReadLocks;
    private final ReentrantLock lock;
    private final Condition writeCondition;
    private final Condition readCondition;

    private AtomicInteger numReadLocksIssued;
    private boolean writeLockIssued;
    private int writesWaiting;

    public BoundedReadWriteLock(final int maxReadLocks) {
        this.maxReadLocks = maxReadLocks > 0 ? maxReadLocks : 0;
        this.numReadLocksIssued = new AtomicInteger(0);
        this.writeLockIssued = Boolean.FALSE;
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
        return numReadLocksIssued.get();
    }

    public boolean isWriteLockIssued() {
        return writeLockIssued;
    }

    public void reset() {
        lock.lock();
        numReadLocksIssued.set(0);
        writeLockIssued = Boolean.FALSE;
        writesWaiting = 0;
        lock.unlock();
    }

    public void acquireWrite() throws InterruptedException {
        lock.lock();
        try {
            while (writeLockIssued || numReadLocksIssued.get() > 0) {
                writesWaiting++;
                writeCondition.await();
                writesWaiting--;
            }
            writeLockIssued = Boolean.TRUE;
        } finally {
            lock.unlock();
        }
    }

    public void acquireRead() throws InterruptedException {
        lock.lock();
        try {
            while (writesWaiting > 0 || writeLockIssued || numReadLocksIssued.get() >= maxReadLocks) {
                readCondition.await();
            }
            numReadLocksIssued.incrementAndGet();
        } finally {
            lock.unlock();
        }
    }

    public void releaseWrite() {
        lock.lock();
        try {
            writeLockIssued = Boolean.FALSE;
            writeCondition.signal();
            readCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }

    public void releaseRead() {
        lock.lock();
        try {
            if (0 > numReadLocksIssued.decrementAndGet()) {
                numReadLocksIssued.incrementAndGet();
            }
            writeCondition.signal();
            readCondition.signalAll();
        } finally {
            lock.unlock();
        }
    }


}

