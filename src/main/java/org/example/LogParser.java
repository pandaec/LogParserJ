package org.example;

import java.nio.file.Path;

public class LogParser implements ILogParser {
    @Override
    public void load(Log log) throws InterruptedException {
        log.loadStatus.isLoading = true;
        for (Path p : log.loadStatus.allPaths) {
            Thread.sleep(750);
            log.loadStatus.loadedPaths.add(p);
        }
        log.loadStatus.isLoading = false;
    }
}
