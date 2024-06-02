package org.example;

import imgui.ImGui;
import imgui.ImGuiListClipper;
import imgui.ImVec2;
import imgui.app.Application;
import imgui.app.Configuration;
import imgui.callback.ImListClipperCallback;
import imgui.flag.*;
import imgui.type.ImBoolean;
import imgui.type.ImString;

import java.io.IOException;
import java.nio.file.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

public class Main extends Application {
    public static class Filter {
        ImString imstr = new ImString(1024);
        ImBoolean imIsCaseSensitive = new ImBoolean(false);
        boolean isRegexError = false;

        Map<String, Pattern> mapPattern = new HashMap<>();
    }

    ImBoolean imShowLogWindow = new ImBoolean(true);
    ImBoolean imShowImportWindow = new ImBoolean(true);

    DateTimeFormatter dateTimeFormatter = DateTimeFormatter.ofPattern("MM-dd HH:mm:ss.SSS");

    public static class ImportData {
        ImString imDirectoryPath = new ImString("D:/Projects/log-parser/WV-ST-20240308/", 1024);
        List<Path> filesLeft = new ArrayList<>();
        List<Path> filesRight = new ArrayList<>();
    }

    private final ImportData importData = new ImportData();

    public static class FindData {
        int line_selected = -1;
        ImString imRawText = new ImString(50);
    }

    Filter filter = new Filter();
    Filter filterFind = new Filter();

    FindData findData = new FindData();

    private final ExecutorService executor = Executors.newWorkStealingPool();

    private volatile ILogParser.Log db;
    private volatile ILogParser.Log dbFind;

    @Override
    protected void configure(Configuration config) {
        config.setTitle("Log Parser");
    }

    @Override
    protected void initImGui(Configuration config) {
        super.initImGui(config);

        final imgui.ImGuiIO io = ImGui.getIO();
//        io.setIniFilename(null);                                // We don't want to save .ini file
        io.addConfigFlags(ImGuiConfigFlags.NavEnableKeyboard);  // Enable Keyboard Controls
        io.addConfigFlags(ImGuiConfigFlags.DockingEnable);      // Enable Docking
//        io.addConfigFlags(ImGuiConfigFlags.ViewportsEnable);    // Enable Multi-Viewport / Platform Windows
        io.setConfigViewportsNoTaskBarIcon(true);
    }

