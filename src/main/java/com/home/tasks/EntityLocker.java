package com.home.tasks;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.locks.Lock;
import java.util.concurrent.locks.ReentrantLock;
import java.util.function.Consumer;

import lombok.extern.slf4j.Slf4j;

/**
 * Only one thread have exclusive grants for entity
 *
 * @author mikelevin
 */
@Slf4j
public class EntityLocker<T> {

    private final Map<T, Thread> locks;
    private final Map<T, Long> timer;
    private final Map<T, LockState> states;
    private final Lock lock;
    private final Object monitor;

    public EntityLocker() {
        locks = new ConcurrentHashMap<>();
        states = new ConcurrentHashMap<>();
        lock = new ReentrantLock();
        timer = new ConcurrentHashMap<>();
        Thread unlockProcessorThread = new Thread(this::unlockEntitiesTimeUp);
        unlockProcessorThread.setDaemon(true);
        unlockProcessorThread.start();
        monitor = new Object();
    }

    private void unlockEntitiesTimeUp() {
        while (true) {
            try {
                lock.lock();
                long current = System.currentTimeMillis();
                List<T> ids = new ArrayList<>();
                for (Map.Entry<T, Long> timeEntry : timer.entrySet()) {
                    if (current >= timeEntry.getValue()) {
                        forceUnlock(timeEntry.getKey());
                        ids.add(timeEntry.getKey());
                    }
                }
                ids.forEach(timer::remove);
                lock.unlock();
                TimeUnit.MILLISECONDS.sleep(10);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    public void acceptSafe(T id, Consumer<T> consumer) {
        lock(id);
        log.debug("Thread {} start execute protected code", Thread.currentThread().getName());
        consumer.accept(id);
        log.debug("Thread {} finish work", Thread.currentThread().getName());
        unlock(id);
    }

    public LockState getState(T id) {
        return states.getOrDefault(id, LockState.DETACH);
    }

    public void lock(T id, long timeout) {
        lock(id);
        long lockedUp = System.currentTimeMillis() + timeout;
        timer.put(id, lockedUp);
    }

    public void lock(T id) {
        Thread current = Thread.currentThread();
        if (locks.containsKey(id)) {
            processWait();
        } else {
            processLock(current, id);
        }

    }

    private void processWait() {
        synchronized (monitor) {
            try {
                monitor.wait();
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

    private void processNotify() {
        synchronized (monitor) {
            monitor.notify();
        }
    }

    private void processLock(Thread thread, T id) {
        locks.put(id, thread);
        states.put(id, LockState.LOCKED);
        log.debug("Entity {} was locked by {}", id, thread.getName());
    }


    private void forceUnlock(T id) {
        locks.remove(id);
        states.put(id, LockState.FREE);
        log.debug("Entity {} was unlocked by timout", id);
        processNotify();
    }

    public void unlock(T id) {
        Thread owner = locks.get(id);
        if (owner == null) {
            log.debug("Entity is not locked");
            return;
        }
        Thread current = Thread.currentThread();
        lock.lock();
        if (current.equals(owner)) {
            locks.remove(id);
            states.put(id, LockState.FREE);
            log.debug("Entity {} was unlocked", id);
            processNotify();
        } else {
            log.error("Thread ({}) has no grants for unlock entity - {}",
                    current.getName(), id);
        }
        lock.unlock();
    }


}
