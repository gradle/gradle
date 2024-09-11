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

package org.gradle.plugins.ear.descriptor.internal

import org.gradle.api.Action
import org.gradle.api.internal.file.FileResolver
import org.gradle.api.model.ObjectFactory
import org.gradle.plugins.ear.descriptor.EarSecurityRole
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.TestUtil
import org.junit.Rule
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory

import static org.gradle.util.internal.TextUtil.toPlatformLineSeparators

class DefaultDeploymentDescriptorTest extends Specification {
    private ObjectFactory objectFactory = TestUtil.objectFactory()

    def descriptor = objectFactory.newInstance(DefaultDeploymentDescriptor, ({ it } as FileResolver), objectFactory)
    @Rule TestNameTestDirectoryProvider tmpDir = new TestNameTestDirectoryProvider(getClass())

    def "writes default descriptor"() {
        def file = tmpDir.file("out.xml")

        when:
        descriptor.writeTo(file)

        then:
        def root = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(file.bytes)).documentElement
        root.nodeName == 'application'
        root.getAttribute("xmlns") == "http://java.sun.com/xml/ns/javaee"
        root.getAttribute("xmlns:xsi") == "http://www.w3.org/2001/XMLSchema-instance"
        root.getAttribute("xsi:schemaLocation") == "http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd"
        root.getAttribute("version") == "6"
        root.childNodes.length == 0
    }

    def "writes version #version default descriptor"() {
        def out = new StringWriter()
        descriptor.version = version

        when:
        descriptor.writeTo(out)

        then:
        def stringOutput = out.toString()
        acceptableDescriptors.contains(stringOutput)

        where:
        version | _
        '1.3'   | _
        '1.4'   | _
        '5'     | _
        '6'     | _
        '7'     | _
        '8'     | _
        '9'     | _
        '10'    | _
        acceptableDescriptors = defaultDescriptorForVersion(version)
    }

    private List<String>defaultDescriptorForVersion(version) {
        // Groovy XML Node put attributes in a HashMap so does not guarantee rendering order for attributes
        // This method generates all permutations so we can assert we have at least one
        def attributesPermutations = { String template, List<String> attributes ->
            def permutations = []
            attributes.eachPermutation { attrs ->
                permutations << template.replace('##ATTRIBUTES##', attrs.join(' '))
            }
            permutations
        }
        switch (version) {
            case '1.3':
                return [toPlatformLineSeparators('''<?xml version="1.0"?>
<!DOCTYPE application PUBLIC "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN" "http://java.sun.com/dtd/application_1_3.dtd">
<application version="1.3"/>
''')]
            case '1.4':
                def attributes = [
                    'xmlns="http://java.sun.com/xml/ns/j2ee"',
                    'xsi:schemaLocation="http://java.sun.com/xml/ns/j2ee http://java.sun.com/xml/ns/j2ee/application_1_4.xsd"',
                    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
                    'version="1.4"'
                ]
                return attributesPermutations('<?xml version="1.0"?>\n<application ##ATTRIBUTES##/>\n', attributes).collect { String descriptor ->
                    toPlatformLineSeparators(descriptor)
                }
            case '5':
            case '6':
                def attributes = [
                    'xmlns="http://java.sun.com/xml/ns/javaee"',
                    "xsi:schemaLocation=\"http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_${version}.xsd\"",
                    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
                    "version=\"${version}\""
                ]
                return attributesPermutations('<?xml version="1.0"?>\n<application ##ATTRIBUTES##/>\n', attributes).collect { String descriptor ->
                    toPlatformLineSeparators(descriptor)
                }
            case '7':
            case '8':
                def attributes = [
                    'xmlns="http://xmlns.jcp.org/xml/ns/javaee"',
                    "xsi:schemaLocation=\"http://xmlns.jcp.org/xml/ns/javaee http://xmlns.jcp.org/xml/ns/javaee/application_${version}.xsd\"",
                    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
                    "version=\"${version}\""
                ]
                return attributesPermutations('<?xml version="1.0"?>\n<application ##ATTRIBUTES##/>\n', attributes).collect { String descriptor ->
                    toPlatformLineSeparators(descriptor)
                }
            default:
                def attributes = [
                    'xmlns="https://jakarta.ee/xml/ns/jakartaee"',
                    "xsi:schemaLocation=\"https://jakarta.ee/xml/ns/jakartaee https://jakarta.ee/xml/ns/jakartaee/application_${version}.xsd\"",
                    'xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"',
                    "version=\"${version}\""
                ]
                return attributesPermutations('<?xml version="1.0"?>\n<application ##ATTRIBUTES##/>\n', attributes).collect { String descriptor ->
                    toPlatformLineSeparators(descriptor)
                }
        }
    }

    def "writes customized descriptor"() {
        def out = new StringWriter()
        descriptor.fileName = "myApp.xml"
        descriptor.version = "1.3"
        descriptor.applicationName = "myapp"
        descriptor.initializeInOrder = true
        descriptor.displayName = "My App"
        descriptor.description = "My Application"
        descriptor.libraryDirectory = "APP-INF/lib"
        descriptor.module("my.jar", "java")
        descriptor.webModule("my.war", "/")
        descriptor.securityRole "admin"
        descriptor.securityRole({ role ->
            role.roleName = "superadmin"
            role.description = "Role of super admin"
        } as Action<EarSecurityRole>)
        descriptor.withXml { it.asNode().appendNode("data-source", "my/data/source") }

        when:
        descriptor.writeTo(out)

        then:
        out.toString() == toPlatformLineSeparators("""<?xml version="1.0"?>
<!DOCTYPE application PUBLIC "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN" "http://java.sun.com/dtd/application_1_3.dtd">
<application version="1.3">
  <application-name>myapp</application-name>
  <description>My Application</description>
  <display-name>My App</display-name>
  <initialize-in-order>true</initialize-in-order>
  <module>
    <java>my.jar</java>
  </module>
  <module>
    <web>
      <web-uri>my.war</web-uri>
      <context-root>/</context-root>
    </web>
  </module>
  <security-role>
    <role-name>admin</role-name>
  </security-role>
  <security-role>
    <description>Role of super admin</description>
    <role-name>superadmin</role-name>
  </security-role>
  <library-directory>APP-INF/lib</library-directory>
  <data-source>my/data/source</data-source>
</application>
""")
    }

    def "writes version #version descriptor withXml #withXmlDescription"() {
        given:
        def out = new StringWriter()
        descriptor.version = version
        descriptor.withXml(withXmlClosure)

        when:
        descriptor.writeTo(out)

        then:
        def stringOutput = out.toString()
        acceptableDescriptors.contains(stringOutput)

        where:
        version | withXmlDescription | withXmlClosure
        '1.3'   | 'asNode'           | { it.asNode() }
        '1.4'   | 'asNode'           | { it.asNode() }
        '1.4'   | 'asElement'        | { it.asElement() }
        '5'     | 'asNode'           | { it.asNode() }
        '5'     | 'asElement'        | { it.asElement() }
        '6'     | 'asNode'           | { it.asNode() }
        '6'     | 'asElement'        | { it.asElement() }
        '7'     | 'asNode'           | { it.asNode() }
        '7'     | 'asElement'        | { it.asElement() }
        '8'     | 'asNode'           | { it.asNode() }
        '8'     | 'asElement'        | { it.asElement() }
        '9'     | 'asNode'           | { it.asNode() }
        '9'     | 'asElement'        | { it.asElement() }
        '10'    | 'asNode'           | { it.asNode() }
        '10'    | 'asElement'        | { it.asElement() }
        acceptableDescriptors = defaultDescriptorForVersion(version)
    }

    def "fails to write version 1.3 descriptor withXml asElement"() {
        given:
        def out = new StringWriter()
        descriptor.version = '1.3'
        descriptor.withXml { it.asElement() }

        when:
        descriptor.writeTo(out)

        then:
        def e = thrown(Exception)
        e.message.contains('External DTD: Failed to read external DTD')
    }
}
