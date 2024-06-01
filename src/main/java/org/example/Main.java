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
import java.util.ArrayList;
import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class Main extends Application {
    public class Filter {
        public ImString imstr = new ImString(1024);
        public ImBoolean imIsCaseSensitive = new ImBoolean(false);
        public boolean isRegexError = false;
    }

    private Filter filter = new Filter();
    private Filter detail_filter = new Filter();

    private ImString imRawText = new ImString(2048);

    private ImBoolean imShowLogWindow = new ImBoolean(true);
    private ImBoolean imShowImportWindow = new ImBoolean(true);


    public class ImportData {
        ImString imDirectoryPath = new ImString("D:/Projects/log-parser/WV-ST-20240308/", 1024);
        List<Path> filesLeft = new ArrayList<>();
        List<Path> filesRight = new ArrayList<>();

    }

    private ImportData importData = new ImportData();

    private ExecutorService executor = Executors.newSingleThreadExecutor();

    private volatile ILogParser.Log db = null;

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

            ImGuiListClipper.forEach(100_000, new ImListClipperCallback() {
                @Override
                public void accept(int index) {
                    ImGui.tableNextRow();
                    if (ImGui.tableSetColumnIndex(0)) {
                        ImGui.text("AAAAA");
                    }

                    if (ImGui.tableSetColumnIndex(1)) {
                        ImGui.text("BBBBBB");
                    }

                    if (ImGui.tableSetColumnIndex(2)) {
                        ImGui.text("CCCCCCCCC");
                    }

                    if (ImGui.tableSetColumnIndex(3)) {
                        ImGui.text("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
                    }
                }
            });
            ImGui.endTable();
        }

        ImGui.endChild();

        ImGui.spacing();

        // If can't resize, then remove this
        ImGui.beginChild("##LowerSection", ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY(), false);

        if (ImGui.beginTabBar("##Tabs")) {
            if (ImGui.beginTabItem("Find")) {

                if (ImGui.inputTextWithHint("##Filter", "Filter", detail_filter.imstr, ImGuiInputTextFlags.EnterReturnsTrue)) {

                }
                ImGui.sameLine();
                ImGui.checkbox("Case Sensitive", filter.imIsCaseSensitive);
                ImGui.spacing();

                if (detail_filter.isRegexError) {
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

                    ImGuiListClipper.forEach(100_000, new ImListClipperCallback() {
                        @Override
                        public void accept(int index) {
                            ImGui.tableNextRow();
                            if (ImGui.tableSetColumnIndex(0)) {
                                ImGui.text("AAAAA");
                            }

                            if (ImGui.tableSetColumnIndex(1)) {
                                ImGui.text("BBBBBB");
                            }

                            if (ImGui.tableSetColumnIndex(2)) {
                                ImGui.text("CCCCCCCCC");
                            }

                            if (ImGui.tableSetColumnIndex(3)) {
                                ImGui.text("DDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDDD");
                            }
                        }
                    });
                    ImGui.endTable();
                }

                ImGui.endChild();

                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Info")) {
                ImGui.textWrapped(imRawText.toString());
                ImGui.endTabItem();
            }

            if (ImGui.beginTabItem("Raw")) {
                ImGui.inputTextMultiline("##source", imRawText, ImGui.getContentRegionAvailX(), ImGui.getContentRegionAvailY());
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
}