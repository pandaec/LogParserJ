package org.example;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

public class LogParser implements ILogParser {
    @Override
    public Log load(List<Path> paths, LoadStatus loadStatus) throws InterruptedException {
        List<LogDetail> lines = new ArrayList<>();

        List<Path> loadedPaths = new ArrayList<>();
        loadStatus.allPaths().clear();
        loadStatus.allPaths().addAll(paths);

        for (Path p : paths) {
            Thread.sleep(750);
            loadedPaths.add(p);
            loadStatus.loadedPaths().clear();
            loadStatus.loadedPaths().addAll(loadedPaths);
        }

        return new Log(lines);
    }
}
