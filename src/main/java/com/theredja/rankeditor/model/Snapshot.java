package com.theredja.rankeditor.model;

import java.util.List;

/**
 * Everything the client editor needs, sent by the server when the editor opens and after every change.
 *
 * @param canEdit     whether this player may apply edits (otherwise read-only)
 * @param ranksActive false when FTB Utilities ranks are disabled; edits are still saved but have no effect until enabled
 * @param fingerprint {@link RankState#fingerprint()} of {@code state}; sent back with edits to detect concurrent changes
 */
public record Snapshot(boolean canEdit, boolean ranksActive, long fingerprint, RankState state, List<NodeInfo> nodes) {
}
