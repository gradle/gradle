/*
 * Copyright 2017 the original author or authors.
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

import org.gradle.api.Action
import org.gradle.api.XmlProvider
import org.gradle.api.file.ConfigurableFileCollection
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.api.internal.project.ProjectStateRegistry
import org.gradle.api.model.ObjectFactory
import org.gradle.api.provider.Provider
import org.gradle.composite.internal.BuildTreeWorkGraphController
import org.gradle.internal.service.ServiceRegistry
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.plugins.ide.internal.IdeArtifactRegistry
import org.gradle.util.TestUtil
import spock.lang.Specification

class IdeaModelTest extends Specification {

    IdeaModel model = TestUtil.newInstance(IdeaModel)

    def "can configure workspace with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [xmlTransformer])
        def xmlAction = {} as Action<XmlProvider>
        model.workspace = TestUtil.newInstance(IdeaWorkspace)

        model.workspace.iws = xmlMerger

        when: "configure workspace"
        model.workspace({ wsp -> wsp.iws.xmlTransformer } as Action<IdeaWorkspace>)

        then:
        1 * model.workspace.iws.getXmlTransformer()

        when: "configure workspace file"
        model.workspace.iws({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)

        then:
        1 * model.workspace.iws.getXmlTransformer()

        when: "configure workspace xml"
        model.workspace.iws.withXml(xmlAction)

        then:
        1 * xmlTransformer.addAction(xmlAction)
    }

    def "can configure project with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [xmlTransformer])
        def xmlAction = {} as Action<XmlProvider>
        def gradleProject = Stub(ProjectInternal) {
            getServices() >> Stub(ServiceRegistry) {
                get(ProjectStateRegistry) >> (ProjectStateRegistry) null
                get(IdeArtifactRegistry) >> (IdeArtifactRegistry) null
                get(BuildTreeWorkGraphController) >> (BuildTreeWorkGraphController) null
            }
        }
        model.project = TestUtil.newInstance(IdeaProject, gradleProject, xmlMerger)

        when: "configure project"
        model.project({ p -> p.vcs = 'GIT' } as Action<IdeaProject>)

        then:
        model.project.vcs == 'GIT'

        when: "configure project file"
        model.project.ipr({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)

        then:
        1 * xmlMerger.getXmlTransformer()

        when: "configure project xml"
        model.project.ipr.withXml(xmlAction)

        then:
        1 * xmlTransformer.addAction(xmlAction)
    }

    def "can configure module with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def project = Mock(org.gradle.api.Project) {
            def objectFactory = Mock(ObjectFactory)
            def fileCollection = Mock(ConfigurableFileCollection)
            objectFactory.fileCollection() >> fileCollection

            getObjects() >> objectFactory
            provider(_) >> Mock(Provider)
        }
        def xmlAction = {} as Action<XmlProvider>
        def moduleIml = Spy(IdeaModuleIml, constructorArgs: [xmlTransformer, null])
        model.module = TestUtil.newInstance(IdeaModule, project, moduleIml)

        when: "configure module"
        model.module({ mod -> mod.name = 'name' } as Action<IdeaModule>)

        then:
        model.module.name == 'name'

        when: "configure module file"
        model.module.iml({ iml -> iml.xmlTransformer } as Action<IdeaModuleIml>)

        then:
        1 * moduleIml.getXmlTransformer()

        when: "configure module xml"
        model.module.iml.withXml(xmlAction)

        then:
        1 * xmlTransformer.addAction(xmlAction)
    }
}
