package jkorpics.util.concurrent.locks;


import org.junit.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.function.Predicate;

import static org.junit.Assert.*;

/**
 * Created by jkorpics on 3/18/17.
 */
public class BoundedReadWriteLockTest {

    public static abstract class LockClient implements Runnable {
        public volatile boolean haveReadLock = Boolean.FALSE;
        public volatile boolean haveWriteLock = Boolean.FALSE;
        public volatile boolean running = Boolean.FALSE;
        private BoundedReadWriteLock lock;

        public LockClient(BoundedReadWriteLock lock) {
            this.lock = lock;
        }

        public void read() {
            try {
                lock.acquireRead();
                haveReadLock = Boolean.TRUE;
            } catch (InterruptedException e) {
                return;
            }
        }

        public void write() {
            try {
                lock.acquireWrite();
                haveWriteLock = Boolean.TRUE;
            } catch (InterruptedException e) {
                return;
            }
        }

        public void stopRead() {
            lock.releaseRead();
            haveReadLock = Boolean.FALSE;
        }

        public void stopWrite() {
            lock.releaseWrite();
            haveWriteLock = Boolean.FALSE;
        }
    }

    private static List<LockClient> filterReadLockHolders(List<LockClient> lockClients) {
        List<LockClient> holders = new ArrayList<LockClient>();
        for (LockClient client : lockClients) {
            if (client.haveReadLock) {
                holders.add(client);
            }
        }
        return holders;
    }

