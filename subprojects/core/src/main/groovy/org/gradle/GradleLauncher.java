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
package org.gradle;

import org.gradle.api.logging.StandardOutputListener;
import org.gradle.initialization.GradleLauncherFactory;
import org.gradle.internal.nativeplatform.services.NativeServices;
import org.gradle.internal.service.ServiceRegistryBuilder;
import org.gradle.internal.service.scopes.GlobalScopeServices;
import org.gradle.logging.LoggingServiceRegistry;
import org.gradle.util.DeprecationLogger;

/**
 * <p>Executes a Gradle build.
 *
 * <p>{@code GradleLauncher} is deprecated. It has been replaced by the Tooling API.
 * If you're interested in embedding Gradle you should read the user guide chapter on embedding Gradle.
 * The main entry point to the Tooling API (and embedding Gradle) is {@code org.gradle.tooling.GradleConnector}.
 *
 * <p>You should try using the Tooling API (via {@code GradleConnector}) instead of {@code GradleLauncher}.
 * However, if you need some capability that isn't yet implemented in the Tooling API here is how you use {@code GradleLauncher}:
 *
 * <ol>
 *
 * <li>Optionally create a {@link StartParameter} instance and configure it with the desired properties. The properties
 * of {@code StartParameter} generally correspond to the command-line options of Gradle. You can use {@link
 * #createStartParameter(String...)} to create a {@link StartParameter} from a set of command-line options.</li>
 *
 * <li>Obtain a {@code GradleLauncher} instance by calling {@link #newInstance}, passing in the {@code StartParameter},
 * or an array of Strings that will be treated as command line arguments.</li>
 *
 * <li>Optionally add one or more listeners to the {@code GradleLauncher}.</li>
 *
 * <li>Call {@link #run} to execute the build. This will return a {@link BuildResult}. Note that if the build fails, the
 * resulting exception will be contained in the {@code BuildResult}.</li>
 *
 * <li>Query the build result. You might want to call {@link BuildResult#rethrowFailure()} to rethrow any build
 * failure.</li>
 *
 * </ol>
 *
 * @deprecated Use the tooling API instead.
 */
@Deprecated
public abstract class GradleLauncher {

    private static GradleLauncherFactory factory;

    /**
     * <p>Executes the build for this {@code GradleLauncher} instance and returns the result. Note that when the build
     * fails, the exception is available using {@link org.gradle.BuildResult#getFailure()}.</p>
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult run();

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object.
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult getBuildAnalysis();

    /**
     * Evaluates the settings and all the projects. The information about available tasks and projects is accessible via
     * the {@link org.gradle.api.invocation.Gradle#getRootProject()} object. Fills the execution plan without running
     * the build. The tasks to be executed tasks are available via {@link org.gradle.api.invocation.Gradle#getTaskGraph()}.
     *
     * @return The result. Never returns null.
     */
    public abstract BuildResult getBuildAndRunAnalysis();

    /**
     * Returns a {@code GradleLauncher} instance based on the passed start parameter.
     *
     * @param startParameter The start parameter object the GradleLauncher instance is initialized with
     * @return The {@code GradleLauncher}. Never returns null.
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static GradleLauncher newInstance(StartParameter startParameter) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GradleLauncher.newInstance()");
        return doGetFactory().newInstance(startParameter);
    }

    @Deprecated
    public static GradleLauncherFactory getFactory() {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GradleLauncher.getFactory()");
        return doGetFactory();
    }

    private static synchronized GradleLauncherFactory doGetFactory() {
        if (factory == null) {
            factory = ServiceRegistryBuilder.builder()
                    .displayName("Global services")
                    .parent(LoggingServiceRegistry.newProcessLogging())
                    .parent(NativeServices.getInstance())
                    .provider(new GlobalScopeServices(false))
                    .build()
                    .get(GradleLauncherFactory.class);
        }
        return factory;
    }

    /**
     * Returns a {@code GradleLauncher} instance based on the passed command line syntax arguments.
     *
     * @param commandLineArgs A String array where each element denotes an entry of the Gradle command line syntax
     * @return The {@code GradleLauncher}. Never returns null.
     * @deprecated Use the tooling API instead.
     */
    @Deprecated
    public static GradleLauncher newInstance(String... commandLineArgs) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GradleLauncher.newInstance()");
        GradleLauncherFactory gradleLauncherFactory = doGetFactory();
        return gradleLauncherFactory.newInstance(gradleLauncherFactory.createStartParameter(commandLineArgs));
    }

    /**
     * Returns a {@code StartParameter} object out of command line syntax arguments. Each command line option has an
     * associated field in the {@code StartParameter} object.
     *
     * @param commandLineArgs A String array where each element denotes an entry of the Gradle command line syntax
     * @return The {@code GradleLauncher}. Never returns null.
     * @deprecated No replacement.
     */
    @Deprecated
    public static StartParameter createStartParameter(String... commandLineArgs) {
        DeprecationLogger.nagUserOfDiscontinuedMethod("GradleLauncher.createStartParameter()");
        return doGetFactory().createStartParameter(commandLineArgs);
    }

    @Deprecated
    public static synchronized void injectCustomFactory(GradleLauncherFactory gradleLauncherFactory) {
        factory = gradleLauncherFactory;
    }

    /**
     * <p>Adds a listener to this build instance. The listener is notified of events which occur during the execution of
     * the build. See {@link org.gradle.api.invocation.Gradle#addListener(Object)} for supported listener types.</p>
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addListener(Object listener);

    /**
     * Use the given listener. See {@link org.gradle.api.invocation.Gradle#useLogger(Object)} for details.
     *
     * @param logger The logger to use.
     */
    public abstract void useLogger(Object logger);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to
     * standard output by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addStandardOutputListener(StandardOutputListener listener);

    /**
     * <p>Adds a {@link StandardOutputListener} to this build instance. The listener is notified of any text written to standard error by Gradle's logging system
     *
     * @param listener The listener to add. Has no effect if the listener has already been added.
     */
    public abstract void addStandardErrorListener(StandardOutputListener listener);

    /**
     * Returns the {@link StartParameter} used by this build instance.
     * @return The parameter. Never null.
     */
    public abstract StartParameter getStartParameter();
}
