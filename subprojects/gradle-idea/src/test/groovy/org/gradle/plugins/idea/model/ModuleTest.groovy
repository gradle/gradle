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
class ModuleTest extends Specification {
    def static final CUSTOM_SOURCE_FOLDERS = [new Path('file://$MODULE_DIR$/src')] as LinkedHashSet
    def static final CUSTOM_TEST_SOURCE_FOLDERS = [new Path('file://$MODULE_DIR$/srcTest')] as LinkedHashSet
    def static final CUSTOM_EXCLUDE_FOLDERS = [new Path('file://$MODULE_DIR$/target')] as LinkedHashSet
    def customDependencies = [
            new ModuleLibrary([new Path('file://$MODULE_DIR$/gradle/lib')] as Set,
                    [new Path('file://$MODULE_DIR$/gradle/javadoc')] as Set, [new Path('file://$MODULE_DIR$/gradle/src')] as Set,
                    [] as Set, null),
            new ModuleLibrary([new Path('file://$MODULE_DIR$/ant/lib'), new Path('jar://$GRADLE_CACHE$/gradle.jar!/')] as Set, [] as Set, [] as Set,
                    [new JarDirectory(new Path('file://$MODULE_DIR$/ant/lib'), false)] as Set, "RUNTIME"),
            new ModuleDependency('someModule', null)]

    Module module

//    def setup() {
//        customDependencies.each { println it }
//    }

    def initWithReader() {
        module = createModule(reader: customModuleReader)

        expect:
        module.javaVersion == "1.6"
        module.sourceFolders == CUSTOM_SOURCE_FOLDERS
        module.testSourceFolders == CUSTOM_TEST_SOURCE_FOLDERS
        module.excludeFolders == CUSTOM_EXCLUDE_FOLDERS
        module.outputDir == new Path('file://$MODULE_DIR$/out')
        module.testOutputDir == new Path('file://$MODULE_DIR$/outTest')
        (module.dependencies as List) == customDependencies
    }

    def initWithReaderAndDependencyVars() {
        def replacable = '$GRADLE_CACHE$'
        def replacer = '$MODULE_DIR$/../'
        module = createModule(dependencyVariableReplacement: new VariableReplacement(replacable: replacable, replacer: replacer), reader: customModuleReader)

        expect:
        module.sourceFolders == CUSTOM_SOURCE_FOLDERS
        module.testSourceFolders == CUSTOM_TEST_SOURCE_FOLDERS
        module.excludeFolders == CUSTOM_EXCLUDE_FOLDERS
        module.outputDir == new Path('file://$MODULE_DIR$/out')
        module.testOutputDir == new Path('file://$MODULE_DIR$/outTest')
        def expectedDependencies = customDependencies.collect { dependency ->
            if (dependency instanceof ModuleLibrary) {
                dependency.classes = dependency.classes.collect { Path path ->
                    Path newPath = path
                    if (path.url.contains(replacable)) {
                        newPath = new Path(path.url.replace(replacable, replacer))
                    }
                    newPath
                }
            }
            dependency
        }
        module.dependencies == (expectedDependencies as Set)
    }

    def initWithReaderAndValues_shouldBeMerged() {
        def constructorSourceFolders = [new Path('a')] as Set
        def constructorTestSourceFolders = [new Path('b')] as Set
        def constructorExcludeFolders = [new Path('c')] as Set
        def constructorOutputDir = new Path('someOut')
        def constructorJavaVersion = '1.6'
        def constructorTestOutputDir = new Path('someTestOut')
        def constructorModuleDependencies = [
                customDependencies[0],
                new ModuleLibrary([new Path('x')], [], [], [new JarDirectory(new Path('y'), false)], null)] as LinkedHashSet
        module = createModule(sourceFolders: constructorSourceFolders, testSourceFolders: constructorTestSourceFolders,
                excludeFolders: constructorExcludeFolders, outputDir: constructorOutputDir, testOutputDir: constructorTestOutputDir,
                moduleLibraries: constructorModuleDependencies, javaVersion: constructorJavaVersion, reader: customModuleReader)

        expect:
        module.sourceFolders == CUSTOM_SOURCE_FOLDERS + constructorSourceFolders
        module.testSourceFolders == CUSTOM_TEST_SOURCE_FOLDERS + constructorTestSourceFolders
        module.excludeFolders == CUSTOM_EXCLUDE_FOLDERS + constructorExcludeFolders
        module.outputDir == constructorOutputDir
        module.testOutputDir == constructorTestOutputDir
        module.javaVersion == constructorJavaVersion
        module.dependencies as LinkedHashSet == ((customDependencies as LinkedHashSet) + constructorModuleDependencies as LinkedHashSet) as LinkedHashSet
    }

