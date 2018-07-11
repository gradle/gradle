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

package org.gradle.api.internal.artifacts.repositories;

import java.util.Map;

public interface ExposableRepository extends ResolutionAwareRepository {

    Map<RepositoryPropertyType, ?> getProperties();

    enum RepositoryPropertyType {

        URL("URL"),
        DIRS("Dirs"),
        ARTIFACT_URLS("Artifact URLs"),
        IVY_PATTERNS("Ivy patterns"),
        ARTIFACT_PATTERNS("Artifact patterns"),
        METADATA_SOURCES("Metadata sources"),
        AUTHENTICATED("Authenticated"),
        AUTHENTICATION_SCHEMES("Authentication schemes"),
        LAYOUT_TYPE("Layout"),
        M2_COMPATIBLE("M2 compatible");

        public final String displayName;

        RepositoryPropertyType(String displayName) {
            this.displayName = displayName;
        }

    }

}
