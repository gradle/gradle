/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.configurationcache.fixtures

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class GradlePropertiesIncludedBuildFixture {

    static List<BuildWithGradleProperties> builds() {
        [new BuildSrc(), new IncludedBuild()]
    }

    static abstract class BuildWithGradleProperties {

        abstract void setup(AbstractIntegrationSpec spec)

        abstract String task()

        abstract String expectedPropertyOutput()

        protected static String echoTaskForProperty(String propertyName) {
            """
                task echo(type: DefaultTask) {
                    def property = providers.gradleProperty('$propertyName')
                        doFirst {
                            println("$propertyName: " + property.orNull)
                        }
                    }
            """
        }
    }

    static class BuildSrc extends BuildWithGradleProperties {

        @Override
        void setup(AbstractIntegrationSpec spec) {
            spec.file("buildSrc/gradle.properties") << "bar=101"
            spec.file("buildSrc/build.gradle") << echoTaskForProperty("bar")
        }

        @Override
        String task() {
            return ":buildSrc:echo"
        }

        @Override
        String expectedPropertyOutput() {
            return "bar: 101"
        }

        @Override
        String toString() {
            return "buildSrc"
        }
    }

    static class IncludedBuild extends BuildWithGradleProperties {

        @Override
        void setup(AbstractIntegrationSpec spec) {
            spec.file("included-build/gradle.properties") << "bar=101"
            spec.file("included-build/build.gradle") << echoTaskForProperty("bar")
            spec.settingsFile("includeBuild('included-build')")
        }

        @Override
        String task() {
            return ":included-build:echo"
        }

        @Override
        String expectedPropertyOutput() {
            return "bar: 101"
        }

        @Override
        String toString() {
            return "included build"
        }
    }
}
