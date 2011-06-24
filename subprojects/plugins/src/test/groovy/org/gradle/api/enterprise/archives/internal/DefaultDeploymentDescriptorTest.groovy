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

package org.gradle.api.enterprise.archives.internal

import spock.lang.Specification
import static org.gradle.util.TextUtil.toPlatformLineSeparators

/**
 * @author: Szczepan Faber, created at: 6/3/11
 */
class DefaultDeploymentDescriptorTest extends Specification {

    def out = new StringWriter()
    def descriptor = new DefaultDeploymentDescriptor(null)

    def "writes default descriptor"() {
        when:
        descriptor.writeTo(out)

        then:
        out.toString() == toPlatformLineSeparators("""<?xml version="1.0"?>
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_6.xsd" version="6"/>
""")
    }

    def "writes values"() {
        descriptor.fileName = "myApp.xml"
        descriptor.version = "5"
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
<application xmlns="http://java.sun.com/xml/ns/javaee" xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance" xsi:schemaLocation="http://java.sun.com/xml/ns/javaee http://java.sun.com/xml/ns/javaee/application_5.xsd" version="5">
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

        //TODO SF make sure it is also able to parse such input (plus custom xml elements)
    }
}
