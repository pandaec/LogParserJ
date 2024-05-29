package org.example;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.List;

public interface ILogParser {
    record LogDetail(LocalDateTime time, String threadName, String fileName, String priority, String content) {
    }

    record Log(List<LogDetail> lines) {
    }

    record LoadStatus(List<Path> loadedPaths, List<Path> allPaths) {
    }

    interface ProgressCallback {
        void onProgressUpdate(LoadStatus loadStatus);
    }

    Log load(List<Path> paths, LoadStatus loadStatus) throws InterruptedException;
}
