package cn.zbx1425.resourcepackupdater.io.network;

import cn.zbx1425.resourcepackupdater.io.ProgressReceiver;

import java.io.OutputStream;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Supplier;

public class DownloadDispatcher {

    private final ProgressReceiver progressReceiver;

    public long totalBytes;
    public long downloadedBytes;
    public AtomicLong newlyDownloadedBytes = new AtomicLong(0);

    private long lastSummaryTime = -1;
    private long lastSummaryBytes = 0;
    public long summaryBytesPerSecond = 0;

    private final ExecutorService executor = Executors.newFixedThreadPool(4);
    public ConcurrentLinkedQueue<DownloadTask> runningTasks = new ConcurrentLinkedQueue<>();
    public ConcurrentLinkedQueue<DownloadTask> incompleteTasks = new ConcurrentLinkedQueue<>();
    private final ConcurrentLinkedQueue<Runnable> delayedProgresses = new ConcurrentLinkedQueue<>();

    private Exception taskException = null;

    public DownloadDispatcher(ProgressReceiver progressReceiver) {
        this.progressReceiver = progressReceiver;
    }

    private final int MAX_RETRIES = 8;

    public void dispatch(DownloadTask task, Supplier<OutputStream> target) {
        totalBytes += task.expectedSize;
        incompleteTasks.add(task);
        executor.submit(() -> {
            runningTasks.add(task);
            try {
                while (true) {
                    try {
                        task.runBlocking(target.get());
                        if (task.failedAttempts > 0) {
                            delayedProgresses.add(() -> {
                                progressReceiver.printLogOutsidePolling(String.format("Downloading files ... (Retry %d succeed)",
                                        task.failedAttempts));
                            });
                        }
                        break;
                    } catch (Exception ex) {
                        task.failedAttempts++;
                        if (task.failedAttempts < MAX_RETRIES) {
                            delayedProgresses.add(() -> {
                                progressReceiver.printLogOutsidePolling(String.format("Retry (%d/%d) for %s due to error:",
                                        task.failedAttempts, MAX_RETRIES, task.fileName));
                                progressReceiver.printLogOutsidePolling(String.format("Retry %d: %s", task.failedAttempts, ex.toString()));
                            });
                        } else {
                            throw ex;
                        }
                    }
                }
            } catch (Exception e) {
                taskException = e;
                executor.shutdownNow();
                runningTasks.clear();
                incompleteTasks.clear();
            } finally {
                runningTasks.remove(task);
                incompleteTasks.remove(task);
            }
        });
    }

    public void updateSummary() {
        while (!delayedProgresses.isEmpty()) delayedProgresses.poll().run();
        long newBytes = newlyDownloadedBytes.getAndSet(0);
        downloadedBytes += newBytes;
        if (lastSummaryTime == -1) {
            lastSummaryTime = System.currentTimeMillis();
            lastSummaryBytes = downloadedBytes;
        } else {
            long currentTime = System.currentTimeMillis();
            long deltaTime = currentTime - lastSummaryTime;
            double SMOOTH_FACTOR = 0.05;
            double instantSpeed = (downloadedBytes - lastSummaryBytes) * 1000.0 / deltaTime;
            summaryBytesPerSecond = (long) (summaryBytesPerSecond * (1 - SMOOTH_FACTOR) + instantSpeed * SMOOTH_FACTOR);
            lastSummaryTime = currentTime;
            lastSummaryBytes = downloadedBytes;
        }
        String message = String.format(": % 5.2f MiB / % 5.2f MiB; %5d KiB/s",
                downloadedBytes / 1048576.0, totalBytes / 1048576.0, summaryBytesPerSecond / 1024);
        progressReceiver.setProgress(downloadedBytes * 1f / totalBytes, 0);

        String runningProgress = incompleteTasks.size() + " Files Remaining\n" +
                String.join("\n", runningTasks.stream()
                .map(task -> "  " + (
                    task.totalBytes == 0 ? "WAIT" :
                        String.format("%.1f%%", task.downloadedBytes * 100f / task.totalBytes)
                ) + "\t"
                + (task.failedAttempts > 0 ? "(RETRY " + task.failedAttempts + ") " : "")
                + task.fileName)
                .toList());
        progressReceiver.setInfo(runningProgress, message);
    }

    public boolean tasksFinished() throws Exception {
        if (taskException != null) {
            while (!delayedProgresses.isEmpty()) delayedProgresses.poll().run();
            throw taskException;
        }
        return incompleteTasks.isEmpty();
    }

    protected void onDownloadProgress(long deltaBytes) {
        newlyDownloadedBytes.addAndGet(deltaBytes);
    }

    public void close() {
        executor.shutdown();
    }
}
