/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.internal.cc.impl

import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest

class ConfigurationCacheClassLoaderCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

    File staticDataLib

    def setup() {
        staticDataLib = file("lib/StaticData.jar").tap {
            parentFile.mkdirs()
        }
        jarWithClasses(
            staticDataLib,
            StaticData: """
                import org.gradle.api.*;
                import org.gradle.api.tasks.*;
                import java.util.concurrent.atomic.AtomicInteger;

                public class StaticData extends DefaultTask {

                    private static final AtomicInteger value = new AtomicInteger();

                    private final String projectName = getProject().getName();

                    @TaskAction
                    void printValue() {
                        // When ClassLoaders are reused
                        // the 1st run should print `<project name>.value = 1`
                        // the 2nd run should print `<project name>.value = 2`
                        // and so on.
                        System.out.println(projectName + ".value = " + value.incrementAndGet());
                    }
                }
            """
        )
    }

    def "reuses cached ClassLoaders"() {

        given: "multiple sub-projects"
        kotlinFile "settings.$scriptExtension", '''
            include("foo:foo")
            include("bar:bar")
        '''
        kotlinFile "build.$scriptExtension", '''
            tasks.register("unused") {}
        '''

        // Make the classpath of :foo differ from :bar's
        // thus causing :foo:foo and :bar:bar to have separate ClassLoaders.
        buildscriptWithCustomClasspath "foo/build.$scriptExtension"

        // Load the StaticData class in the different sub-sub-projects
        // for a more interesting ClassLoader hierarchy.
        for (projectDir in ['foo/foo', 'bar/bar']) {
            file("$projectDir/build.$scriptExtension") << """
                buildscript {
                    dependencies {
                        classpath(files("${staticDataLib.toURI()}"))
                    }
                }

                tasks.register("ok", $classRef) {
                }
            """
        }

        when:
        configurationCacheRun ":foo:foo:ok", ":bar:bar:ok"

        then:
        outputContains("foo.value = 1")
        outputContains("bar.value = 1")

        when:
        configurationCacheRun ":foo:foo:ok", ":bar:bar:ok"

        then:
        outputContains("foo.value = 2")
        outputContains("bar.value = 2")

        when: 'one of the scripts change'
        file("foo/foo/build.$scriptExtension") << """
            tasks.register("unused") {}
        """

        and:
        configurationCacheRun ":foo:foo:ok", ":bar:bar:ok"

        then: 'classloaders are still reused since the classpath remains the same'
        outputContains("foo.value = 3")
        outputContains("bar.value = 3")

        and: 'configuration cache is retrieved successfully after reuse'
        configurationCacheRun ":foo:foo:ok", ":bar:bar:ok"

        then:
        outputContains("foo.value = 4")
        outputContains("bar.value = 4")

        where:
        scriptExtension | classRef
        'gradle'        | 'StaticData'
        'gradle.kts'    | 'StaticData::class'
    }

    def "reuses strict ClassLoaders but discards scripts with non-strict ones"() {
        given:
        settingsFile '''
            rootProject.name = "test"
            include("foo:foo")
            include("bar:bar")
        '''

        // Make the classpath of :foo differ from :bar's
        // thus causing :foo:foo and :bar:bar to have separate ClassLoaders.
        buildscriptWithCustomClasspath 'foo/build.gradle.kts'

        for (projectDir in ['foo/foo', 'bar/bar']) {
            kotlinFile "$projectDir/build.gradle.kts", """
                buildscript {
                    // make bar/bar classLoader non-strict by accessing classLoader
                    // this should cause the compiled script class to be discarded
                    ${projectDir.endsWith('bar') ? 'require(classLoader != null)' : ''}
                    dependencies {
                        classpath(files("${staticDataLib.toURI()}"))
                    }
                }

                class StaticScriptData {
                    companion object {
                        var script = 1
                    }
                }

                tasks.register("ok", StaticData::class) {
                    val id = "\${project.name}.script"
                    doLast { println("\$id = \${StaticScriptData.script++}") }
                }
            """
        }
        and: 'non-strict classloaders must be allowed'
        executer.withEagerClassLoaderCreationCheckDisabled()

        when:
        run "ok"

        then:
        outputContains("foo.value = 1")
        outputContains("foo.script = 1")
        outputContains("bar.value = 1")
        outputContains("bar.script = 1")

        when:
        configurationCacheRun "ok"

        then: 'strict foo/foo is fully reused but non-strict script bar/bar is discarded'
        outputContains("foo.value = 2")
        outputContains("foo.script = 2")
        outputContains("bar.value = 2")
        outputContains("bar.script = 1")
    }

    private void buildscriptWithCustomClasspath(String scriptFileName) {
        File someLib = file('lib/someLib.jar')
        jarWithClasses(someLib, SomeClass: 'class SomeClass {}')
        file(scriptFileName) << """
            buildscript { dependencies { classpath(files("${someLib.toURI()}")) } }
        """
    }

    private void configurationCacheRun(String... args) {
        run(AbstractConfigurationCacheIntegrationTest.ENABLE_CLI_OPT, *args)
    }
}
