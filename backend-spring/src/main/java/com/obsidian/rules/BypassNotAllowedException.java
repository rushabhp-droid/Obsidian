package com.obsidian.rules;

/** The organization's policy doesn't allow user bypasses. Mapped to 403. */
public class BypassNotAllowedException extends RuntimeException {

    public BypassNotAllowedException() {
        super("Your organization doesn't allow personal bypasses. Ask an administrator.");
    }
}
