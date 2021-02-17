/*
 * Copyright 2020 the original author or authors.
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

package org.gradle.testfixtures

import org.gradle.api.internal.file.temp.FilePermissionsCheckerFixture
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.fixtures.executer.GradleContextualExecuter
import spock.lang.IgnoreIf

@IgnoreIf({ GradleContextualExecuter.embedded })
// These tests run builds that themselves run a build in a test worker with 'gradleApi()' dependency, which needs to pick up Gradle modules from a real distribution
class ProjectBuilderEndUserIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
        apply plugin: 'groovy'

        dependencies {
            implementation localGroovy()
            implementation gradleApi()
            testImplementation('org.spockframework:spock-core:1.0-groovy-2.4') {
                exclude module: 'groovy-all'
            }
        }

        ${jcenterRepository()}

        test.testLogging.exceptionFormat = 'full'
        """
        file("src/test/groovy/FilePermissionsChecker.groovy") << FilePermissionsCheckerFixture.createFileContents()
    }

    def "project builder has correctly set working directory"() {
        when:
        groovyTestSourceFile '''
        import org.gradle.testfixtures.ProjectBuilder
        import org.gradle.testfixtures.internal.ProjectBuilderImpl

        import java.io.File
        import spock.lang.Specification

        class Test extends Specification {

            def "system property is set"() {
                expect:
                System.getProperty(ProjectBuilderImpl.PROJECT_BUILDER_SYS_PROP) != null
            }

            def "project builder has expected user home"() {
                when:
                String userHome = new File(System.getProperty("user.home")).absolutePath.replace(File.separatorChar, '/' as char)
                File gradleUserHome = ProjectBuilder.builder().build().gradle.gradleUserHomeDir
                String gradleUserHomeAbsolutePath = gradleUserHome.absolutePath
                then:
                gradleUserHomeAbsolutePath.contains("/build/tmp/")
                gradleUserHomeAbsolutePath.contains("/gradle-project-builder/")
                // Comes from FilePermissionsCheckerFixture
                FilePermissionsChecker.assertSafeParentFile(gradleUserHome)
            }
        }
        '''
        then:
        succeeds('test')
    }

    def "project builder working directory can be changed by the user"() {
        when:
        buildFile << '''
        test {
            File customTestKitDir = file('my-custom-project-builder-dir')
            systemProperty('org.gradle.project.builder.dir', customTestKitDir)
        }
        '''
        groovyTestSourceFile '''
        import org.gradle.testfixtures.ProjectBuilder
        import org.gradle.testfixtures.internal.ProjectBuilderImpl

        import java.io.File
        import spock.lang.Specification

        class Test extends Specification {

            def "system property is set"() {
                expect:
                System.getProperty(ProjectBuilderImpl.PROJECT_BUILDER_SYS_PROP).endsWith('my-custom-project-builder-dir')
            }

            def "project builder has expected user home"() {
                when:
                File gradleUserHome = ProjectBuilder.builder().build().gradle.gradleUserHomeDir
                String gradleUserHomeAbsolutePath = gradleUserHome.absolutePath
                then:
                gradleUserHomeAbsolutePath.contains("/my-custom-project-builder-dir/")
                // Comes from FilePermissionsCheckerFixture
                FilePermissionsChecker.assertSafeParentFile(gradleUserHome)
            }
        }
        '''
        then:
        succeeds('test')
    }
}
