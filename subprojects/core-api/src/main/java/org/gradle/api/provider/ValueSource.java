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

package org.gradle.api.provider;

import org.gradle.api.Action;
import org.gradle.api.file.RegularFile;

import javax.annotation.Nullable;
import javax.inject.Inject;

/**
 * Represents an external source of information used by a Gradle build.
 *
 * Examples of external sources include client environment variables,
 * system properties, configuration files, shell commands, network services,
 * among others.
 *
 * <p>
 * Representing external sources as {@link ValueSource}s allows Gradle to transparently manage
 * <a href="https://docs.gradle.org/current/userguide/configuration_cache.html" target="_top">the configuration cache</a>
 * as values obtained from those sources change.
 * For example, a build might run a different set of tasks depending on whether the {@code CI}
 * environment variable is set or not.
 * </p>
 *
 * <p>
 * To integrate a new type of value source, create an abstract subclass of this interface
 * and use {@link ProviderFactory#of(Class, Action)} to get a provider to a configured source.
 * The returned {@link org.gradle.api.provider.Provider} can be passed to tasks or queried
 * by build logic during the configuration phase. In the latter case, the source would be automatically
 * considered as an input to the work graph cache.
 * </p>
 * <p>
 * It is possible to have some Gradle services to be injected into the implementation, similar to
 * tasks and plugins. It can be done by adding a parameter to the constructor and annotating the
 * constructor with the {@code @Inject} annotation:
 * <pre>
 * public abstract class MyValueSource implements ValueSource&lt;...&gt; {
 *     &#064;Inject
 *     public MyValueSource(ExecOperations execOperations) {
 *         ...
 *     }
 * }
 * </pre>
 * Currently, only a small subset of services is supported:
 * <ul>
 *     <li>{@link org.gradle.process.ExecOperations} provides means to execute external processes.
 *     It is possible to use this service even at configuration time. However, as the
 *     returned value is used to check the configuration cache, the {@link #obtain()} method will
 *     be called during each build. Calling slow commands here will slow things down.</li>
 * </ul>
 *
 * <p>
 * A value source implementation will most likely take parameters. To do this create a
 * subtype of {@link ValueSourceParameters} and declare this type as the type parameter
 * to the value source implementation.
 * </p>
 *
 * <p>
 * A value source implementation doesn't have to be thread-safe, as the single call
 * to {@link #obtain()} is synchronized.
 * </p>
 *
 * <p>
 * A value source implementation is exempt from the automatic detection of work graph cache inputs.
 * For example, if the {@link #obtain()} method calls {@code System.getenv("FOO")} then changes to
 * the {@code FOO} environment variable only invalidate the cache if the value returned by the
 * {@code obtain()} method itself changes. The same applies to reading files or system properties.
 * Starting an external process with a standard API (for example, {@code java.lang.ProcessBuilder}) is
 * also allowed.
 * </p>
 * @param <T> The type of value obtained from this source.
 * @param <P> The source specific parameter type.
 * @see ProviderFactory#environmentVariable(String)
 * @see ProviderFactory#systemProperty(String)
 * @see ProviderFactory#fileContents(RegularFile)
 * @see ProviderFactory#of(Class, Action)
 * @see <a href="https://docs.gradle.org/current/userguide/configuration_cache.html">Configuration Cache</a>
 * @since 6.1
 */
public interface ValueSource<T, P extends ValueSourceParameters> {

    /**
     * The object provided by {@link ValueSourceSpec#getParameters()} when creating a provider from the value source.
     *
     * <p>
     * Do not implement this method in your subclass.
     * Gradle provides the implementation when creating a provider from the value source via {@link ProviderFactory#of(Class, Action)}.
     * </p>
     */
    @Inject
    P getParameters();

    /**
     * Obtains the value from the source. The returned value must be effectively immutable.
     * The implementation is exempt from the automatic detection of work graph cache inputs.
     *
     * <p>This method must be implemented in the subclass.</p>
     * <p>This method is only called if the provider value is requested and only once in that case.</p>
     *
     * @return the value obtained or {@code null} if the value is not present.
     */
    @Nullable
    T obtain();
}
