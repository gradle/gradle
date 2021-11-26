/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.plugins.ide.eclipse

import groovy.xml.XmlParser
import org.gradle.test.fixtures.file.TestFile

class EclipseWtpComponentFixture {
    private final Node component

    private EclipseWtpComponentFixture(Node component) {
        this.component = component;
    }

    static EclipseWtpComponentFixture create(TestFile projectDir) {
        TestFile file = projectDir.file(".settings/org.eclipse.wst.common.component")
        file.assertIsFile()
        new EclipseWtpComponentFixture(new XmlParser().parse(file))
    }

    String getDeployName() {
        return component."wb-module"."@deploy-name".text()
    }

    List<WbResource> getResources() {
        return component."wb-module"."wb-resource".collect { new WbResource(it) }
    }

    List<WbModule> getModules() {
        return component."wb-module"."dependent-module".collect { new WbModule(it) }
    }

    Map<String, String> getModuleProperties() {
        return component."wb-module".property.collectEntries { [(it.@name) : it.@value] }
    }

    WbModule lib(String jarName) {
        def module = modules.find {
            def handle = it.node.@handle
            return handle.startsWith('module:/classpath/') && handle.endsWith(jarName)
        }
        assert module != null
        assert module.node."dependency-type"*.text() == ['uses']
        return module
    }

    WbModule project(String projectName) {
        def module = modules.find {
            def handle = it.node.@handle
            return handle == "module:/resource/$projectName/$projectName"
        }
        assert module != null
        assert module.node."dependency-type"*.text() == ['uses']
        return module
    }

    WbResource sourceDirectory(String path) {
        def resource = resources.find { it.node."@source-path" == path }
        assert resource != null
        return resource
    }

    class WbModule {
        private final Node node

        WbModule(Node node) {
            this.node = node
        }

        void assertDeployedAt(String path) {
            assert node."@deploy-path" == path
        }
    }

    class WbResource {
        private final Node node

        WbResource(Node node) {
            this.node = node
        }

        void assertDeployedAt(String path) {
            assert node."@deploy-path" == path
        }

        void assertAttributes(Map attributes) {
            attributes.each { key, value ->  assert node."@$key" == value }
        }

    }
}
