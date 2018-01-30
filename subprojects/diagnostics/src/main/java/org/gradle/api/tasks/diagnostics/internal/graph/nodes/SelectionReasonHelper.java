/*
 * Copyright 2018 the original author or authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.gradle.api.tasks.diagnostics.internal.graph.nodes;

import com.google.common.collect.Iterables;
import org.gradle.api.artifacts.result.ComponentSelectionDescriptor;
import org.gradle.api.artifacts.result.ComponentSelectionReason;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionDescriptorInternal;
import org.gradle.api.internal.artifacts.ivyservice.resolveengine.result.ComponentSelectionReasonInternal;

public abstract class SelectionReasonHelper {
    public static String getReasonDescription(ComponentSelectionReason reason) {
        ComponentSelectionReasonInternal r = (ComponentSelectionReasonInternal) reason;
        String description = getReasonDescription(r);
        if (reason.isConstrained()) {
            if (!r.hasCustomDescriptions()) {
                return "via constraint";
            } else {
                return "via constraint, " + description;
            }
        }
        return description;
    }

    private static String getReasonDescription(ComponentSelectionReasonInternal reason) {
        if (!reason.hasCustomDescriptions()) {
            return reason.isExpected() ? null : Iterables.getLast(reason.getDescriptions()).getDescription();
        }
        return getLastCustomReason(reason);
    }

    private static String getLastCustomReason(ComponentSelectionReasonInternal reason) {
        String lastCustomReason = null;
        for (ComponentSelectionDescriptor descriptor : reason.getDescriptions()) {
            if (((ComponentSelectionDescriptorInternal)descriptor).hasCustomDescription()) {
                lastCustomReason = descriptor.getDescription();
            }
        }
        return lastCustomReason;
    }
}
