/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.api.invocation;

/**
 * Provides some useful information about the build invocation that triggered this build.
 * <p>
 * An instance of the type can be injected into a task or plugin by annotating a public constructor or method with {@code javax.inject.Inject}.
 *
 * <pre class='autoTested'>
 * public class MyPlugin implements Plugin{@literal <}Project{@literal >} {
 *     // injection into a constructor
 *     {@literal @}javax.inject.Inject
 *     public MyPlugin(BuildInvocationDetails invocationDetails) {}
 *
 *     public void apply(Project project) {
 *     }
  * }
 * </pre>
 * @since 5.0
 */
public interface BuildInvocationDetails {

    /**
     * The wall-clock time in millis that the build was started.
     *
     * The build is considered to have started as soon as the user, or some tool, initiated the build.
     * During continuous build, subsequent builds are timed from when changes are noticed.
     */
    long getBuildStartedTime();
}
