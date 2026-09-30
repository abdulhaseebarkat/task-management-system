package com.slmtires.itms.dto;

/**
 * keepProgress = true  -> each person resumes as IN_PROGRESS at the progress they had before completing.
 * keepProgress = false (or body omitted) -> everyone restarts at ASSIGNED / 0%.
 */
public record ReopenRequest(Boolean keepProgress) {

    public boolean keep() {
        return Boolean.TRUE.equals(keepProgress);
    }
}
