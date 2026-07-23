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
import org.gradle.api.internal.file.TestFiles
import org.gradle.api.internal.project.ProjectInternal
import org.gradle.internal.xml.XmlTransformer
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
        model.wtp.component = TestUtil.newInstance(EclipseWtpComponent, project)

        when:
        model.pathVariables(one: new File('.'))

        then:
        model.classpath.pathVariables == [one: new File('.')]
        model.wtp.component.pathVariables == [one: new File('.')]
    }

    def "can configure project with Actions"() {
        given:
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [new XmlTransformer()])
        def mergeAction = {} as Action<Project>
        model.project = TestUtil.newInstance(EclipseProject, xmlMerger)

        when: "configure project"
        model.project({ p -> p.comment = 'something' } as Action<EclipseProject>)

        then:
        model.project.comment == 'something'

        when: "configure project file"
        model.project.file({ fcm -> fcm.whenMerged } as Action<XmlFileContentMerger>)

        then:
        1 * xmlMerger.getWhenMerged()

        when: "register a merge hook"
        model.project.file.whenMerged(mergeAction)

        then:
        !model.project.file.whenMerged.empty
    }

    def "can configure classpath with Actions"() {
        given:
        def xmlMerger = Spy(XmlFileContentMerger, constructorArgs: [new XmlTransformer()])
        def mergeAction = {} as Action<Classpath>
        model.classpath.file = xmlMerger

        when: "configure classpath"
        model.classpath({ cp -> cp.downloadJavadoc = true } as Action<EclipseClasspath>)

        then:
        model.classpath.downloadJavadoc

        when: "configure classpath file"
        model.classpath.file({ fcm -> fcm.whenMerged } as Action<XmlFileContentMerger>)

        then:
        1 * xmlMerger.getWhenMerged()

        when: "register a merge hook"
        model.classpath.file.whenMerged(mergeAction)

        then:
        !model.classpath.file.whenMerged.empty
    }

    def "can configure jdt with Actions"() {
        given:
        model.jdt = TestUtil.newInstance(EclipseJdt)

        when: "configure jdt"
        model.jdt({ jdt -> jdt.sourceCompatibility = JavaVersion.VERSION_1_9 } as Action<EclipseJdt>)

        then:
        model.jdt.sourceCompatibility == JavaVersion.VERSION_1_9
    }

    def "can configure wtp with Actions"() {
        given:
        def component = TestUtil.newInstance(EclipseWtpComponent, project)
        model.wtp = TestUtil.newInstance(EclipseWtp)

        when: "configure wtp"
        model.wtp({ wtp ->
            wtp.component = component
        } as Action<EclipseWtp>)

        then:
        model.wtp.component == component

        when: "configure wtp component"
        model.wtp.component({ comp -> comp.deployName = 'name' } as Action<EclipseWtpComponent>)

        then:
        model.wtp.component.deployName == 'name'
    }
}
