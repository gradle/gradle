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

package org.gradle.plugins.ide.idea.model

import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.ConfigureUtil

/**
 * Enables fine-tuning workspace details (*.iws file) of the Idea plugin
 * <p>
 * At the moment, the only practical way of manipulating the resulting content is via the withXml hook:
 *
 * <pre autoTested=''>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * idea.workspace.iws.withXml { provider ->
 *     provider.asNode().appendNode('gradleRocks', 'true')
 * }
 * </pre>
 *
 * @author Szczepan Faber, created at: 06/09/11
 */
class IdeaWorkspace {

    /**
     * Enables advanced manipulation of the output xml
     * <p>
     * For example see docs for {@link IdeaWorkspace}
     *
     * @param closure
     */
    void iws(Closure closure) {
        ConfigureUtil.configure(closure, getIws())
    }

    /**
     * Enables advanced manipulation of the output xml
     * <p>
     * For example see docs for {@link IdeaWorkspace}
     */
    XmlFileContentMerger iws
}
