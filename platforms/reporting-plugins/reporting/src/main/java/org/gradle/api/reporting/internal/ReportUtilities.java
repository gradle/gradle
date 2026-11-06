/*
 * Copyright 2025 the original author or authors.
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

package org.gradle.api.reporting.internal;

import org.gradle.api.Project;
import org.gradle.api.reporting.ReportingExtension;

/**
 * Utility to calculate report titles for API documentation.
 *
 * TODO: This should be moved to java-base or similar as its only used by JVM-based plugins once the deprecated {@link ReportingExtension#getApiDocTitle()} is removed.
 */
public interface ReportUtilities {
    /**
     * Returns API title based on project name and version of the given project.
     *
     * @implNote Since the project version is eagerly read, changes to the project version do not affect the title after this method is called.
     * In the future, if the project version was Provider-based, this method could be updated or inlined in each place its called.
     */
    static String getApiDocTitleFor(Project project) {
        Object version = project.getVersion();
        if (Project.DEFAULT_VERSION.equals(version)) {
            return project.getName() + " API";
        } else {
            return project.getName() + " " + version + " API";
        }
    }
}
