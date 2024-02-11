/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.api.tasks.wrapper.internal;

import org.gradle.api.NonNullApi;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.wrapper.Install;

@NonNullApi
public class WrapperDefaults {
    public static final String SCRIPT_PATH = "gradlew";
    public static final String JAR_FILE_PATH = "gradle/wrapper/gradle-wrapper.jar";
    public static final Wrapper.DistributionType DISTRIBUTION_TYPE = Wrapper.DistributionType.BIN;

    public static final String DISTRIBUTION_PATH = Install.DEFAULT_DISTRIBUTION_PATH;
    public static final Wrapper.PathBase DISTRIBUTION_BASE = Wrapper.PathBase.GRADLE_USER_HOME;
    public static final String ARCHIVE_PATH = DISTRIBUTION_PATH;
    public static final Wrapper.PathBase ARCHIVE_BASE = DISTRIBUTION_BASE;

    public static final int NETWORK_TIMEOUT = 10000;
    public static final boolean VALIDATE_DISTRIBUTION_URL = true;
}
