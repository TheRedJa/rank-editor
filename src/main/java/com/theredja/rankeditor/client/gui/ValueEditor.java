package com.theredja.rankeditor.client.gui;

import java.util.List;
import java.util.function.Consumer;
import java.util.function.Predicate;

import net.minecraft.command.ICommandSender;
import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.config.ConfigString;
import com.feed_the_beast.ftblib.lib.gui.Theme;
import com.feed_the_beast.ftblib.lib.gui.misc.GuiEditConfigValue;

/**
 * FTBLib's single-value editor ({@link GuiEditConfigValue}) used as an overlay of the editor, with the node and its
 * constraints drawn above the box (the stock dialog has no title). The edited value is a {@link Text}: FTBLib's
 * typed values (ConfigInt, ConfigStringEnum) can't represent the empty string that means "remove the entry", so the
 * type's rules are applied by a validator instead.
 */
@SideOnly(Side.CLIENT)
final class ValueEditor extends GuiEditConfigValue {

    /** A string value that accepts empty text and anything the validator allows. */
    static final class Text extends ConfigString {

        private final Predicate<String> validator;

        Text(String initial, Predicate<String> validator) {
            super(initial);
            this.validator = validator;
        }

        @Override
        public boolean setValueFromString(ICommandSender sender, String string, boolean simulate) {
            if (!string.isEmpty() && !validator.test(string)) {
                return false;
            }
            if (!simulate) {
                setString(string);
            }
            return true;
        }

        @Override
        public Text copy() {
            return new Text(getString(), validator);
        }
    }

    private final List<String> hints;

    /**
     * @param onAccept receives the accepted text (empty = clear); the dialog closes itself afterwards
     * @param onCancel must close the overlay
     */
    ValueEditor(String node, String initial, Predicate<String> validator, List<String> hints, Consumer<String> onAccept, Runnable onCancel) {
        super(node, new Text(initial, validator), (value, accepted) -> {
            if (accepted) {
                onAccept.accept(value.getString());
            } else {
                onCancel.run();
            }
        });
        this.hints = hints;
    }

    @Override
    public Theme getTheme() {
        return DarkTheme.INSTANCE;
    }

    @Override
    public void drawBackground(Theme theme, int x, int y, int w, int h) {
        super.drawBackground(theme, x, y, w, h);
        int ty = y - 4 - hints.size() * 10;
        for (int i = 0; i < hints.size(); i++) {
            Ui.text(theme, Ui.ellipsize(theme, hints.get(i), w), x, ty + i * 10, i == 0 ? Ui.PENDING : Ui.MUTED);
        }
    }
}
