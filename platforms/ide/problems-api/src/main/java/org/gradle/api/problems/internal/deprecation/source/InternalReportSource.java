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

package org.gradle.api.problems.internal.deprecation.source;

import org.gradle.api.problems.deprecation.source.ReportSource;

public abstract class InternalReportSource extends ReportSource {

    /**
     * Returns a report source marking Gradle itself as the source of the deprecation.
     *
     * @since 8.14
     */
    public static ReportSource gradle() {
        return GradleReportSource.INSTANCE;
    }

}
