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

package org.gradle.tooling.model.build;

import org.gradle.api.Incubating;
import org.gradle.tooling.model.BuildIdentifier;
import org.gradle.tooling.model.BuildModel;
import org.gradle.tooling.model.Model;
import org.gradle.tooling.model.UnsupportedMethodException;

/**
 * Informs about the build environment, like Gradle version or the Java home in use.
 * <p>
 * Example:
 * <pre autoTested=''>
 * ProjectConnection connection = GradleConnector.newConnector()
 *    .forProjectDirectory(new File("someProjectFolder"))
 *    .connect();
 *
 * try {
 *    BuildEnvironment env = connection.getModel(BuildEnvironment.class);
 *    System.out.println("Gradle version: " + env.getGradle().getGradleVersion());
 *    System.out.println("Java home: " + env.getJava().getJavaHome());
 * } finally {
 *    connection.close();
 * }
 * </pre>
 *
 * @since 1.0-milestone-8
 */
public interface BuildEnvironment extends Model, BuildModel {
    /**
     * Returns the identifier for the Gradle build that this environment is used by.
     *
     * @since 2.13
     */
    @Incubating
    BuildIdentifier getBuildIdentifier();

    /**
     * Returns information about the Gradle environment, for example the Gradle version.
     *
     * @since 1.0-milestone-8
     */
    GradleEnvironment getGradle();

    /**
     * Returns information about the Java environment, for example the Java home or the JVM args used.
     *
     * @since 1.0-milestone-8
     * @throws UnsupportedMethodException For Gradle versions older than 1.0-milestone-8, where this method is not supported.
     */
    JavaEnvironment getJava() throws UnsupportedMethodException;
}
