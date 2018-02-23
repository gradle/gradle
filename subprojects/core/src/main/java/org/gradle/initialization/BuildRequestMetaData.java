/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.initialization;

import org.gradle.internal.scan.UsedByScanPlugin;

/**
 * A bunch of information about the request which launched a build.
 */
@UsedByScanPlugin("Loaded via root project service registry, and getBuildTimeClock() called. Stopped being used in plugin 1.10")
public interface BuildRequestMetaData {

    /**
     * Returns the meta-data about the client used to launch this build.
     */
    BuildClientMetaData getClient();

    /**
     * Returns a clock measuring the time since the request was made by the user of the client.
     *
     * Use {@link #getStartTime()}.
     */
    @Deprecated
    @UsedByScanPlugin("see class doc")
    org.gradle.util.Clock getBuildTimeClock();

    /**
     * The time that the request was made by the user of the client.
     */
    long getStartTime();

}