    def initWithNullReader_shouldUseDefaultValuesAndMerge() {
        def constructorSourceFolders = [new Path('a')] as Set
        module = createModule(sourceFolders: constructorSourceFolders)

        expect:
        module.javaVersion == Module.INHERITED
        module.sourceFolders == constructorSourceFolders
        module.dependencies.size() == 0
    }

    def initWithNullReader_shouldUseDefaultSkeleton() {
        when:
        module = createModule([:])

        then:
        new XmlParser().parse(toXmlReader).toString() == module.xml.toString()
    }

    def toXml_shouldContainCustomValues() {
        def constructorSourceFolders = [new Path('a')] as Set
        def constructorOutputDir = new Path('someOut')
        def constructorTestOutputDir = new Path('someTestOut')

        when:
        this.module = createModule(javaVersion: '1.6', sourceFolders: constructorSourceFolders, reader: defaultModuleReader,
                outputDir: constructorOutputDir, testOutputDir: constructorTestOutputDir)
        def module = createModule(reader: toXmlReader)

        then:
        this.module == module
    }

    def toXml_shouldContainSkeleton() {
        when:
        module = createModule(reader: customModuleReader)

        then:
        new XmlParser().parse(toXmlReader).toString() == module.xml.toString()
    }

    def beforeConfigured() {
        def constructorSourceFolders = [new Path('a')] as Set
        Action beforeConfiguredActions = { Module module ->
            module.sourceFolders.clear()
        } as Action

        when:
        module = createModule(sourceFolders: constructorSourceFolders, reader: customModuleReader, beforeConfiguredActions: beforeConfiguredActions)

        then:
        createModule(reader: toXmlReader).sourceFolders == constructorSourceFolders
    }

    def whenConfigured() {
        def constructorSourceFolder = new Path('a')
        def configureActionSourceFolder = new Path("c")

        Action whenConfiguredActions = { Module module ->
            assert module.sourceFolders.contains((CUSTOM_SOURCE_FOLDERS as List)[0])
            assert module.sourceFolders.contains(constructorSourceFolder)
            module.sourceFolders.add(configureActionSourceFolder)
        } as Action

        when:
        module = createModule(sourceFolders: [constructorSourceFolder] as Set, reader: customModuleReader,
                whenConfiguredActions: whenConfiguredActions)

        then:
        createModule(reader: toXmlReader).sourceFolders == CUSTOM_SOURCE_FOLDERS + ([constructorSourceFolder, configureActionSourceFolder] as LinkedHashSet)
    }

    private StringReader getToXmlReader() {
        StringWriter toXmlText = new StringWriter()
        module.toXml(toXmlText)
        return new StringReader(toXmlText.toString())
    }

    def withXml() {
        XmlTransformer withXmlActions = new XmlTransformer()
        module = createModule(reader: customModuleReader, withXmlActions: withXmlActions)

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

    private InputStreamReader getCustomModuleReader() {
        return new InputStreamReader(getClass().getResourceAsStream('customModule.xml'))
    }

    private InputStreamReader getDefaultModuleReader() {
        return new InputStreamReader(getClass().getResourceAsStream('defaultModule.xml'))
    }

    private Module createModule(Map customArgs) {
        Action dummyBroadcast = Mock()
        XmlTransformer xmlTransformer = new XmlTransformer()
        Map args = [contentPath: null, sourceFolders: [] as Set, testSourceFolders: [] as Set, excludeFolders: [] as Set, outputDir: null, testOutputDir: null,
                moduleLibraries: [] as Set, dependencyVariableReplacement: VariableReplacement.NO_REPLACEMENT, javaVersion: null,
                reader: null, beforeConfiguredActions: dummyBroadcast, whenConfiguredActions: dummyBroadcast, withXmlActions: xmlTransformer] + customArgs
        return new Module(args.contentPath, args.sourceFolders, args.testSourceFolders, args.excludeFolders, args.outputDir, args.testOutputDir,
                args.moduleLibraries, args.dependencyVariableReplacement, args.javaVersion, args.reader,
                args.beforeConfiguredActions, args.whenConfiguredActions, args.withXmlActions)
    }
}