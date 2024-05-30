package org.example;

import java.nio.file.Path;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public interface ILogParser {
    record LogDetail(LocalDateTime time, String threadName, String fileName, String priority, String content) {
    }

    class Log {
        List<LogDetail> lines = new ArrayList<>();
        LoadStatus loadStatus;

        public static Log load(List<Path> paths) {
            var l = new Log();
            l.loadStatus = new LoadStatus(new ArrayList<>(paths));
            return l;
        }

        public void start() throws InterruptedException {
            LogParser logParser = new LogParser();
            logParser.load(this);
        }
    }

    class LoadStatus {
        boolean isLoading = false;
        List<Path> loadedPaths = new ArrayList<>();
        List<Path> allPaths;

        public LoadStatus(List<Path> paths) {
            allPaths = new ArrayList<>(paths);
        }
    }


    void load(Log log) throws InterruptedException;
}
