/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.tasks.cache.origin;

import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.cache.OriginMetadata;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.time.TimeProvider;
import org.gradle.util.GradleVersion;

import java.io.File;

public class OriginMetadataConverter {
    private final InetAddressFactory inetAddressFactory;
    private final String userName;
    private final String operatingSystem;
    private final TimeProvider timeProvider;
    private final GradleVersion gradleVersion;
    private final File rootDir;

    public OriginMetadataConverter(TimeProvider timeProvider, InetAddressFactory inetAddressFactory, File rootDir, String userName, String operatingSystem, GradleVersion gradleVersion) {
        this.inetAddressFactory = inetAddressFactory;
        this.rootDir = rootDir;
        this.userName = userName;
        this.operatingSystem = operatingSystem;
        this.timeProvider = timeProvider;
        this.gradleVersion = gradleVersion;
    }

    public OriginMetadata convert(TaskInternal task, long elapsedTime) {
        return new DefaultOriginMetadata(task.getPath(),
            task.getClass().getCanonicalName(),
            gradleVersion.getVersion(),
            timeProvider.getCurrentTime(),
            elapsedTime,
            rootDir.getAbsolutePath(),
            operatingSystem,
            inetAddressFactory.getHostname(),
            userName);
    }
}
