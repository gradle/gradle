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

package org.gradle.api.internal.provider;

import org.gradle.api.provider.Provider;

/** Structural recognition only: failure to recognize an update does not prove independence. */
public final class PropertyUpdateClassifier {
    private static final int MAX_MAPS = 128;

    private PropertyUpdateClassifier() {
    }

    /** Returns zero for an unrecognized shape, including chains beyond the inspection budget. */
    public static int mapCount(Provider<?> candidate, Provider<?> previous) {
        Provider<?> cursor = candidate;
        for (int i = 0; i < MAX_MAPS && cursor.getClass() == TransformBackedProvider.class; i++) {
            cursor = ((TransformBackedProvider<?, ?>) cursor).provider;
            if (cursor == previous) {
                return i + 1;
            }
        }
        return 0;
    }
}
