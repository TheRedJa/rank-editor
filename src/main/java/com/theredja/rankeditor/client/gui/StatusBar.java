package com.theredja.rankeditor.client.gui;

import java.util.ArrayList;
import java.util.List;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;

/**
 * Bottom line: keyboard hints on the left (always), then a warning when the rank files have problems, and the latest
 * status message right-aligned (green, red for errors) until it expires.
 */
@SideOnly(Side.CLIENT)
final class StatusBar extends Panel {

    private final GuiRankEditor editor;
    private final Label hints;
    private final Label message;
    private final Label problems;

    StatusBar(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        this.hints = new Label(this, editor::hintText).color(Ui.DIM);
        this.message = new Label(this, editor::messageText).color(editor::messageColor).alignRight();
        this.problems = new Label(this, () -> {
            int n = editor.problems().size();
            return n == 0 ? "" : "! " + n + (n == 1 ? " problem" : " problems") + " in rank files";
        }).color(Ui.WARN).alignRight().tooltip(this::problemLines);
    }

    private List<String> problemLines() {
        List<String> lines = new ArrayList<>();
        for (String problem : editor.problems()) {
            lines.addAll(Ui.wrap(editor.getTheme(), "- " + problem, 280));
        }
        return lines;
    }

    @Override
    public void addWidgets() {
        add(hints);
        add(problems);
        add(message);
    }

    @Override
    public void alignWidgets() {
        int hintsWidth = Math.min(editor.getTheme().getStringWidth(editor.hintText()) + 4, width / 3);
        int problemsWidth = editor.problems().isEmpty() ? 0 : 150;
        int messageX = hintsWidth + problemsWidth + 12;
        hints.setPosAndSize(0, 0, hintsWidth, height);
        problems.setPosAndSize(hintsWidth + 8, 0, problemsWidth, height);
        message.setPosAndSize(messageX, 0, Math.max(0, width - messageX), height);
    }
}