    @Override
    public void process() {
        try {
            ImGui.dockSpaceOverViewport(ImGui.getMainViewport());

            //         ImGui.showDemoWindow();

            if (ImGui.beginMainMenuBar()) {
                if (ImGui.beginMenu("Windows")) {
                    ImGui.menuItem("Log", null, imShowLogWindow);
                    ImGui.menuItem("Import", null, imShowImportWindow);
                    ImGui.endMenu();
                }
                ImGui.endMainMenuBar();
            }

            renderLoadingModal();
            renderLogWindow();
            renderImportWindow();
        } catch (Throwable e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        launch(new Main());
    }

    private void renderLoadingModal() {
        if (db == null || !db.loadStatus.isLoading) {
            return;
        }
        ImGui.openPopup("Importing...");
        ImVec2 vec = ImGui.getMainViewport().getCenter();
        ImGui.setNextWindowPos(vec.x, vec.y, ImGuiCond.Appearing, 0.5f, 0.5f);
        if (ImGui.beginPopupModal("Importing...", ImGuiWindowFlags.AlwaysAutoResize)) {
            ImGui.text(String.format("Files loaded: %d / %d", db.loadStatus.loadedPaths.size(), db.loadStatus.allPaths.size()));
            ImGui.text(db.loadStatus.currentFileName);
            ImGui.endPopup();
        }
    }

    private void renderLogWindow() {
        ImGui.begin("Log Parser", imShowLogWindow);

        if (ImGui.inputTextWithHint("Filter", "Filter", filter.imstr, ImGuiInputTextFlags.EnterReturnsTrue)) {
            executor.submit(() -> {
                List<ILogParser.LogDetail> lines_filtered = new ArrayList<>();
                parseFilter(filter);
                if (filter.isRegexError) {
                    return;
                }
                try {
                    for (ILogParser.LogDetail detail : db.lines) {
                        if (isLineMatchFilter(detail, filter)) {
                            lines_filtered.add(detail);
                        }
                    }
                    db.lines_filtered = lines_filtered;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }
        ImGui.sameLine();
        ImGui.checkbox("Case Sensitive", filter.imIsCaseSensitive);
        ImGui.spacing();

        if (filter.isRegexError) {
            ImGui.textColored(0.8f, 0.0f, 0.0f, 1.0f, "Syntax Error");
            ImGui.spacing();
        }

        ImGui.beginChild("ChildL", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY() * 0.7f, false, ImGuiWindowFlags.HorizontalScrollbar);

        // TODO clipper scroll to highlight

        if (ImGui.beginTable("LogTable", 4, ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.RowBg | ImGuiTableFlags.Borders | ImGuiTableFlags.Resizable)) {
            ImGui.tableSetupColumn("Time", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Lv", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Thread", ImGuiTableColumnFlags.WidthFixed);
            ImGui.tableSetupColumn("Content", ImGuiTableColumnFlags.WidthStretch);
            ImGui.tableHeadersRow();

            if (db != null) {
                ImGuiListClipper.forEach(db.lines_filtered.size(), new ImListClipperCallback() {
                    @Override
                    public void accept(int i) {
                        ILogParser.LogDetail detail = db.lines_filtered.get(i);
                        ImGui.tableNextRow();
                        if (ImGui.tableSetColumnIndex(0)) {
                            String dt = dateTimeFormatter.format(detail.time);
                            String id = String.format("%s##%d", dt, i);
                            if (ImGui.selectable(id, false, ImGuiSelectableFlags.SpanAllColumns)) {
                                findData.line_selected = i;
                                findData.imRawText = new ImString(detail.getContent());
                            }
                        }

                        if (ImGui.tableSetColumnIndex(1)) {
                            ImGui.text(detail.priority);
                        }

                        if (ImGui.tableSetColumnIndex(2)) {
                            ImGui.text(detail.threadName);
                        }

                        if (ImGui.tableSetColumnIndex(3)) {
                            String content = detail.getContent();
                            int index = content.indexOf("\n");
                            if (index >= 0) {
                                content = content.substring(0, index);
                            }
                            ImGui.text(content);
                        }
                    }
                });
            }
            ImGui.endTable();
        }
        ImGui.endChild();
        ImGui.spacing();
        // If can't resize, then remove this
        ImGui.beginChild("##LowerSection", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false);

        if (ImGui.beginTabBar("##Tabs")) {
            if (ImGui.beginTabItem("Find")) {
                if (ImGui.inputTextWithHint("##Filter", "Filter", filterFind.imstr, ImGuiInputTextFlags.EnterReturnsTrue)) {
                    executor.submit(() -> {
                        List<ILogParser.LogDetail> lines_filtered = new ArrayList<>();
                        parseFilter(filterFind);
                        if (filterFind.isRegexError) {
                            return;
                        }
                        try {
                            for (ILogParser.LogDetail detail : dbFind.lines) {
                                if (isLineMatchFilter(detail, filter) && isLineMatchFilter(detail, filterFind)) {
                                    lines_filtered.add(detail);
                                }
                            }
                            dbFind.lines_filtered = lines_filtered;
                        } catch (Throwable e) {
                            e.printStackTrace();
                        }
                    });
                }
                ImGui.sameLine();
                ImGui.checkbox("Case Sensitive", filterFind.imIsCaseSensitive);
                ImGui.spacing();

                if (filterFind.isRegexError) {
                    ImGui.textColored(0.8f, 0.0f, 0.0f, 1.0f, "Syntax Error");
                    ImGui.spacing();
                }

                ImGui.beginChild("##FindTabChild", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false, ImGuiWindowFlags.HorizontalScrollbar);

                if (ImGui.beginTable("FindTabLogTable", 4, ImGuiTableFlags.SizingFixedFit | ImGuiTableFlags.RowBg | ImGuiTableFlags.Borders | ImGuiTableFlags.Resizable)) {
                    ImGui.tableSetupColumn("Time", ImGuiTableColumnFlags.WidthFixed);
                    ImGui.tableSetupColumn("Lv", ImGuiTableColumnFlags.WidthFixed);
                    ImGui.tableSetupColumn("Thread", ImGuiTableColumnFlags.WidthFixed);
                    ImGui.tableSetupColumn("Content", ImGuiTableColumnFlags.WidthStretch);
                    ImGui.tableHeadersRow();

                    if (dbFind != null) {
                        ImGuiListClipper.forEach(dbFind.lines_filtered.size(), new ImListClipperCallback() {
                            @Override
                            public void accept(int i) {
                                ILogParser.LogDetail detail = dbFind.lines_filtered.get(i);
                                ImGui.tableNextRow();
                                if (ImGui.tableSetColumnIndex(0)) {
                                    String dt = dateTimeFormatter.format(detail.time);
                                    String id = String.format("%s##Find%d", dt, i);
                                    if (ImGui.selectable(id, false, ImGuiSelectableFlags.SpanAllColumns)) {
                                    }
                                }

                                if (ImGui.tableSetColumnIndex(1)) {
                                    ImGui.text(detail.priority);
                                }

                                if (ImGui.tableSetColumnIndex(2)) {
                                    ImGui.text(detail.threadName);
                                }

                                if (ImGui.tableSetColumnIndex(3)) {
                                    String content = detail.getContent();
                                    int index = content.indexOf("\n");
                                    if (index >= 0) {
                                        content = content.substring(0, index);
                                    }
                                    ImGui.text(content);
                                }
                            }
                        });
                    }

                    ImGui.endTable();
                }

                ImGui.endChild();

                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Info")) {
                ImGui.textWrapped(findData.imRawText.toString());
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Raw")) {
                ImGui.inputTextMultiline("##source", findData.imRawText, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
                ImGui.endTabItem();
            }

            ImGui.endTabBar();
        }

        ImGui.endChild();

        ImGui.end();
    }

    private void renderImportWindow() {
        ImGui.begin("Import", imShowImportWindow);

        if (ImGui.button("Load")) {
            importData.filesLeft.clear();
            importData.filesRight.clear();

            try {
                String rawPath = importData.imDirectoryPath.toString();
                Path path = Paths.get(rawPath);
                try (DirectoryStream<Path> stream = Files.newDirectoryStream(path)) {
                    for (Path entry : stream) {
                        if (Files.isRegularFile(entry)) {
                            importData.filesLeft.add(entry);
                        }
                    }
                }
            } catch (InvalidPathException | IOException e) {
                e.printStackTrace();
            }
        }

        ImGui.sameLine();
        ImGui.inputText("Log Directory", importData.imDirectoryPath);

        ImGui.beginChild("##Left", ImGui.getContentRegionAvailX() / 2, ImGui.getContentRegionAvailY(), true);

        boolean isMoved = false;
        if (ImGui.button("Stage All")) {
            importData.filesRight.addAll(importData.filesLeft);
            importData.filesLeft.clear();
            isMoved = true;
        }

        Iterator<Path> iterLeft = importData.filesLeft.iterator();
        while (iterLeft.hasNext()) {
            Path left = iterLeft.next();
            if (ImGui.selectable(left.getFileName().toString())) {
                iterLeft.remove();
                importData.filesRight.add(left);
                isMoved = true;
            }
        }

        ImGui.endChild();
        ImGui.sameLine();

        ImGui.beginChild("##Right", 0, 0, true);
        if (ImGui.button("Load Logs")) {
            executor.submit(() -> {
                try {
                    db = ILogParser.Log.load(importData.filesRight);
                    db.start();

                    dbFind = new ILogParser.Log();
                    dbFind.lines = db.lines;
                } catch (Throwable e) {
                    e.printStackTrace();
                }
            });
        }

        Iterator<Path> iterRight = importData.filesRight.iterator();
        while (iterRight.hasNext()) {
            Path right = iterRight.next();
            if (ImGui.selectable(right.getFileName().toString())) {
                iterRight.remove();
                importData.filesLeft.add(right);
                isMoved = true;
            }
        }

        if (isMoved) {
            Collections.sort(importData.filesLeft);
            Collections.sort(importData.filesRight);
        }

        ImGui.endChild();
        ImGui.end();
    }

    static void resetFilter(Filter filter) {
        filter.mapPattern.clear();
        filter.isRegexError = false;
    }


    static void parseFilter(Filter filter) {
        resetFilter(filter);
        String filterStr = filter.imstr.toString();

        int colStart = 0;
        int colEnd = 0;
        int condStart = 0;
        int condEnd = 0;

        try {
            int regexFlag = 0;
            if (!filter.imIsCaseSensitive.get()) {
                regexFlag |= Pattern.CASE_INSENSITIVE;
            }
            // Parsing logic could be done in regex instead
            for (int i = 0; i < filterStr.length(); i++) {
                char c = filterStr.charAt(i);
                if (colEnd < 1) {
                    for (; i < filterStr.length() && filterStr.charAt(i) == ' '; i++) ;
                    colStart = i;
                    for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) != ' ' && filterStr.charAt(i + 1) != '='; i++)
                        ;
                    colEnd = i + 1;
                } else if (condStart < 1) {
                    if (c == '\'' || c == '"') {
                        i = i + 1;
                        condStart = i;
                        for (; i < filterStr.length() && (filterStr.charAt(i) != '\'' && filterStr.charAt(i) != '"'); i++)
                            ;
                        condEnd = i;
                        String colName = filterStr.substring(colStart, colEnd);
                        Pattern pattern = Pattern.compile("^.*" + filterStr.substring(condStart, condEnd) + ".*$", regexFlag);
                        filter.mapPattern.put(colName.toUpperCase(), pattern);
                    }
                } else if (condEnd > 0) {
                    if (i + 2 < filterStr.length()) {
                        String s = filterStr.substring(i, i + 3);
                        if (s.equalsIgnoreCase("AND")) {
                            i = i + 3;
                            for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) == ' '; i++) ;
                            colStart = i + 1;
                            colEnd = 0;
                            condStart = 0;
                            condEnd = 0;
                        }
                    }
                }
                for (; i + 1 < filterStr.length() && filterStr.charAt(i + 1) == ' '; i++) ;
            }

            if (filter.mapPattern.isEmpty()) {
                Pattern pattern = Pattern.compile("^.*" + filterStr + ".*$", regexFlag);
                filter.mapPattern.put("*", pattern);
            }
        } catch (PatternSyntaxException e) {
            e.printStackTrace();
            filter.isRegexError = true;
        }
    }


    static boolean isLineMatchFilter(ILogParser.LogDetail detail, Filter filter) {
        Pattern patternDefault = filter.mapPattern.get("*");
        boolean isAnyMatchTrue = filter.mapPattern.size() == 1 && patternDefault != null;
        Map<String, String> map = new HashMap<>();
        map.put("C1", detail.priority);
        map.put("LV", detail.priority);
        map.put("C2", detail.threadName);
        map.put("THREAD", detail.threadName);
        map.put("C3", detail.getContent());
        map.put("CONTENT", detail.getContent());

        for (Map.Entry<String, String> kv : map.entrySet()) {
            Pattern pattern = filter.mapPattern.getOrDefault(kv.getKey(), patternDefault);
            if (pattern == null) {
                continue;
            }
            String content = kv.getValue();
            boolean isMatch = pattern.matcher(content).find();
            if (!isAnyMatchTrue) {
                if (!isMatch) {
                    return false;
                }
            } else {
                if (isMatch) {
                    return true;
                }
            }
        }
        return !isAnyMatchTrue;
    }
}