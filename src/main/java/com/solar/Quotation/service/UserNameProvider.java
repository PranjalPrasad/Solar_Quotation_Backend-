package com.solar.Quotation.service;

import java.util.Collection;
import java.util.Map;

/** Resolves user ids to display names (for createdBy / updatedBy in responses). Needs only the repository. */
public interface UserNameProvider {
    Map<Long, String> names(Collection<Long> userIds);
}
