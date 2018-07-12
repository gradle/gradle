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

package org.gradle.api.internal.changedetection.state.mirror.logical;

import org.gradle.api.internal.changedetection.state.NormalizedFileSnapshot;
import org.gradle.api.internal.changedetection.state.mirror.PhysicalSnapshot;
import org.gradle.internal.scan.UsedByScanPlugin;

import java.util.Map;

public interface FingerprintingStrategy {

    /**
     * Not referenced directly, but names are used in SnapshotTaskInputsBuildOperationType.Result.VisitState.getPropertyNormalizationStrategyName().
     * Existing entry names should stay stable.
     * Order is irrelevant.
     */
    @UsedByScanPlugin
    enum Identifier {
        ABSOLUTE,
        RELATIVE_PATH,
        NAME_ONLY,
        IGNORED_PATH,
        COMPILE_CLASSPATH,
        RUNTIME_CLASSPATH,
    }

    Map<String, NormalizedFileSnapshot> collectSnapshots(Iterable<PhysicalSnapshot> roots);

    FingerprintCompareStrategy getCompareStrategy();

    Identifier getIdentifier();
}
