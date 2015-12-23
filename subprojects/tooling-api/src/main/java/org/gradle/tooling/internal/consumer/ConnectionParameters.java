/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.tooling.internal.consumer;

import java.io.File;
import java.util.concurrent.TimeUnit;

public interface ConnectionParameters extends org.gradle.tooling.internal.protocol.ConnectionParameters {
    File getProjectDir();

    /**
     * Specifies whether to search for root project, or null to use default.
     */
    Boolean isSearchUpwards();

    /**
     * Returns the Gradle user home directory, or null to use default.
     */
    File getGradleUserHomeDir();

    /**
     * Returns the daemon base directory, or null to use default.
     */
    File getDaemonBaseDir();

    Boolean isEmbedded();

    Integer getDaemonMaxIdleTimeValue();

    TimeUnit getDaemonMaxIdleTimeUnits();

    /**
     * Whether to log debug statements eagerly
     */
    boolean getVerboseLogging();
}
