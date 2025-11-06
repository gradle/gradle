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
//file:noinspection unused

package testengines
/**
 * TODO use the original TestEnginesFixture from testing-jvm.
 */
trait TestEnginesFixture {
    private static final String ENGINE_COPY_TO_DIR_NAME = "test-engine-build"

    abstract List<TestEngines> getEnginesToSetup()

    def cleanupSpec() {
        //engineBuildDir.deleteDir()
    }

    String enableEngineForSuite(String enginePath) {
        return """
                useJUnitJupiter()

                dependencies {
                    implementation files("${enginePath.replace(File.separatorChar, '/' as char)}")
                }
        """
    }


    enum TestEngines {
        BASIC_RESOURCE_BASED("rbt-engine"),
        RESOURCE_AND_CLASS_BASED("resource-and-class-engine")

        private final String name

        TestEngines(final String name) {
            this.name = name
        }

        String getName() {
            return name
        }
    }
}
