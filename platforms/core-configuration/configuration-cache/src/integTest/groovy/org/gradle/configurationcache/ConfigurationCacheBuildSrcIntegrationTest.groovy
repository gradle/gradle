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

import org.gradle.api.DefaultTask
import org.gradle.api.provider.Property
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

class ConfigurationCacheBuildSrcIntegrationTest extends AbstractConfigurationCacheIntegrationTest {
    def "can use task types defined in buildSrc"() {
        given:
        createDirs("buildSrc/ignored")
        file("buildSrc/settings.gradle") << """
            include 'ignored' // include some content
        """
        file("buildSrc/build.gradle") << """
            allprojects { apply plugin: 'java-library' } // include some content
        """
        file("buildSrc/src/main/java/CustomTask.java") << """
            import ${DefaultTask.name};
            import ${TaskAction.name};
            import ${Internal.name};
            import ${Property.name};

            public class CustomTask extends DefaultTask {
                private final Property<String> greeting = getProject().getObjects().property(String.class);

                @Internal
                public Property<String> getGreeting() {
                    return greeting;
                }

                @TaskAction
                public void run() {
                    System.out.println(getGreeting().get());
                }
            }
        """

        buildFile << """
            task greeting(type: CustomTask) {
                greeting = 'yo configuration cache'
            }
        """
        def configurationCache = newConfigurationCacheFixture()

        when:
        configurationCacheRun("greeting")

        then:
        result.assertTaskExecuted(":buildSrc:jar")
        result.assertTaskExecuted(":greeting")

        when:
        configurationCacheRun("greeting")

        then:
        result.assertTasksExecuted(":greeting") // buildSrc tasks are not executed
        outputContains("yo configuration cache")
        configurationCache.assertStateLoaded()
    }
}
