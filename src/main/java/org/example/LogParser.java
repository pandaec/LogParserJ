package org.example;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class LogParser implements ILogParser {
    Pattern pattern = Pattern.compile("^\\[([A-Z]{3}) ([A-Za-z0-9\\s_\\-!@#$%^&*()_+|<?.:=\\[\\]/,]+?),(\\d{2}-\\d{2}\\s\\d{2}:\\d{2}:\\d{2}\\.\\d+)\\]:(.*)$");

    @Override
    public void load(Log log) throws InterruptedException {
        log.loadStatus.isLoading = true;

        LogDetail detail = null;
        for (Path p : log.loadStatus.allPaths) {
            String currentFileName = p.getFileName().toString();
            try (var reader = Files.newBufferedReader(p)) {
                for (String line = reader.readLine(); line != null; line = reader.readLine()) {
                    Matcher matcher = pattern.matcher(line);

                    if (matcher.matches()) {
                        if (detail != null) {
                            log.lines.add(detail);
                        }
                        detail = new LogDetail(null, matcher.group(2), matcher.group(3), matcher.group(4), p.toString());
                        log.loadStatus.currentFileName = currentFileName;
                    } else {
                        if (detail != null) {
                            detail.appendLine(line);
                        }
                    }
                }
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            log.loadStatus.loadedPaths.add(p);
        }
        if (detail != null) {
            log.lines.add(detail);
        }

        log.loadStatus.isLoading = false;
    }
}
