/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.testkit.runner;

import org.gradle.api.Incubating;
import org.gradle.api.internal.GradleDistributionLocator;
import org.gradle.api.internal.classpath.DefaultGradleDistributionLocator;
import org.gradle.internal.classloader.ClasspathUtil;
import org.gradle.testkit.runner.internal.DefaultGradleRunner;

import java.io.File;
import java.util.List;

/**
 * Executes a Gradle build, allowing inspection of the outcome.
 * <p>
 * A Gradle runner can be used to functionally test build logic, by executing a contrived build.
 * Assertions can then be made on the outcome of the build, such as the state of files created by the build,
 * or what tasks were actually executed during the build.
 * <p>
 * A runner can be created via the {@link #create()} method.
 * <p>
 * The {@link #withArguments(String...)} method allows the build arguments to be specified,
 * just as they would be on the command line. It's required to provide a project directory with
 * the method {@link #withProjectDir(File)}} before invoking the build.
 * The {@link #build()} method can be used to invoke the build when it is expected to succeed,
 * while the {@link #buildAndFail()} method can be used when the build is expected to fail.
 * <p>
 * GradleRunner instances are not thread safe and cannot be used concurrently.
 * However, multiple instances are able to be used concurrently.
 * <p>
 * Further aspects of the build are also able to be configured.
 * <p>
 * Please see <a href="https://docs.gradle.org/current/userguide/test_kit.html">the Gradle TestKit User Guide chapter</a> for more information.
 *
 * @since 2.6
 */
@Incubating
public abstract class GradleRunner {

    /**
     * Creates a new Gradle runner.
     * <p>
     * The runner requires a Gradle distribution (and therefore a specific version of Gradle) in order to execute builds.
     * This method will find a Gradle distribution, based on the filesystem location of this class.
     * That is, it is expected that this class is loaded from a Gradle distribution.
     * <p>
     * When using the runner as part of tests <i>being executed by Gradle</i> (i.e. a build using the {@code gradleTestKit()} dependency),
     * this means that the same distribution of Gradle that is executing the tests will be used by runner returned by this method.
     * <p>
     * When using the runner as part of tests <i>being executed by an IDE</i>,
     * this means that the same distribution of Gradle that was used when importing the project will be used.
     *
     * @return a new Gradle runner
     */
    public static GradleRunner create() {
        GradleDistributionLocator gradleDistributionLocator = new DefaultGradleDistributionLocator(GradleRunner.class);
        final File gradleHome = gradleDistributionLocator.getGradleHome();
        if (gradleHome == null) {
            try {
                File classpathForClass = ClasspathUtil.getClasspathForClass(GradleRunner.class);
                throw new IllegalStateException("Could not create a GradleRunner, as the GradleRunner class was loaded from " + classpathForClass + " which is not a Gradle distribution");
            } catch (Exception e) {
                throw new IllegalStateException("Could not create a GradleRunner, as the GradleRunner class was not loaded from a Gradle distribution");
            }
        }
        return new DefaultGradleRunner(gradleHome);
    }

    /**
     * The Gradle user home directory to use for the build.
     * <p>
     * The Gradle user home directory contains dependency caches, and other persistent information.
     * <p>
     * Each runner <i>instance</i> is assigned a default for this property, of a directory inside the JVM's temp directory
     * (i.e. the location specified by the {@code java.io.tmpdir} system property, typically {@code /tmp}).
     * This default is different to Gradle's default, of {@code ~/.gradle}.
     * This is in order to prevent builds under test inheriting any environmental configuration from the current user.
     *
     * @return the Gradle “user home” directory to use
     */
    public abstract File getGradleUserHomeDir();

    /**
     * The directory that the build will be executed in.
     * <p>
     * This is analogous to the current directory when executing Gradle from the command line.
     *
     * @return the directory to execute the build in
     */
    public abstract File getProjectDir();

    /**
     * Sets the directory that the Gradle will be executed in.
     * <p>
     * This method is required to be called before using {@link #build()} or {@link #buildAndFail()}.
     *
     * @param projectDir the project directory
     * @return {@code this}
     * @see #getProjectDir()
     */
    public abstract GradleRunner withProjectDir(File projectDir);

    /**
     * The build arguments.
     * <p>
     * Effectively, the command line arguments to Gradle.
     * This includes all tasks, flags, properties etc.
     *
     * The returned list is an unmodifiable view of items.
     *
     * @return the build arguments
     */
    public abstract List<String> getArguments();

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see #getArguments()
     */
    public abstract GradleRunner withArguments(List<String> arguments);

    /**
     * Sets the build arguments.
     *
     * @param arguments the build arguments
     * @return this
     * @see #getArguments()
     */
    public abstract GradleRunner withArguments(String... arguments);

    /**
     * Executes a build, expecting it to complete without failure.
     *
     * @throws IllegalStateException if project directory was not provided beforehand
     * @throws UnexpectedBuildFailure if the build does not succeed
     * @return the build result
     */
    public abstract BuildResult build() throws IllegalStateException, UnexpectedBuildFailure;

    /**
     * Executes a build, expecting it to complete with failure.
     *
     * @throws IllegalStateException if project directory was not provided beforehand
     * @throws UnexpectedBuildSuccess if the build succeeds
     * @return the build result
     */
    public abstract BuildResult buildAndFail() throws IllegalStateException, UnexpectedBuildSuccess;

}
