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

package org.gradle.tooling.model.eclipse;

import org.gradle.api.Incubating;

import java.util.Map;

/**
 * An Eclipse build command is a reference to a project builder object which automatically executes whenever a resource
 * in the associate project changes.
 *
 * @since 2.9
 */
@Incubating
public interface EclipseBuildCommand {

    /**
     * Returns the name of the build command.
     * <p>
     * Corresponds to the {@code org.eclipse.core.resources.ICommand#getBuilderName()} method in the Eclipse platform API.
     * @see <a href="http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/ICommand.html#getBuilderName--">Definition of ICommand#getBuilderName() in the Eclipse documentation</a>
     *
     * @return The name of the build command. Does not return null.
     */
    String getName();

    /**
     * The arguments supplied for the build command.
     * <p>
     * Corresponds to the {@code org.eclipse.core.resources.ICommand#getBuilderName()} method in the Eclipse platform API.
     * @see <a href="http://help.eclipse.org/mars/topic/org.eclipse.platform.doc.isv/reference/api/org/eclipse/core/resources/ICommand.html#getArguments--">Definition of ICommand#getArguments() in the Eclipse documentation</a>
     *
     * @return The arguments map. If no arguments supplied then returns an empty map.
     */
    Map<String, String> getArguments();
}
