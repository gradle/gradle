/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.WellBehavedPluginTest
import org.gradle.integtests.fixtures.archives.TestReproducibleArchives

@TestReproducibleArchives
class JavaLibraryDistributionIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginName() {
        "java-library-distribution"
    }

    @Override
    String getMainTask() {
        return "distZip"
    }

    def "distribution includes project jar and runtime dependencies"() {
        given:
        settingsFile << "rootProject.name = 'DefaultJavaDistribution'"

        and:
        buildFile << """
        apply plugin: 'java-library-distribution'

        ${mavenCentralRepository()}
        dependencies {
            implementation 'commons-collections:commons-collections:3.2.2'
            api 'commons-cli:commons-cli:1.2'
            runtimeOnly 'commons-lang:commons-lang:2.6'
        }
        """

        when:
        run 'distZip'

        then:
        def expandDir = file('expanded')
        file('build/distributions/DefaultJavaDistribution.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'DefaultJavaDistribution/lib/commons-collections-3.2.2.jar',
                'DefaultJavaDistribution/lib/commons-cli-1.2.jar',
                'DefaultJavaDistribution/lib/commons-lang-2.6.jar',
                'DefaultJavaDistribution/DefaultJavaDistribution.jar')
        expandDir.file('DefaultJavaDistribution/DefaultJavaDistribution.jar').assertIsCopyOf(file('build/libs/DefaultJavaDistribution.jar'))
    }

    def "can include additional source files in distribution"() {
        given:
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        createDir('src/dist') {
            file 'dist1.txt'
            dir2 {
                file 'dist2.txt'
            }
        }
        createDir('others/dist') {
            file 'other1.txt'
            dir2 {
                file 'other2.txt'
            }
        }

        and:
        settingsFile << "rootProject.name='canCreateADistributionWithSrcDistRuntime'"

        and:
        buildFile << """
		apply plugin:'java-library-distribution'

        version = 1.2

        distributions {
            main {
                distributionBaseName = 'SuperApp'
                contents {
                    from 'others/dist'
                }
            }
        }

        ${mavenCentralRepository()}
        dependencies {
            runtimeOnly 'commons-lang:commons-lang:2.6'
        }
        """

        when:
        run 'distZip'

        then:
        def expandDir = file('expanded')
        file('build/distributions/SuperApp-1.2.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants(
                'SuperApp-1.2/lib/commons-lang-2.6.jar',
                'SuperApp-1.2/file1.txt',
                'SuperApp-1.2/dist1.txt',
                'SuperApp-1.2/other1.txt',
                'SuperApp-1.2/dir2/file2.txt',
                'SuperApp-1.2/dir2/dist2.txt',
                'SuperApp-1.2/dir2/other2.txt',
                'SuperApp-1.2/canCreateADistributionWithSrcDistRuntime-1.2.jar')
    }

    def "fails when distribution baseName is null"() {
        given:
        buildFile << """
            apply plugin:'java-library-distribution'

            distributions {
                main{
                    distributionBaseName = null
                    distributionBaseName.convention(null)
                }
            }
            """

        expect:
        executer.noDeprecationChecks()
        runAndFail 'distZip'
        failure.assertHasCause "Cannot query the value of property 'distributionBaseName' because it has no value available."
    }

    def "compile only dependencies are not included in distribution"() {
        given:
        mavenRepo.module('org.gradle.test', 'compile', '1.0').publish()
        mavenRepo.module('org.gradle.test', 'compileOnly', '1.0').publish()

        and:
        buildFile << """
apply plugin:'java-library-distribution'

distributions {
    main {
        distributionBaseName = 'sample'
    }
}

repositories {
    maven { url '$mavenRepo.uri' }
}

dependencies {
    implementation 'org.gradle.test:compile:1.0'
    compileOnly 'org.gradle.test:compileOnly:1.0'
}
"""
        when:
        run "installDist"

        then:
        file('build/install/sample/lib').allDescendants() == ['compile-1.0.jar'] as Set
    }
}
