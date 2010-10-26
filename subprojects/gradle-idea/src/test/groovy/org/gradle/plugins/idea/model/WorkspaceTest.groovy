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
package org.gradle.plugins.idea.model

import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.internal.XmlTransformer
import spock.lang.Specification

/**
 * @author Hans Dockter
 */

class WorkspaceTest extends Specification {
    Workspace workspace

    def toXmlWithCustomReader() {
        when:
        workspace = createWorkspace(reader: customWorkspaceReader)

        then:
        new XmlParser().parse(toXmlReader).toString() == new XmlParser().parse(customWorkspaceReader).toString()
    }

    def toXmlWithNullReader() {
        when:
        workspace = createWorkspace([:])

        then:
        new XmlParser().parse(toXmlReader).toString() == new XmlParser().parse(defaultWorkspaceReader).toString()
    }
    
    private StringReader getToXmlReader() {
        StringWriter toXmlText = new StringWriter()
        workspace.toXml(toXmlText)
        return new StringReader(toXmlText.toString())
    }

    def withXml() {
        XmlTransformer withXmlActions = new XmlTransformer()
        workspace = createWorkspace(reader: customWorkspaceReader, withXmlActions: withXmlActions)

        when:
        def modifiedVersion
        withXmlActions.addAction { XmlProvider provider ->
            def xml = provider.asNode()
            xml.@version += 'x'
            modifiedVersion = xml.@version
        }

        then:
        new XmlParser().parse(toXmlReader).@version == modifiedVersion
    }

    private InputStreamReader getCustomWorkspaceReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customWorkspace.xml'))
    }

    private InputStreamReader getDefaultWorkspaceReader() {
        return new InputStreamReader(getClass().getResourceAsStream('defaultWorkspace.xml'))
    }

    private Workspace createWorkspace(Map customArgs) {
        XmlTransformer dummyBroadcast = new XmlTransformer()
        Map args = [reader: null, withXmlActions: dummyBroadcast] + customArgs
        return new Workspace(args.reader, args.withXmlActions)
    }
}