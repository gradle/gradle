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
package org.gradle.plugins.idea

import org.gradle.api.Action
import org.gradle.api.DefaultTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import org.gradle.listener.ListenerBroadcast
import org.gradle.plugins.idea.model.Workspace

/**
 * Generates an IDEA workspace file.
 *
 * @author Hans Dockter
 */
public class IdeaWorkspace extends DefaultTask {
    /**
     * The iws file. Used to look for existing files as well as the target for generation. Must not be null.
     */
    @OutputFile
    File outputFile

    private ListenerBroadcast<Action> withXmlActions = new ListenerBroadcast<Action>(Action.class);

    def IdeaWorkspace() {
        outputs.upToDateWhen { false }
    }

    @TaskAction
    void updateXML() {
        Reader xmlreader = outputFile.exists() ? new FileReader(outputFile) : null;
        Workspace workspace = new Workspace(xmlreader, withXmlActions)
        outputFile.withWriter { Writer writer -> workspace.toXml(writer) }
    }

    void withXml(Closure closure) {
        withXmlActions.add("execute", closure);
    }
}