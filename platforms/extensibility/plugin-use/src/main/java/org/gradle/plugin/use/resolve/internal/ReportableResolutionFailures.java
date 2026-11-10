/*
 * Copyright 2026 Gradle and contributors.
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
package org.gradle.plugin.use.resolve.internal;

import org.gradle.internal.resolve.ArtifactNotFoundException;
import org.gradle.internal.resolve.ModuleVersionNotFoundException;
import org.jspecify.annotations.NullMarked;

import java.util.ArrayList;
import java.util.Collection;
import java.util.List;

/**
 * Picks out the failures that mean more than "the plugin marker is absent". A plugin that is
 * genuinely missing yields nothing here, so it keeps the message it had before.
 */
@NullMarked
class ReportableResolutionFailures {

    private ReportableResolutionFailures() {
    }

    static List<Throwable> selectUnexpected(Collection<Throwable> failures) {
        List<Throwable> unexpected = new ArrayList<>(failures.size());
        for (Throwable failure : failures) {
            if (!isNotFound(failure)) {
                unexpected.add(failure);
            }
        }
        return unexpected;
    }

    // Only the narrow subtypes count as absence. ModuleVersionResolveException itself carries
    // the failures worth reporting, so matching on it would filter everything out.
    private static boolean isNotFound(Throwable failure) {
        return failure instanceof ModuleVersionNotFoundException
            || failure instanceof ArtifactNotFoundException;
    }
}
