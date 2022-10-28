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

import org.gradle.api.Action
import org.gradle.api.JavaVersion
import org.gradle.api.XmlProvider
import org.gradle.api.internal.PropertiesTransformer
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.xml.XmlTransformer
import org.gradle.plugins.ide.api.PropertiesFileContentMerger
import org.gradle.plugins.ide.api.XmlFileContentMerger
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

class EclipseModelTest extends Specification {

    @Subject
    EclipseModel model
    def project = Mock(ProjectInternal) {
        getTaskDependencyFactory() >> TestFiles.taskDependencyFactory()
    }

    def setup() {
        project.getObjects() >> TestUtil.objectFactory()
        model = TestUtil.newInstance(EclipseModel, project)
        model.classpath = TestUtil.newInstance(EclipseClasspath, project)
    }

    def "enables setting path variables even if wtp is not configured"() {
        given:
        model.wtp = null

        when:
        model.pathVariables(one: new File('.'))
        model.pathVariables(two: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.'), two: new File('.')]
    }

    def "enables setting path variables even if wtp component is not configured"() {
        given:
        model.wtp = TestUtil.newInstance(EclipseWtp)
        //for example when wtp+java applied but project is not a dependency to any war/ear.
        assert model.wtp.component == null

        when:
        model.pathVariables(one: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.')]
    }

    def "enables setting path variables"() {
        given:
        model.wtp = TestUtil.newInstance(EclipseWtp)
        model.wtp.component = TestUtil.newInstance(EclipseWtpComponent, project, Mock(XmlFileContentMerger))

        when:
        model.pathVariables(one: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.')]
        model.wtp.component.pathVariables == [one: new File('.')]
    }

    def "can configure project with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [xmlTransformer])
        def xmlAction = {} as Action<XmlProvider>
        model.project = TestUtil.newInstance(EclipseProject, xmlMerger)

        when: "configure project"
        model.project({ p -> p.comment = 'something' } as Action<EclipseProject>)

        then:
        model.project.comment == 'something'

        when: "configure project file"
        model.project.file({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)

        then:
        1 * xmlMerger.getXmlTransformer()

        when: "configure project XML"
        model.project.file.withXml(xmlAction)

        then:
        1 * xmlTransformer.addAction(xmlAction)
    }

    def "can configure classpath with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [xmlTransformer])
        def xmlAction = {} as Action<XmlProvider>
        model.classpath.file = xmlMerger

        when: "configure classpath"
        model.classpath({ cp -> cp.downloadJavadoc = true } as Action<EclipseClasspath>)

        then:
        model.classpath.downloadJavadoc

        when: "configure classpath file"
        model.classpath.file({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)

        then:
        1 * xmlMerger.getXmlTransformer()

        when: "configure classpath XML"
        model.classpath.file.withXml(xmlAction)

        then:
        1 * xmlTransformer.addAction(xmlAction)
    }

    def "can configure jdt with Actions"() {
        given:
        def propertiesTransformer = Mock(PropertiesTransformer)
        def propertiesMerger = Spy(PropertiesFileContentMerger, constructorArgs: [propertiesTransformer])
        def propertiesAction = {} as Action<Properties>
        model.jdt = TestUtil.newInstance(EclipseJdt, propertiesMerger)

        when: "configure jdt"
        model.jdt({ jdt -> jdt.sourceCompatibility = JavaVersion.VERSION_1_9 } as Action<EclipseJdt>)

        then:
        model.jdt.sourceCompatibility == JavaVersion.VERSION_1_9

        when: "configure jdt file"
        model.jdt.file({ fcm -> fcm.transformer } as Action<PropertiesFileContentMerger>)

        then:
        1 * propertiesMerger.getTransformer()

        when: "configure jdt properties"
        model.jdt.file.withProperties(propertiesAction)

        then:
        1 * propertiesTransformer.addAction(propertiesAction)
    }

    def "can configure wtp with Actions"() {
        given:
        def xmlTransformer = Mock(XmlTransformer)
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [xmlTransformer])
        def xmlAction = {} as Action<XmlProvider>
        def facet = TestUtil.newInstance(EclipseWtpFacet, xmlMerger)
        def component = TestUtil.newInstance(EclipseWtpComponent, project, xmlMerger)
        model.wtp = TestUtil.newInstance(EclipseWtp)

        when: "configure wtp"
        model.wtp({ wtp ->
            wtp.component = component
            wtp.facet = facet
        } as Action<EclipseWtp>)

        then:
        model.wtp.component == component
        model.wtp.facet == facet
        model.wtp.facet.facets.empty

        when: "configure wtp component and facet"
        model.wtp.component({ comp -> comp.deployName = 'name' } as Action<EclipseWtpComponent>)
        model.wtp.facet({ fac -> fac.facets.add(new Facet()) } as Action<EclipseWtpFacet>)

        then:
        model.wtp.component.deployName == 'name'
        model.wtp.facet.facets.size() == 1

        when: "configure wtp component and facet file"
        model.wtp.component.file({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)
        model.wtp.facet.file({ fcm -> fcm.xmlTransformer } as Action<XmlFileContentMerger>)

        then:
        2 * xmlMerger.getXmlTransformer()

        when: "configure wtp component and facet xml"
        model.wtp.component.file.withXml(xmlAction)
        model.wtp.facet.file.withXml(xmlAction)

        then:
        2 * xmlTransformer.addAction(xmlAction)
    }
}
