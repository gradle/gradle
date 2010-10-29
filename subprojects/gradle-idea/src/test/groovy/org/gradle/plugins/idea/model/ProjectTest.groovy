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

import org.gradle.api.Action
import org.gradle.api.artifacts.maven.XmlProvider
import org.gradle.api.internal.XmlTransformer
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class ProjectTest extends Specification {
    Project project
    final PathFactory pathFactory = new PathFactory()

    def initWithReaderAndNoJdkAndNoWildcards() {
        project = createProject(javaVersion: "1.4", reader: customProjectReader)

        expect:
        project.modulePaths == [new ModulePath(path('file://$PROJECT_DIR$/gradle-idea-plugin.iml'), '$PROJECT_DIR$/gradle-idea-plugin.iml')] as Set
        project.wildcards == ["?*.gradle", "?*.grails"] as Set
        project.jdk == new Jdk(true, false, "JDK_1_4", "1.4")
    }

    def initWithReaderAndJdkAndWildcards_shouldBeMerged() {
        project = createProject(wildcards: ['?*.groovy'] as Set, reader: customProjectReader)

        expect:
        project.modulePaths == [new ModulePath(path('file://$PROJECT_DIR$/gradle-idea-plugin.iml'), '$PROJECT_DIR$/gradle-idea-plugin.iml')] as Set
        project.wildcards == ["?*.gradle", "?*.grails", "?*.groovy"] as Set
        project.jdk == new Jdk("1.6")
    }

    def initWithNullReader_shouldUseDefaults() {
        project = createProject(wildcards: ['!?*.groovy'] as Set)

        expect:
        project.modulePaths.size() == 0
        project.wildcards == ["!?*.groovy"] as Set
        project.jdk == new Jdk("1.6")
    }

    def toXml_shouldContainCustomValues() {
        when:
        project = createProject(wildcards: ['?*.groovy'] as Set, reader: customProjectReader)

        then:
        project == createProject(wildcards: ['?*.groovy'] as Set, reader: toXmlReader)
    }

    def toXml_shouldContainSkeleton() {
        when:
        project = createProject(reader: customProjectReader)

        then:
        new XmlParser().parse(toXmlReader).toString() == project.xml.toString()
    }

    def beforeConfigured() {
        Action beforeConfiguredActions = { Project ideaProject ->
            ideaProject.modulePaths.clear()
        } as Action
        def modulePaths = [new ModulePath(path("a"), "b")] as Set

        when:
        project = createProject(modulePaths: modulePaths, reader: customProjectReader, beforeConfiguredActions: beforeConfiguredActions)

        then:
        createProject(reader: toXmlReader).modulePaths == modulePaths
    }

    def whenConfigured() {
        def moduleFromInitialXml = null
        def moduleFromProjectConstructor = new ModulePath(path("a"), "b")
        def moduleAddedInWhenConfiguredAction = new ModulePath(path("c"), "d")
        Action beforeConfiguredActions = { Project ideaProject ->
            moduleFromInitialXml = (ideaProject.modulePaths as List)[0]
        } as Action
        Action whenConfiguredActions = { Project ideaProject ->
            assert ideaProject.modulePaths.contains(moduleFromInitialXml)
            assert ideaProject.modulePaths.contains(moduleFromProjectConstructor)
            ideaProject.modulePaths.add(moduleAddedInWhenConfiguredAction)
        } as Action

        when:
        project = createProject(modulePaths: [moduleFromProjectConstructor] as Set, reader: customProjectReader,
                beforeConfiguredActions: beforeConfiguredActions,
                whenConfiguredActions: whenConfiguredActions)

        then:
        createProject(reader: toXmlReader).modulePaths == [moduleFromInitialXml, moduleFromProjectConstructor, moduleAddedInWhenConfiguredAction] as Set
    }

    private StringReader getToXmlReader() {
        StringWriter toXmlText = new StringWriter()
        project.toXml(toXmlText)
        return new StringReader(toXmlText.toString())
    }

    def withXml() {
        XmlTransformer withXmlActions = new XmlTransformer()
        project = createProject(reader: customProjectReader, withXmlActions: withXmlActions)

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

    private InputStreamReader getCustomProjectReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customProject.xml'))
    }

    private Path path(String url) {
        pathFactory.path(url)
    }

    private Project createProject(Map customArgs) {
        Action dummyBroadcast = Mock()
        XmlTransformer xmlTransformer = new XmlTransformer()
        Map args = [modulePaths: [] as Set, javaVersion: "1.6", wildcards: [] as Set, reader: null,
                beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: xmlTransformer] + customArgs
        return new Project(args.modulePaths, args.javaVersion, args.wildcards, args.reader,
                args.beforeConfiguredActions, args.whenConfiguredActions, args.withXmlActions, pathFactory)
    }
}
