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

package org.gradle.plugins.ide.idea

import groovy.util.slurpersupport.GPathResult

class IdeaModuleFixture {
    private GPathResult iml

    IdeaModuleFixture(GPathResult iml) {
        this.iml = iml
    }

    ImlDependencies getDependencies() {
        new ImlDependencies(iml.component.orderEntry.collect { it ->
            if (it.@type == 'module-library') {
                def lib = new ImlLibrary()
                lib.type = 'module-library'
                lib.url = it.library.CLASSES.root.@url.text()
                lib.scope = it.@scope != '' ? it.@scope : 'COMPILE'
                return lib
            } else if (it.@type == 'module') {
                def module = new ImlModule()
                module.type = 'module'
                module.scope = it.@scope != '' ? it.@scope : 'COMPILE'
                module.moduleName = it.'@module-name'
                return module
            } else if (it.@type == 'sourceFolder') {
                def source = new ImlSource()
                source.type = 'sourceFolder'
                source.forTests = it.@forTests
                return source
            } else {
                def dep = new ImlDependency()
                dep.type = it.@type
                return dep
            }
        })

    }

    class ImlDependencies {
        List<ImlDependency> dependencies

        private ImlDependencies(List<ImlDependency> dependencies) {
            this.dependencies = dependencies
        }

        List<ImlLibrary> getLibraries() {
            dependencies.findAll { it.type == 'module-library' }
        }

        List<ImlModule> getModules() {
            dependencies.findAll { it.type == 'module' }
        }

        void assertHasInheritedJdk() {
            assert dependencies.any { ImlDependency it ->
                it.type == 'inheritedJdk'
            }
        }

        void assertHasSource(String forTests) {
            assert dependencies.findAll { it.type == 'sourceFolder' }.any { ImlSource it -> it.forTests = forTests }
        }

        void assertHasModule(String scope, String name) {
            assert modules.any { ImlModule it ->
                it.scope == scope && it.moduleName == name
            }
        }

        void assertHasLibrary(String scope, String name) {
            assert libraries.any { ImlLibrary it ->
                it.scope == scope && it.url.contains(name)
            }
        }
    }
    class ImlDependency {
        String type
    }
    class ImlSource extends ImlDependency {
        String forTests
    }
    class ImlModule extends ImlDependency {
        String scope
        String moduleName
    }
    class ImlLibrary extends ImlDependency {
        String scope
        String url

        @Override
        public String toString() {
            return "ImlLibrary{" +
                    "type='" + type + '\'' +
                    ", scope='" + scope + '\'' +
                    ", url='" + url + '\'' +
                    '}';
        }
    }
}
