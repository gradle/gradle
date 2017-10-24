/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.dependencylock.fixtures

import org.gradle.internal.SystemProperties
import org.gradle.test.fixtures.maven.MavenFileRepository

final class DependencyLockFixture {

    public static final String COPY_LIBS_TASK_NAME = 'copyLibs'

    private DependencyLockFixture() {
    }

    static String appliedPlugin() {
        """
            apply plugin: 'dependency-lock'
        """
    }

    static String mavenRepository(MavenFileRepository mavenRepo) {
        """
            repositories {
                maven { url "${mavenRepo.uri}" }
            }
        """
    }

    static String appliedPluginAndRepository(MavenFileRepository mavenRepo) {
        """
            ${appliedPlugin()}
            ${mavenRepository(mavenRepo)}
        """
    }

    static String customConfigurations(String... configurationNames) {
        """
            configurations {
                ${configurationNames.join(SystemProperties.instance.lineSeparator)}
            }
        """
    }

    static String copyLibsTask(String... configurationNames) {
        """
            task $COPY_LIBS_TASK_NAME(type: Copy) {
                ${configurationNames.collect { "from configurations.${it}" }.join(SystemProperties.instance.lineSeparator)}
                into "\$buildDir/libs"
            }
        """
    }
}
