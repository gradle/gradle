/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.plugins.ide.fixtures

import groovy.transform.ToString
import groovy.xml.slurpersupport.GPathResult
import org.gradle.internal.InternalTransformers
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.internal.CollectionUtils

class IdeaModuleFixture extends IdeProjectFixture {
    private final GPathResult iml
    private final TestFile file

    IdeaModuleFixture(TestFile file, GPathResult iml) {
        this.iml = iml
        this.file = file
    }

    String getLanguageLevel() {
        if (iml.component.@LANGUAGE_LEVEL.size() != 0) {
            return iml.component.@LANGUAGE_LEVEL
        }
        return null
    }

    IdeaContentRoot getContent() {
        def contentRoot = iml.component.content
        def sourceFolders = contentRoot.sourceFolder.collect {
            new SourceFolder(url: it.@url, isTestSource: it.@isTestSource)
        }
        def excludeFolders = contentRoot.excludeFolder.collect {
            new ExcludeFolder(url: it.@url)
        }
        return new IdeaContentRoot(rootUrl: contentRoot.@url, sources: sourceFolders, excludes: excludeFolders)
    }

    @ToString
    class SourceFolder {
        String url
        boolean isTestSource = false
    }

    @ToString
    class ExcludeFolder {
        String url
    }

    @ToString
    class IdeaContentRoot {
        String rootUrl
        List<SourceFolder> sources
        List<ExcludeFolder> excludes

        void assertContainsSourcePaths(String... paths) {
            def sourceRoots = sources.collect {
                it.url - 'file://$MODULE_DIR$/'
            } as Set

            def setDiff = CollectionUtils.diffSetsBy(sourceRoots, CollectionUtils.toSet(paths as List), InternalTransformers.noOpTransformer())
            assert setDiff.leftOnly.empty
            assert setDiff.rightOnly.empty
        }

        void assertContainsExcludes(String... paths) {
            def excludeRoots = excludes.collect {
                it.url - 'file://$MODULE_DIR$/'
            } as Set

            def setDiff = CollectionUtils.diffSetsBy(excludeRoots, CollectionUtils.toSet(paths as List), InternalTransformers.noOpTransformer())
            assert setDiff.leftOnly.empty
            assert setDiff.rightOnly.empty
        }
    }

    ImlDependencies getDependencies() {
        new ImlDependencies(iml.component.orderEntry.collect { it ->
            if (it.@type == 'module-library') {
                def lib = new ImlModuleLibrary()
                lib.type = it.@type
                lib.url = it.library.CLASSES.root.@url.text()
                lib.scope = it.@scope != '' ? it.@scope : 'COMPILE'
                lib.source = it.library.SOURCES.root.@url*.text()
                lib.javadoc = it.library.JAVADOC.root.@url*.text()
                return lib
            } else if (it.@type == 'module') {
                def module = new ImlModule()
                module.type = it.@type
                module.scope = it.@scope != '' ? it.@scope : 'COMPILE'
                module.moduleName = it.'@module-name'
                return module
            } else if (it.@type == 'sourceFolder') {
                def source = new ImlSource()
                source.type = it.@type
                source.forTests = it.@forTests
                return source
            } else if (it.@type == 'library') {
                def library = new ImlLibrary()
                library.type = it.@type
                library.name = it.@name
                library.level = it.@level
                return library
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

        List<ImlModuleLibrary> getLibraries() {
            dependencies.findAll { it instanceof ImlModuleLibrary }
        }

        List<ImlModule> getModules() {
            dependencies.findAll { it instanceof ImlModule }
        }

        void assertHasInheritedJdk() {
            assert dependencies.any { ImlDependency it ->
                it.type == 'inheritedJdk'
            }
        }

        void assertHasSource(String forTests) {
            assert dependencies.findAll { it.type == 'sourceFolder' }.any { ImlSource it -> it.forTests == forTests }
        }

        void assertHasModule(List<String> scopes, String name) {
            scopes.each {assertHasModule(it, name)}
        }

        void assertHasLibrary(List<String> scopes, String name) {
            scopes.each {assertHasLibrary(it, name)}
        }

        void assertHasModule(String scope = 'COMPILE', String name) {
            assert modules.any { ImlModule it ->
                it.scope == scope && it.moduleName == name
            }  : "No module '$name' with scope $scope found in ${modules*.moduleName}"
        }

        void assertHasLibrary(String scope = 'COMPILE', String name) {
            assert libraries.any { ImlModuleLibrary it ->
                it.scope == scope && it.url.contains(name)
            }  : "No library '$name' with scope $scope found in ${libraries*.url}"
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

        @Override
        String toString() {
            return "ImlModule {" +
                "name='" + moduleName + '\'' +
                ", scope='" + scope + '\'' +
                '}';
        }
    }

    class ImlLibrary extends ImlDependency {
        String name
        String level
    }

    class ImlModuleLibrary extends ImlDependency {
        String scope
        String url
        List<String> javadoc
        List<String> source

        @Override
        public String toString() {
            return "ImlModuleLibrary{" +
                "type='" + type + '\'' +
                ", scope='" + scope + '\'' +
                ", url='" + url + '\'' +
                '}';
        }

        String getJarName() {
            return url.replaceFirst('!/$', '').split('/').last()
        }

        void assertHasSource(List<String> filenames) {
            assert source.collect { it.replaceFirst('!/$', '').split('/').last() } == filenames
        }

        void assertHasJavadoc(List<String> filenames) {
            assert javadoc.collect { it.replaceFirst('!/$', '').split('/').last() } == filenames
        }

        void assertHasNoJavadoc() {
            assert javadoc.empty
        }

        void assertHasNoSource() {
            assert source.empty
        }
    }
}
