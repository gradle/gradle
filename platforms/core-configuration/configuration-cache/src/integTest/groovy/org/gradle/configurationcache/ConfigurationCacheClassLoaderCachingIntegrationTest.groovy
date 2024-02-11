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

package org.gradle.configurationcache

import org.gradle.integtests.fixtures.longlived.PersistentBuildProcessIntegrationTest

class ConfigurationCacheClassLoaderCachingIntegrationTest extends PersistentBuildProcessIntegrationTest {

    def "reuses cached ClassLoaders"() {

        given: 'a Task that holds some static data'
        File staticDataLib = file("lib/StaticData.jar").tap {
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

        and: "multiple sub-projects"
        settingsFile << """
            include 'foo:foo'
            include 'bar:bar'
        """

        // Make the classpath of :foo differ from :bar's
        // thus causing :foo:foo and :bar:bar to have separate ClassLoaders.
        File someLib = file('lib/someLib.jar')
        jarWithClasses(someLib, SomeClass: 'class SomeClass {}')

        file("foo/build.gradle") << """
            buildscript { dependencies { classpath(files('${someLib.toURI()}')) } }
        """

        // Load the StaticData class in the different sub-sub-projects
        // for a more interesting ClassLoader hierarchy.
        for (projectDir in ['foo/foo', 'bar/bar']) {
            file("$projectDir/build.gradle") << """
                buildscript { dependencies { classpath(files('${staticDataLib.toURI()}')) } }

                task ok(type: StaticData) {
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
    }

    private void configurationCacheRun(String... args) {
        run(AbstractConfigurationCacheIntegrationTest.ENABLE_CLI_OPT, *args)
    }
}
