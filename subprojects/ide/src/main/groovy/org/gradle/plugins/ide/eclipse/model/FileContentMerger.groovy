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

package org.gradle.plugins.ide.eclipse.model

import org.gradle.api.internal.XmlTransformer
import org.gradle.listener.ActionBroadcast

/**
 * Models the generation/parsing/merging capabilities.
 * <p>
 * For example see docs for {@link EclipseProject}, {@link EclipseClasspath}, {@link EclipseWtp}
 *
 * @author: Szczepan Faber, created at: 4/21/11
 */
class FileContentMerger {

    ActionBroadcast whenMerged = new ActionBroadcast()
    ActionBroadcast beforeMerged = new ActionBroadcast()
    XmlTransformer xmlTransformer

    /**
     * Adds a closure to be called after content is loaded from existing file
     * but before gradle build information is merged
     * <p>
     * This is advanced api that gives access to internal implementation.
     * It might be useful if you want to alter the way gradle build information is merged into existing file content
     * <p>
     * Object that is passed as parameter to the closure is of type:
     * <p>
     * {@link Project} for: project { file { }} closure
     * {@link Jdt}     for: jdt { file { }} closure
     * <p>
     * For example see docs for {@link EclipseProject}
     *
     * @param closure The closure to execute.
     */
    public void beforeMerged(Closure closure) {
        beforeMerged.add(closure)
    }

    /**
     * Adds a closure to be called after content is loaded from existing file
     * and after gradle build information is merged
     * <p>
     * This is advanced api that gives access to internal implementation of idea plugin.
     * Use it only to tackle some tricky edge cases.
     * <p>
     * Object that is passed as parameter to the closure is of type:
     * <p>
     * {@link Project} for: project { file { }} closure
     * {@link Jdt}     for: jdt { file { }} closure
     * <p>
     * For example see docs for {@link EclipseProject}
     *
     * @param closure The closure to execute.
     */
    public void whenMerged(Closure closure) {
        whenMerged.add(closure)
    }

    /**
     * Adds a closure to be called when the file has been created. The XML is passed to the closure as a
     * parameter in form of a {@link org.gradle.api.artifacts.maven.XmlProvider}. The closure can modify the XML before
     * it is written to the output file.
     * <p>
     * For example see docs for {@link EclipseProject}
     *
     * @param closure The closure to execute when the XML has been created.
     */
    public void withXml(Closure closure) {
        xmlTransformer.addAction(closure)
    }
}
