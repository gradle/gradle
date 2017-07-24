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
package org.gradle.plugins.ide.idea.model;

import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.plugins.ide.api.XmlFileContentMerger;

import static org.gradle.util.ConfigureUtil.configure;

/**
 * Enables fine-tuning workspace details (*.iws file) of the IDEA plugin.
 * <p>
 * At the moment, the only practical way of manipulating the resulting content is via the withXml hook:
 *
 * <pre class='autoTested'>
 * apply plugin: 'java'
 * apply plugin: 'idea'
 *
 * idea.workspace.iws.withXml { provider -&gt;
 *     provider.asNode().appendNode('gradleRocks', 'true')
 * }
 * </pre>
 */
public class IdeaWorkspace {

    private XmlFileContentMerger iws;

    /**
     * Enables advanced manipulation of the output XML.
     * <p>
     * For example see docs for {@link IdeaWorkspace}
     */
    public XmlFileContentMerger getIws() {
        return iws;
    }

    public void setIws(XmlFileContentMerger iws) {
        this.iws = iws;
    }

    /**
     * Enables advanced manipulation of the output XML.
     * <p>
     * For example see docs for {@link IdeaWorkspace}
     */
    public void iws(Closure closure) {
        configure(closure, getIws());
    }

    /**
     * Enables advanced manipulation of the output XML.
     * <p>
     * For example see docs for {@link IdeaWorkspace}
     *
     * @since 3.5
     */
    public void iws(Action<? super XmlFileContentMerger> action) {
        action.execute(getIws());
    }

    public void mergeXmlWorkspace(Workspace xmlWorkspace) {
        iws.getBeforeMerged().execute(xmlWorkspace);

        //we don't merge anything in the iws, yet.
        //I kept the logic for the sake of consistency
        // and compatibility with pre M4 ways of configuring IDEA information.

        iws.getWhenMerged().execute(xmlWorkspace);
    }
}
