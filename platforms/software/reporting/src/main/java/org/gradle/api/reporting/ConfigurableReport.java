/*
 * Copyright 2013 the original author or authors.
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

package org.gradle.api.reporting;

import java.io.File;

/**
 * A file based report to be created with a configurable destination.
 *
 * Note this is a legacy type which offers no additional functionality.  Reports
 * should implement {@link Report} directly instead of using this interface.
 */
public interface ConfigurableReport extends Report {
    /**
     * Sets the destination for the report.
     *
     * @param file The destination for the report.
     * @see #getOutputLocation()
     * @since 4.0
     *
     * @deprecated Use {@link #getOutputLocation()}.set() instead. This method will be removed in Gradle 9.0.
     */
    @Deprecated
    void setDestination(File file);
}
