package com.theredja.rankeditor.client.gui;

import java.util.function.BooleanSupplier;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.function.Supplier;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;
import com.feed_the_beast.ftblib.lib.gui.TextBox;
import com.feed_the_beast.ftblib.lib.util.misc.MouseButton;

/**
 * Text box with the behaviour the editor needs on top of FTBLib's {@link TextBox}:
 * <ul>
 *   <li>a model {@linkplain #source binding} that is re-read on refresh unless the user is typing,</li>
 *   <li>a {@linkplain #commit commit} on Enter, Tab and focus loss that stages an edit (an error reverts the text),</li>
 *   <li>Escape to abandon an edit, and a live {@linkplain #onChange change listener} for search boxes.</li>
 * </ul>
 * TextBox never reports focus loss itself, so the owning panel calls {@link #blur()} on clicks elsewhere.
 */
@SideOnly(Side.CLIENT)
final class InputBox extends TextBox {

    private final GuiRankEditor editor;
    private Supplier<String> source;
    private Function<String, String> committer;
    private Predicate<String> validator;
    private BooleanSupplier editable;
    private Runnable changeListener;
    private Runnable enterListener;
    private Runnable tabListener;

    /** @param editor owner for error reporting and focus tracking; null for boxes inside dialogs */
    InputBox(Panel parent, GuiRankEditor editor) {
        super(parent);
        this.editor = editor;
        if (editor != null) {
            editor.register(this);
        }
        setHeight(16);
    }

    /** Binds the box to a model value. */
    InputBox source(Supplier<String> value) {
        this.source = value;
        return this;
    }

    /** Called with the text when the user finishes an edit; returns an error message or null on success. */
    InputBox commit(Function<String, String> action) {
        this.committer = action;
        return this;
    }

    InputBox validator(Predicate<String> valid) {
        this.validator = valid;
        revalidate();
        return this;
    }

    InputBox editable(BooleanSupplier supplier) {
        this.editable = supplier;
        return this;
    }

    InputBox ghost(String hint) {
        this.ghostText = hint;
        return this;
    }

    InputBox onChange(Runnable listener) {
        this.changeListener = listener;
        return this;
    }

    InputBox onEnter(Runnable listener) {
        this.enterListener = listener;
        return this;
    }

    InputBox onTab(Runnable listener) {
        this.tabListener = listener;
        return this;
    }

    void revalidate() {
        setText(getText(), false);
    }

    /** Pulls the bound model value into the box, unless the user is in the middle of typing. */
    void syncFromSource() {
        if (source != null && !isFocused()) {
            setSilently(source.get());
        }
    }

    void setSilently(String value) {
        if (!value.equals(getText())) {
            setText(value, false);
            setCursorPosition(0);
        }
    }

    void focus() {
        setFocused(true);
        setCursorPosition(getText().length());
    }

    /** Drops focus and commits a pending edit. */
    void blur() {
        if (isFocused()) {
            setFocused(false);
            commitEdit();
        }
    }

    /** Drops focus and restores the bound value (Escape). */
    void cancel() {
        setFocused(false);
        if (source != null) {
            setSilently(source.get());
        }
    }

    private void commitEdit() {
        if (committer == null || source == null) {
            return;
        }
        String text = getText();
        if (text.equals(source.get())) {
            return;
        }
        String error = isTextValid() ? committer.apply(text) : "Invalid value '" + text + "'";
        if (error != null) {
            if (editor != null) {
                editor.showError(error);
            }
            setSilently(source.get());
        }
    }

    @Override
    public boolean mousePressed(MouseButton button) {
        boolean wasFocused = isFocused();
        boolean consumed = super.mousePressed(button);
        if (wasFocused && !isFocused()) {
            commitEdit();
        }
        return consumed;
    }

    @Override
    public boolean allowInput() {
        return editable == null || editable.getAsBoolean();
    }

    @Override
    public boolean isValid(String text) {
        return validator == null || validator.test(text);
    }

    @Override
    public void onTextChanged() {
        if (changeListener != null) {
            changeListener.run();
        }
    }

    @Override
    public void onEnterPressed() {
        commitEdit();
        if (enterListener != null) {
            enterListener.run();
        }
    }

    @Override
    public void onTabPressed() {
        commitEdit();
        if (tabListener != null) {
            tabListener.run();
        }
    }
}
