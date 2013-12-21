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

import org.gradle.api.internal.file.FileResolver
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.junit.Rule
import spock.lang.Specification

import javax.xml.parsers.DocumentBuilderFactory

import static org.gradle.util.TextUtil.toPlatformLineSeparators

class DefaultDeploymentDescriptorTest extends Specification {
    def descriptor = new DefaultDeploymentDescriptor({ it } as FileResolver)
    @Rule TestNameTestDirectoryProvider tmpDir

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

    def "writes version 1.3 default descriptor"() {
        def out = new StringWriter()
        descriptor.version = '1.3'

        when:
        descriptor.writeTo(out)

        then:
        out.toString() == toPlatformLineSeparators("""<?xml version="1.0"?>
<!DOCTYPE application PUBLIC "-//Sun Microsystems, Inc.//DTD J2EE Application 1.3//EN" "http://java.sun.com/dtd/application_1_3.dtd">
<application version="1.3"/>
""")
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
        descriptor.securityRole "superadmin"
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
    <role-name>superadmin</role-name>
  </security-role>
  <library-directory>APP-INF/lib</library-directory>
  <data-source>my/data/source</data-source>
</application>
""")
    }
}
