/*
 * Copyright 2025 the original author or authors.
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

class ConfigurationCacheGracefulDegradationIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "can declare applied plugin as CC incompatible"() {
        buildFile("buildSrc/src/main/groovy/foo.gradle", """
            gradle.addBuildListener(new BuildListener() {
                @Override
                void settingsEvaluated(Settings settings){}
                @Override
                void projectsLoaded(Gradle gradle){}
                @Override
                void projectsEvaluated(Gradle gradle){}
                @Override
                void buildFinished(BuildResult result){
                    println("Build finished callback from foo plugin")
                }
                })
        """)

        buildFile("buildSrc/build.gradle", """
            plugins {
                id("groovy-gradle-plugin")
            }
        """)

        buildFile """
            gradle.requireConfigurationCacheDegradationIf("Foo plugin isn't CC compatible") { true }
            plugins.apply("foo")
        """

        when:
        configurationCacheRun "help"

        then:
        outputContains("Build finished callback from foo plugin")
        postBuildOutputContains("build file 'build.gradle': Foo plugin isn't CC compatible")
    }
}
