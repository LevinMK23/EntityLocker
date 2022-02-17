package com.home.tasks;

import java.util.concurrent.TimeUnit;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EntityLockerTest {

    @Test
    @DisplayName("Try lock entity from 2 threads")
    void testConcurrentLock() throws InterruptedException {

        EntityLocker<Integer> locker = new EntityLocker<>();

        int entity = 1;

        Thread thread1 = new Thread(() -> locker.lock(entity));
        Thread thread2 = new Thread(() -> locker.lock(entity));

        thread1.start();
        thread2.start();
        thread1.join();
        thread1.join();

        LockState state = locker.getState(1);
        Assertions.assertEquals(LockState.LOCKED, state);

    }

    @Test
    @DisplayName("Lock and unlock entities any times")
    void testLockUnlock() {

        EntityLocker<Integer> locker = new EntityLocker<>();

        int[] entities = IntStream.range(1, 5).toArray();

        for (int i = 0; i < 3; i++) {
            locker.lock(entities[i]);
        }

        locker.unlock(entities[0]);
        locker.unlock(entities[1]);

        Assertions.assertEquals(LockState.FREE, locker.getState(entities[0]));
        Assertions.assertEquals(LockState.LOCKED, locker.getState(entities[2]));
        Assertions.assertEquals(LockState.DETACH, locker.getState(entities[3]));


        locker.lock(entities[0]);
        Assertions.assertEquals(LockState.LOCKED, locker.getState(entities[0]));

        locker.unlock(entities[0]);
        Assertions.assertEquals(LockState.FREE, locker.getState(entities[0]));

    }

    @Test
    @DisplayName("Test lock entity with timeout")
    void testUnlockByTimeout() throws InterruptedException {

        EntityLocker<Integer> locker = new EntityLocker<>();

        int id = 5;

        locker.lock(id, 100);

        TimeUnit.MILLISECONDS.sleep(120);

        Assertions.assertEquals(LockState.FREE, locker.getState(id));

    }

    @Test
    @DisplayName("Test execute code safety")
    void testSafeExecute() throws InterruptedException {

        EntityLocker<Integer> locker = new EntityLocker<>();

        Thread[] threads = new Thread[5];

        for (int i = 0; i < 5; i++) {
            int finalI = i;
            threads[i] = new Thread(() -> locker.acceptSafe((finalI % 2 + 1), id -> {
                System.out.println("Entity " + id + " action 1");
                System.out.println("Entity " + id + " action 2");
                System.out.println("Entity " + id + " action 3");
            }));

            threads[i].start();
        }

        TimeUnit.MILLISECONDS.sleep(200);

        Assertions.assertEquals(LockState.FREE, locker.getState(1));
        Assertions.assertEquals(LockState.FREE, locker.getState(2));

    }

}