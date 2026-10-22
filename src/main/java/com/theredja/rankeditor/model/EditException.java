package com.theredja.rankeditor.model;

/** An edit batch was rejected; the message is shown to the user as-is. */
public final class EditException extends Exception {

    public EditException(String message) {
        super(message);
    }
}
