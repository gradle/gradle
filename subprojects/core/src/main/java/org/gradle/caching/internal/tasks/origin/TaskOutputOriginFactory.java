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

package org.gradle.caching.internal.tasks.origin;

import com.google.common.collect.ImmutableSet;
import org.gradle.api.internal.TaskInternal;
import org.gradle.api.internal.tasks.OriginTaskExecutionMetadata;
import org.gradle.internal.UncheckedException;
import org.gradle.internal.id.UniqueId;
import org.gradle.internal.remote.internal.inet.InetAddressFactory;
import org.gradle.internal.scopeids.id.BuildInvocationScopeId;
import org.gradle.internal.time.Clock;
import org.gradle.util.GradleVersion;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.Set;

public class TaskOutputOriginFactory {

    private static final Logger LOGGER = LoggerFactory.getLogger(TaskOutputOriginFactory.class);

    private static final String BUILD_INVOCATION_ID_KEY = "buildInvocationId";
    public static final String TYPE_KEY = "type";
    public static final String PATH_KEY = "path";
    public static final String GRADLE_VERSION_KEY = "gradleVersion";
    public static final String CREATION_TIME_KEY = "creationTime";
    public static final String EXECUTION_TIME_KEY = "executionTime";
    public static final String ROOT_PATH_KEY = "rootPath";
    public static final String OPERATING_SYSTEM_KEY = "operatingSystem";
    public static final String HOST_NAME_KEY = "hostName";
    public static final String USER_NAME_KEY = "userName";
    private static final Set<String> METADATA_KEYS = ImmutableSet.of(BUILD_INVOCATION_ID_KEY, TYPE_KEY, PATH_KEY, GRADLE_VERSION_KEY, CREATION_TIME_KEY, EXECUTION_TIME_KEY, ROOT_PATH_KEY, OPERATING_SYSTEM_KEY, HOST_NAME_KEY, USER_NAME_KEY);

    private final InetAddressFactory inetAddressFactory;
    private final String userName;
    private final String operatingSystem;
    private final Clock clock;
    private final GradleVersion gradleVersion;
    private final BuildInvocationScopeId buildInvocationScopeId;
    private final File rootDir;

    public TaskOutputOriginFactory(Clock clock, InetAddressFactory inetAddressFactory, File rootDir, String userName, String operatingSystem, GradleVersion gradleVersion, BuildInvocationScopeId buildInvocationScopeId) {
        this.inetAddressFactory = inetAddressFactory;
        this.rootDir = rootDir;
        this.userName = userName;
        this.operatingSystem = operatingSystem;
        this.clock = clock;
        this.gradleVersion = gradleVersion;
        this.buildInvocationScopeId = buildInvocationScopeId;
    }

    public TaskOutputOriginWriter createWriter(final TaskInternal task, final long elapsedTime) {
        return new TaskOutputOriginWriter() {
            @Override
            public void execute(OutputStream outputStream) {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                properties.setProperty(BUILD_INVOCATION_ID_KEY, buildInvocationScopeId.getId().asString());
                properties.setProperty(TYPE_KEY, task.getClass().getCanonicalName());
                properties.setProperty(PATH_KEY, task.getPath());
                properties.setProperty(GRADLE_VERSION_KEY, gradleVersion.getVersion());
                properties.setProperty(CREATION_TIME_KEY, Long.toString(clock.getCurrentTime()));
                properties.setProperty(EXECUTION_TIME_KEY, Long.toString(elapsedTime));
                properties.setProperty(ROOT_PATH_KEY, rootDir.getAbsolutePath());
                properties.setProperty(OPERATING_SYSTEM_KEY, operatingSystem);
                properties.setProperty(HOST_NAME_KEY, inetAddressFactory.getHostname());
                properties.setProperty(USER_NAME_KEY, userName);
                try {
                    properties.store(outputStream, "Generated origin information");
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                assert METADATA_KEYS.containsAll(properties.stringPropertyNames()) : "Update expected metadata property list";
            }
        };
    }

    public TaskOutputOriginReader createReader(final TaskInternal task) {
        return new TaskOutputOriginReader() {
            @Override
            public OriginTaskExecutionMetadata execute(InputStream inputStream) {
                // TODO: Replace this with something better
                Properties properties = new Properties();
                try {
                    properties.load(inputStream);
                } catch (IOException e) {
                    throw UncheckedException.throwAsUncheckedException(e);
                }
                if (!properties.stringPropertyNames().containsAll(METADATA_KEYS)) {
                    throw new IllegalStateException("Cached result format error, corrupted origin metadata.");
                }
                LOGGER.info("Origin for {}: {}", task, properties);

                UniqueId originBuildInvocationId = UniqueId.from(properties.getProperty(BUILD_INVOCATION_ID_KEY));
                long originalExecutionTime = Long.parseLong(properties.getProperty(EXECUTION_TIME_KEY));
                return new OriginTaskExecutionMetadata(originBuildInvocationId, originalExecutionTime);
            }
        };
    }
}
