/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.jvm.toolchain;

import org.gradle.api.Incubating;
import org.gradle.api.JavaVersion;

import java.io.File;
import java.util.Optional;

/**
 * Information about a Java installation, which may include a JRE or a JDK or both.
 *
 * @since 6.1
 */
@Incubating
public interface JavaInstallation {
    /**
     * Returns the Java version that this installation provides
     */
    JavaVersion getJavaVersion();

    /**
     * Returns the root directory of this installation.
     */
    File getInstallationDirectory();

    /**
     * Returns a Java executable packaged in this installation that can be used for running applications.
     * This will be the Java executable from the stand alone JRE, if present, otherwise the Java executable from the JDK.
     */
    File getJavaExecutable();

    /**
     * Returns information about the JDK packaged in this installation, if any.
     */
    Optional<JavaDevelopmentKit> getJdk();
}