    private static List<LockClient> getReadClients(final int count,
                                                   final CountDownLatch latch,
                                                   final BoundedReadWriteLock lock) {
        List<LockClient> clients = new ArrayList<LockClient>();
        for (int i = 0; i < count; i++) {
            LockClient client = new LockClient(lock) {
                public void run() {
                    try {
                        latch.countDown();
                        latch.await();
                        running = Boolean.TRUE;
                        read();
                        while (haveReadLock) {
                            Thread.sleep(2000);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running = Boolean.FALSE;
                }
            };
            clients.add(client);
        }
        return clients;
    }

    private static List<LockClient> getWriteClients(final int count,
                                                    final CountDownLatch latch,
                                                    final BoundedReadWriteLock lock) {
        List<LockClient> clients = new ArrayList<LockClient>();
        for (int i = 0; i < count; i++) {
            LockClient client = new LockClient(lock) {
                public void run() {
                    try {
                        latch.countDown();
                        latch.await();
                        running = Boolean.TRUE;
                        write();
                        while (haveWriteLock) {
                            Thread.sleep(2000);
                        }
                    } catch (InterruptedException e) {
                        Thread.currentThread().interrupt();
                    }
                    running = Boolean.FALSE;
                }
            };
            clients.add(client);
        }
        return clients;
    }

    @Test
    public void testLockCountLowerBound() {
        // test that lock counts dont go below 0
        BoundedReadWriteLock lock = new BoundedReadWriteLock(4);
        lock.releaseRead();
        lock.releaseRead();
        lock.releaseRead();
        lock.releaseRead();
        assertEquals("read lock count should not go below 0", 0, lock.getNumReadLocksIssued());
    }

    @Test
    public void testReset() throws InterruptedException {
        // test that lock counts dont go below 0
        BoundedReadWriteLock lock = new BoundedReadWriteLock(4);
        lock.acquireWrite();
        assertTrue(lock.isWriteLockIssued());
        lock.reset();
        assertFalse("Reset should reset write lock count", lock.isWriteLockIssued());
        lock.acquireRead();
        lock.acquireRead();
        assertEquals(2, lock.getNumReadLocksIssued());
        lock.reset();
        assertEquals("Reset should reset read lock count", 0, lock.getNumReadLocksIssued());
    }

    @Test
    public void getConcurrentReadLockRequests() throws InterruptedException {
        int maxConcurrentReads = 4;
        int numThreads = 7;
        final BoundedReadWriteLock lock = new BoundedReadWriteLock(maxConcurrentReads);

        List<LockClient> lockClients = getReadClients(numThreads, new CountDownLatch(numThreads), lock);
        startClients(lockClients, Executors.newFixedThreadPool(numThreads));

        // wait for max number of read locks to be issued
        waitForReadLocksIssued(maxConcurrentReads, lock);
        verifyNumReadHolders(maxConcurrentReads, lockClients);

        // release and 4 other threads should have locks.
        releaseReads(lock, lockClients);
        waitForReadLocksIssued(3, lock);

        // verify remainder of lock clients have gotten read locks
        verifyNumReadHolders(3, lockClients);
    }

    @Test
    public void testConcurrentWriteRequest() throws InterruptedException {
        int maxConcurrentReads = 4;
        int numWriteClients = 1;
        int numReadClients = 99;
        int numThreads = numReadClients + numWriteClients;

        // countdown latch that will release once all threads are present
        final CountDownLatch latch = new CountDownLatch(numReadClients);

        final BoundedReadWriteLock lock = new BoundedReadWriteLock(maxConcurrentReads);
        List<LockClient> readClients = getReadClients(numReadClients, latch, lock);
        LockClient writeClient = getWriteClients(1, latch, lock).get(0);


        // get all the read lock clients running.
        Executor executor = Executors.newFixedThreadPool(numThreads);
        startClients(readClients, executor);

        // let all 4 read locks be issued and then start write client
        waitForReadLocksIssued(maxConcurrentReads, lock);

        // start the write client
        executor.execute(writeClient);
        waitForRunning(writeClient);

        // verify only reads
        verifyReadsOnly(lock, readClients, writeClient);

        // release the reads and verify write client has been given write lock
        releaseReads(lock, readClients);
        waitForReadLocksIssued(0, lock);

        // now see if write lock is issued
        waitForWriteLockIssued(lock);
        verifyWriteOnly(lock, readClients, writeClient);

        // release the write lock and verify read locks issued again
        writeClient.stopWrite();
        waitForReadLocksIssued(4, lock);
        verifyReadsOnly(lock, readClients, writeClient);
    }

    private void startClients(List<LockClient> clients, Executor executor) {
        for (LockClient client : clients) {
            executor.execute(client);
        }
    }

    private void verifyReadsOnly(BoundedReadWriteLock lock, List<LockClient> readClients, LockClient writeClient) {
        List<LockClient> holders = filterReadLockHolders(readClients);
        assertEquals("Dont have the correct number of read locks", lock.getMaxReadLocks(), holders.size());
        assertFalse("Write lock should not be held when there are outstanding read locks", writeClient.haveWriteLock);
    }

    private void verifyWriteOnly(BoundedReadWriteLock lock, List<LockClient> readClients, LockClient writeClient) {
        List<LockClient> holders = filterReadLockHolders(readClients);
        assertEquals("Dont have the correct number of read locks", 0, holders.size());
        assertTrue("Write lock should not be held when there are outstanding read locks", writeClient.haveWriteLock);
    }

    private void releaseReads(BoundedReadWriteLock lock, List<LockClient> readClients) {
        List<LockClient> holders = filterReadLockHolders(readClients);
        for (LockClient holder : holders) {
            holder.stopRead();
        }
    }

    private void waitForRunning(final LockClient client) {
        waitUntil(x -> client.running);
    }

    private void waitForWriteLockIssued(final BoundedReadWriteLock lock) {
        waitUntil((x) -> lock.isWriteLockIssued());
        assertTrue("Write lock expected to be issued", lock.isWriteLockIssued());
    }

    private void waitForReadLocksIssued(final int numReadLocks, final BoundedReadWriteLock lock) {
        waitUntil((x) -> lock.getNumReadLocksIssued() == numReadLocks);
        assertEquals(numReadLocks, lock.getNumReadLocksIssued());
    }

    private void verifyNumReadHolders(int numExpectedReadHolders, List<LockClient> clients) {
        List<LockClient> holders = filterReadLockHolders(clients);
        assertEquals("Dont have the correct number of read locks", numExpectedReadHolders, holders.size());
    }

    private void waitUntil(Predicate<Void> predicate) {
        long maxWaitTime = 5000L;
        long interval = 50L;
        long total = 0L;
        while(total < maxWaitTime && ! predicate.test(null)) {
            try {
                total += interval;
                Thread.sleep(interval);
            } catch(InterruptedException e) {
                total = maxWaitTime;
            }
        }
    }

}