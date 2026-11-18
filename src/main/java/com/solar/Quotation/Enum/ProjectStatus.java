package com.solar.Quotation.Enum;

import java.util.EnumSet;
import java.util.Set;

public enum ProjectStatus {
    PLANNING, IN_PROGRESS, COMPLETED, ON_HOLD;

    public Set<ProjectStatus> allowedNext() {
        return switch (this) {
            case PLANNING -> EnumSet.of(IN_PROGRESS, ON_HOLD);
            case IN_PROGRESS -> EnumSet.of(COMPLETED, ON_HOLD);
            case ON_HOLD -> EnumSet.of(PLANNING, IN_PROGRESS);
            case COMPLETED -> EnumSet.noneOf(ProjectStatus.class);
        };
    }

    public boolean canMoveTo(ProjectStatus next) {
        return next != null && allowedNext().contains(next);
    }
}