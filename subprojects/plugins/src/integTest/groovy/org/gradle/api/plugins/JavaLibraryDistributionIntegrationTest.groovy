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

import static org.hamcrest.Matchers.containsString

class JavaLibraryDistributionIntegrationTest extends WellBehavedPluginTest {

    @Override
    String getPluginId() {
        "java-library-distribution"
    }

    def canCreateADistributionWithSrcDistRuntime() {
        given:
        createDir('libs') {
            file 'a.jar'
        }
        createDir('src/main/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        and:
        settingsFile << "rootProject.name='canCreateADistributionWithSrcDistRuntime'"
        and:
        buildFile << """
		apply plugin:'java-library-distribution'

		    version = 1.2

            distributions{
                main{
				    baseName ='SuperApp'
				}
			}

			dependencies {
				runtime files('libs/a.jar')
			}
        """
        when:
        run 'distZip'
        then:
        def expandDir = file('expanded')
        file('build/distributions/SuperApp-1.2.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('lib/a.jar', 'file1.txt', 'dir2/file2.txt', 'canCreateADistributionWithSrcDistRuntime-1.2.jar')
    }

    def canCreateADistributionWithReasonableDefaults() {
        given:
        createDir('libs') {
            file 'a.jar'
        }
        settingsFile << "rootProject.name = 'DefaultJavaDistribution'"
        and:
        buildFile << """
        apply plugin:'java-library-distribution'
        dependencies {
            runtime files('libs/a.jar')
        }
        """
        when:
        run 'distZip'

        then:
        def expandDir = file('expanded')
        file('build/distributions/DefaultJavaDistribution.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('lib/a.jar', 'DefaultJavaDistribution.jar')
    }

    def failWithNullConfiguredDistributionName() {
        when:
        buildFile << """
            apply plugin:'java-library-distribution'
            distributions{
                main{
                    baseName = null
                }
            }
            """
        then:
        runAndFail 'distZip'
        failure.assertThatDescription(containsString("Distribution baseName must not be null or empty! Check your configuration of the distribution plugin."))
    }


    def canCreateADistributionIncludingOtherFile() {
        given:
        createDir('libs') {
            file 'a.jar'
        }
        createDir('src/dist') {
            file 'file1.txt'
            dir2 {
                file 'file2.txt'
            }
        }
        createDir('other') {
            file 'file3.txt'
        }
        createDir('other2') {
            file 'file4.txt'
        }
        and:
        settingsFile << "rootProject.name='canCreateADistributionIncludingOtherFile'"
        and:
        buildFile << """
		apply plugin:'java-library-distribution'
            distributions{
                main{
				    baseName ='SuperApp'
				    contents {
				        from  'other'
				        from ('other2'){
				            into('other2')
				        }

				    }
				}
			}

			dependencies {
				runtime files('libs/a.jar')
			}

        """
        when:
        run 'distZip'
        then:
        def expandDir = file('expanded')
        file('build/distributions/SuperApp.zip').unzipTo(expandDir)
        expandDir.assertHasDescendants('lib/a.jar', 'file1.txt', 'dir2/file2.txt', 'canCreateADistributionIncludingOtherFile.jar', 'file3.txt', 'other2/file4.txt')
    }
}
