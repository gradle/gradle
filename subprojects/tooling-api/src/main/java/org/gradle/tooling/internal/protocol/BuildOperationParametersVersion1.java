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
package org.gradle.tooling.internal.protocol;

import java.io.File;
import java.util.concurrent.TimeUnit;

/**
 * DO NOT CHANGE THIS INTERFACE. It is part of the cross-version protocol.
 *
 * @since 1.0-milestone-3
 * @deprecated 1.2-rc-1. Use {@link BuildParameters} instead.
 */
@Deprecated
public interface BuildOperationParametersVersion1 extends LongRunningOperationParametersVersion1 {
    /**
     * @since 1.0-milestone-3
     */
    File getProjectDir();

    /**
     * Specifies whether to search for root project, or null to use default.
     *
     * @since 1.0-milestone-3
     */
    Boolean isSearchUpwards();

    /**
     * Returns the Gradle user home directory, or null to use default.
     *
     * @since 1.0-milestone-3
     */
    File getGradleUserHomeDir();

    /**
     * Specifies whether to run the build in this process, or null to use default.
     *
     * @since 1.0-milestone-3
     */
    Boolean isEmbedded();

    /**
     * Specifies the maximum idle time for any daemon process launched by the provider, or null to use the default.
     *
     * @since 1.0-milestone-3
     */
    Integer getDaemonMaxIdleTimeValue();

    /**
     * Specifies the units for the maximum idle time.
     *
     * @since 1.0-milestone-3
     */
    TimeUnit getDaemonMaxIdleTimeUnits();

    /**
     * @since 1.0-milestone-3
     */
    long getStartTime();

    /**
     * @since 1.0-milestone-7
     */
    boolean getVerboseLogging();
}
