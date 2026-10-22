package com.theredja.rankeditor.model;

import java.util.UUID;

/** A player the server knows about. {@code op} decides whether the default OP rank applies. */
public record PlayerInfo(UUID uuid, String name, boolean online, boolean op) {
}
