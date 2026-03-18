/*
 * Copyright 2025 the original author or authors.
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
package gradlebuild.docs.dsl.docbook

import gradlebuild.docs.XmlSpecification
import gradlebuild.docs.dsl.source.model.ClassMetaData
import gradlebuild.docs.dsl.source.model.MethodMetaData
import gradlebuild.docs.dsl.source.model.PropertyMetaData
import gradlebuild.docs.dsl.source.model.TypeMetaData

class ClassDocXmlGeneratorTest extends XmlSpecification {

    def "generates properties section from metadata"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addProperty(classMetaData, "archiveName", "The archive name")
        addProperty(classMetaData, "baseName", "The base name")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def propertiesSection = result.getElementsByTagName("section").item(0)
        propertiesSection.getElementsByTagName("title").item(0).textContent == "Properties"
        def rows = getDataRows(propertiesSection)
        rows.size() == 2
        rows[0] == "archiveName"
        rows[1] == "baseName"
    }

    def "generates methods section from metadata"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addMethod(classMetaData, "copy", "Copies files")
        addMethod(classMetaData, "delete", "Deletes files")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def sections = result.getElementsByTagName("section")
        def methodsSection = sections.item(1)
        methodsSection.getElementsByTagName("title").item(0).textContent == "Methods"
        def rows = getDataRows(methodsSection)
        rows.size() == 2
        rows[0] == "copy"
        rows[1] == "delete"
    }

    def "excludes dslHidden properties"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addProperty(classMetaData, "visible", "A visible property")
        def hidden = addProperty(classMetaData, "hidden", "A hidden property")
        hidden.setDslHidden(true)

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def propertiesSection = result.getElementsByTagName("section").item(0)
        def rows = getDataRows(propertiesSection)
        rows.size() == 1
        rows[0] == "visible"
    }

    def "excludes dslHidden methods"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addMethod(classMetaData, "visible", "A visible method")
        def hidden = addMethod(classMetaData, "hidden", "A hidden method")
        hidden.setDslHidden(true)

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def methodsSection = result.getElementsByTagName("section").item(1)
        def rows = getDataRows(methodsSection)
        rows.size() == 1
        rows[0] == "visible"
    }

    def "excludes properties without javadoc"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addProperty(classMetaData, "documented", "Has javadoc")
        addProperty(classMetaData, "undocumented", "")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def propertiesSection = result.getElementsByTagName("section").item(0)
        def rows = getDataRows(propertiesSection)
        rows.size() == 1
        rows[0] == "documented"
    }

    def "excludes methods without javadoc"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addMethod(classMetaData, "documented", "Has javadoc")
        addMethod(classMetaData, "undocumented", "")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def methodsSection = result.getElementsByTagName("section").item(1)
        def rows = getDataRows(methodsSection)
        rows.size() == 1
        rows[0] == "documented"
    }

    def "excludes getter and setter methods that are represented as properties"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        // This simulates what SourceMetaDataVisitor does: creates a property from a getter
        def type = new TypeMetaData("java.lang.String")
        def getter = classMetaData.addMethod("getName", type, "The name")
        classMetaData.addReadableProperty("name", type, "The name", getter)
        // A non-property method
        addMethod(classMetaData, "execute", "Executes the task")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def propertiesSection = result.getElementsByTagName("section").item(0)
        def propRows = getDataRows(propertiesSection)
        propRows.size() == 1
        propRows[0] == "name"

        def methodsSection = result.getElementsByTagName("section").item(1)
        def methodRows = getDataRows(methodsSection)
        methodRows.size() == 1
        methodRows[0] == "execute"
    }

    def "sorts properties and methods alphabetically"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addProperty(classMetaData, "zebra", "Z property")
        addProperty(classMetaData, "alpha", "A property")
        addProperty(classMetaData, "middle", "M property")
        addMethod(classMetaData, "zoo", "Z method")
        addMethod(classMetaData, "apple", "A method")

        when:
        def result = ClassDocXmlGenerator.generate(classMetaData, document)

        then:
        def propertiesSection = result.getElementsByTagName("section").item(0)
        def propRows = getDataRows(propertiesSection)
        propRows == ["alpha", "middle", "zebra"]

        def methodsSection = result.getElementsByTagName("section").item(1)
        def methodRows = getDataRows(methodsSection)
        methodRows == ["apple", "zoo"]
    }

    def "generates valid structure consumable by ClassDoc"() {
        given:
        def classMetaData = new ClassMetaData("org.gradle.TestClass")
        addProperty(classMetaData, "name", "The name")
        addMethod(classMetaData, "execute", "Executes")

        when:
        def content = ClassDocXmlGenerator.generate(classMetaData, document)
        // Verify the structure is what ClassDoc expects
        def classDoc = withCategories {
            new gradlebuild.docs.dsl.docbook.model.ClassDoc("org.gradle.TestClass", content, document, classMetaData, null)
        }

        then:
        classDoc.propertiesTable != null
        classDoc.methodsTable != null
    }

    private PropertyMetaData addProperty(ClassMetaData classMetaData, String name, String comment) {
        def type = new TypeMetaData("java.lang.String")
        def getter = classMetaData.addMethod("get" + name.capitalize(), type, comment)
        return classMetaData.addReadableProperty(name, type, comment, getter)
    }

    private MethodMetaData addMethod(ClassMetaData classMetaData, String name, String comment) {
        def type = new TypeMetaData("void")
        return classMetaData.addMethod(name, type, comment)
    }

    private List<String> getDataRows(def section) {
        def table = section.getElementsByTagName("table").item(0)
        def rows = []
        def trs = table.getElementsByTagName("tr")
        for (int i = 0; i < trs.length; i++) {
            def tr = trs.item(i)
            // Skip the header row (inside thead)
            if (tr.parentNode.nodeName == "thead") continue
            def tds = tr.getElementsByTagName("td")
            if (tds.length > 0) {
                rows << tds.item(0).textContent.trim()
            }
        }
        return rows
    }
}
