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
package org.gradle.plugins.ide.idea.model

import org.gradle.api.internal.XmlTransformer
import spock.lang.Specification

/**
 * @author Hans Dockter
 */
class ProjectTest extends Specification {
    final PathFactory pathFactory = new PathFactory()
    final customModules = [new ModulePath(path('file://$PROJECT_DIR$/gradle-idea-plugin.iml'), '$PROJECT_DIR$/gradle-idea-plugin.iml')] as Set
    final customWildcards = ["?*.gradle", "?*.grails"] as Set
    Project project = new Project(new XmlTransformer())

    def loadFromReader() {
        when:
        project.load(customProjectReader)

        then:
        project.modulePaths == customModules
        project.wildcards == customWildcards
        project.jdk == new Jdk(true, false, null, "1.4")
    }

    def customJdkAndWildcards_shouldBeMerged() {
        def modules = [new ModulePath(path('file://$PROJECT_DIR$/other.iml'), '$PROJECT_DIR$/other.iml')] as Set

        when:
        project.load(customProjectReader)
        project.configure(modules, '1.6', ['?*.groovy'] as Set)

        then:
        project.modulePaths == customModules + modules
        project.wildcards == customWildcards + ['?*.groovy'] as Set
        project.jdk == new Jdk("1.6")
    }

    def loadDefaults() {
        when:
        project.loadDefaults()

        then:
        project.modulePaths.size() == 0
        project.wildcards == [] as Set
        project.jdk == new Jdk(true, true, "JDK_1_5", null)
    }

    def toXml_shouldContainCustomValues() {
        when:
        project.loadDefaults()
        project.configure([] as Set, '1.5', ['?*.groovy'] as Set)
        def xml = toXmlReader
        def other = new Project(new XmlTransformer())
        other.load(xml)

        then:
        project.wildcards == other.wildcards
        project.modulePaths == other.modulePaths
        project.jdk == other.jdk
    }

    private InputStream getToXmlReader() {
        ByteArrayOutputStream toXmlText = new ByteArrayOutputStream()
        project.store(toXmlText)
        return new ByteArrayInputStream(toXmlText.toByteArray())
    }

    private InputStream getCustomProjectReader() {
        return getClass().getResourceAsStream('customProject.xml')
    }

    private Path path(String url) {
        pathFactory.path(url)
    }
}
