package com.theredja.rankeditor.client.gui;

import net.minecraftforge.fml.relauncher.Side;
import net.minecraftforge.fml.relauncher.SideOnly;

import com.feed_the_beast.ftblib.lib.gui.Panel;

/** Header above the permission table for the selected holder; sizes itself to the width it is given. */
@SideOnly(Side.CLIENT)
abstract class HolderHeader extends Panel {

    HolderHeader(Panel parent) {
        super(parent);
    }

    /** Positions the children for {@code availableWidth} and returns the height needed. */
    abstract int layout(int availableWidth);

    @Override
    public void alignWidgets() {
        layout(width);
    }
}
