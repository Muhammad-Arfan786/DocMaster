package com.docreader.utils;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

/**
 * Global executor pools for the whole application.
 * Provides thread pools for disk I/O, network operations, and main thread execution.
 *
 * Follows Singleton pattern for efficient thread pool reuse across the app.
 *
 * Usage:
 *   AppExecutors.getInstance().diskIO().execute(() -> {
 *       // Background work
 *       AppExecutors.getInstance().mainThread().execute(() -> {
 *           // UI update
 *       });
 *   });
 */
public final class AppExecutors {

    private static final int DISK_IO_THREAD_COUNT = 3;
    private static final int NETWORK_THREAD_COUNT = 3;
    private static final long SHUTDOWN_TIMEOUT_SECONDS = 5;

    private static volatile AppExecutors instance;

    private final ExecutorService diskIO;
    private final ExecutorService networkIO;
    private final Executor mainThread;

    private AppExecutors() {
        diskIO = Executors.newFixedThreadPool(DISK_IO_THREAD_COUNT, runnable -> {
            Thread thread = new Thread(runnable, "DiskIO-Thread");
            thread.setPriority(Thread.NORM_PRIORITY - 1);
            return thread;
        });

        networkIO = Executors.newFixedThreadPool(NETWORK_THREAD_COUNT, runnable -> {
            Thread thread = new Thread(runnable, "NetworkIO-Thread");
            return thread;
        });

        mainThread = new MainThreadExecutor();
    }

    /**
     * Get the singleton instance of AppExecutors.
     */
    public static AppExecutors getInstance() {
        if (instance == null) {
            synchronized (AppExecutors.class) {
                if (instance == null) {
                    instance = new AppExecutors();
                }
            }
        }
        return instance;
    }

    /**
     * Executor for disk I/O operations (file read/write, database, PDF processing).
     */
    public ExecutorService diskIO() {
        return diskIO;
    }

    /**
     * Executor for network operations.
     */
    public ExecutorService networkIO() {
        return networkIO;
    }

    /**
     * Executor that runs on the main/UI thread.
     */
    public Executor mainThread() {
        return mainThread;
    }

    /**
     * Convenience method: execute background task and callback on main thread.
     *
     * @param backgroundTask Task to run on background thread
     * @param uiCallback Callback to run on main thread after background task completes
     */
    public void executeWithCallback(Runnable backgroundTask, Runnable uiCallback) {
        diskIO.execute(() -> {
            try {
                backgroundTask.run();
                mainThread.execute(uiCallback);
            } catch (Exception e) {
                AppLogger.e("Background task failed", e);
                mainThread.execute(uiCallback);
            }
        });
    }

    /**
     * Convenience method: execute background task with success and error callbacks.
     *
     * @param backgroundTask Task to run on background thread
     * @param onSuccess Callback on success (runs on main thread)
     * @param onError Callback on error (runs on main thread)
     */
    public void executeWithCallbacks(Runnable backgroundTask, Runnable onSuccess,
                                      java.util.function.Consumer<Exception> onError) {
        diskIO.execute(() -> {
            try {
                backgroundTask.run();
                mainThread.execute(onSuccess);
            } catch (Exception e) {
                AppLogger.e("Background task failed", e);
                mainThread.execute(() -> onError.accept(e));
            }
        });
    }

    /**
     * Shutdown all executors gracefully.
     * Call this when the application is terminating.
     */
    public void shutdown() {
        shutdownExecutor(diskIO, "DiskIO");
        shutdownExecutor(networkIO, "NetworkIO");
    }

    private void shutdownExecutor(ExecutorService executor, String name) {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_TIMEOUT_SECONDS, TimeUnit.SECONDS)) {
                executor.shutdownNow();
                AppLogger.w(name + " executor did not terminate gracefully");
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    /**
     * Main thread executor implementation using Handler.
     */
    private static class MainThreadExecutor implements Executor {
        private final Handler mainHandler = new Handler(Looper.getMainLooper());

        @Override
        public void execute(Runnable command) {
            mainHandler.post(command);
        }
    }
}
