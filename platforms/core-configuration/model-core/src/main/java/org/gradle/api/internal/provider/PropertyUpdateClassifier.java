/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.Provider;

/**
 * Recognizes a supported structural operation at a mutation boundary, not during evaluation.
 * The anchor is the exact previous-plan copy handed to one replace() invocation; other property
 * reads, earlier copies and opaque callbacks are not evidence of an update to this previous plan.
 *
 * <p>A negative result means unclassified, not proven replacement. In particular, this bounded
 * diagnostic classifier must not be used to authorize a collaborative source replacement.</p>
 */
final class PropertyUpdateClassifier {
    private static final int MAX_MAPS = 128;

    private PropertyUpdateClassifier() {
    }

    static boolean isMapUpdate(Provider<?> candidate, Provider<?> previous) {
        Provider<?> current = candidate;
        for (int maps = 0; maps < MAX_MAPS; maps++) {
            // Only this concrete node has known map semantics. Do not inspect custom subclasses,
            // invoke provider methods, or look inside user callbacks to guess their dependencies.
            if (current.getClass() != TransformBackedProvider.class) {
                return false;
            }
            current = ((TransformBackedProvider<?, ?>) current).provider;
            if (current == previous) {
                return true;
            }
        }
        return false;
    }
}
