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

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class ApplicationPluginIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        file("settings.gradle").text = "rootProject.name='AppPluginTestProject'"
        file("src/dist/read.me").createFile()
        file("src/main/resources/config.xml").createFile()
        file("src/main/resources/scripts/myscript").with {
            createFile()
            withWriter { itWriter ->
                itWriter.println """
                echo \$defaultJvmOpts
                echo \${classpath}
                echo \$myprop
                """
            }
        }
        file("src/main/resources/scripts/myscript.bat").with {
            createFile()
            withWriter { itWriter ->
                itWriter.println """
                echo \$defaultJvmOpts
                echo \${classpath}
                echo \$myprop
                """
            }
        }

        file("src/main/java/Ok.java").with {
            createFile()
            withWriter { itWriter ->
                itWriter.println """
                public class Ok {
                    public static void main(String[] args) {
                        System.out.println("Ok!");
                    }
                }
                """
            }
        }
    }

    def checkDefaultDistribution() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin: 'application'

            mainClassName = 'Ok'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                    exclude('scripts')
                }
            }

            """
        then:
        succeeds('distZip')
        and:
        file('build/distributions/AppPluginTestProject.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/AppPluginTestProject/bin/AppPluginTestProject.bat").assertIsFile()
        file("unzip/AppPluginTestProject/bin/AppPluginTestProject").assertIsFile()
        file("unzip/AppPluginTestProject/lib/AppPluginTestProject.jar").assertIsFile()
        file("unzip/AppPluginTestProject/config/config.xml").assertIsFile()
        file("unzip/AppPluginTestProject/read.me").assertIsFile()
    }

    def checkAlteredDistribution() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin:'application'

            applicationBinDir = '.'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                }
                exclude('scripts')
            }

            startScripts {
                mainClassName = 'Ok'
                classpath += files('config')
            }

            """
        then:
        succeeds('distZip')
        and:
        file('build/distributions/AppPluginTestProject.zip').usingNativeTools().unzipTo(file("unzip"))
        def winScript = file("unzip/AppPluginTestProject/AppPluginTestProject.bat")
        winScript.assertIsFile()
        winScript.text =~ /(?m)CLASSPATH=.*?%APP_HOME%\\config/
        def nixScript = file("unzip/AppPluginTestProject/AppPluginTestProject")
        nixScript.assertIsFile()
        nixScript.text =~ /(?m)CLASSPATH=.*?APP_HOME\/config/
        file("unzip/AppPluginTestProject/lib/AppPluginTestProject.jar").assertIsFile()
        file("unzip/AppPluginTestProject/config/config.xml").assertIsFile()
        file("unzip/AppPluginTestProject/read.me").assertIsFile()
    }

    def checkCustomScriptDistribution() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin:'application'

            applicationBinDir = '.'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                }
                exclude('scripts')
            }

            startScripts {
                mainClassName = 'Ok'
                classpath += files('config')
                token 'myprop', 'myvalue'
                unixStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript.bat')
                }
            }

            """
        then:
        succeeds('distZip')
        and:
        file('build/distributions/AppPluginTestProject.zip').usingNativeTools().unzipTo(file("unzip"))
        def winScript = file("unzip/AppPluginTestProject/AppPluginTestProject.bat")
        winScript.assertIsFile()
        winScript.text =~ /(?m)CLASSPATH=.*?%APP_HOME%\\config/
        def nixScript = file("unzip/AppPluginTestProject/AppPluginTestProject")
        nixScript.assertIsFile()
        nixScript.text =~ /(?m)CLASSPATH=.*?APP_HOME\/config/
        file("unzip/AppPluginTestProject/myscript.bat").assertDoesNotExist()
        def nixMyScript = file("unzip/AppPluginTestProject/myscript")
        nixMyScript.assertIsFile()
        nixMyScript.text =~ /echo myvalue/
        file("unzip/AppPluginTestProject/lib/AppPluginTestProject.jar").assertIsFile()
        file("unzip/AppPluginTestProject/config/config.xml").assertIsFile()
        file("unzip/AppPluginTestProject/config/scripts/myscript").assertDoesNotExist()
        file("unzip/AppPluginTestProject/read.me").assertIsFile()
    }

    def checkCustomTokenNotFound() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin:'application'

            applicationBinDir = '.'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                }
                exclude('scripts')
            }

            startScripts {
                mainClassName = 'Ok'
                classpath += files('config')
                //token 'myprop', 'myvalue' //myprop is expected but not given
                unixStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript.bat')
                }
            }

            """
        then:
        fails('distZip')
    }

    def checkNotEscapingDistribution() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin:'application'

            applicationBinDir = '.'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                }
                exclude('scripts')
            }

            startScripts {
                mainClassName = 'Ok'
                defaultJvmOpts = ['-Dmyopt=']
                quoteJvmOpts { system, jvmOpt ->
                    switch(jvmOpt) {
                        case '-Dmyopt=': return system.equals('nix') ? (jvmOpt + '\$MY_ENV_VAR') : (jvmOpt + '%MY_ENV_VAR%')
                        default: return jvmOpt
                    }
                }
                token 'myprop', 'myvalue'
                classpath += files('config')
                windowsStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript')
                }
                unixStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript.bat')
                }
            }

            """
        then:
        succeeds('distZip')
        and:
        file('build/distributions/AppPluginTestProject.zip').usingNativeTools().unzipTo(file("unzip"))
        def winScript = file("unzip/AppPluginTestProject/AppPluginTestProject.bat")
        winScript.assertIsFile()
        winScript.text =~ /(?m)CLASSPATH=.*?%APP_HOME%\\config/
        def nixScript = file("unzip/AppPluginTestProject/AppPluginTestProject")
        nixScript.assertIsFile()
        nixScript.text =~ /(?m)CLASSPATH=.*?APP_HOME\/config/
        def nixMyScript = file("unzip/AppPluginTestProject/myscript")
        nixMyScript.assertIsFile()
        nixMyScript.text =~ '-Dmyopt=\\$MY_ENV_VAR'
        def winMyScript = file("unzip/AppPluginTestProject/myscript.bat")
        winMyScript.assertIsFile()
        winMyScript.text =~ /-Dmyopt=%MY_ENV_VAR%/
        file("unzip/AppPluginTestProject/lib/AppPluginTestProject.jar").assertIsFile()
        file("unzip/AppPluginTestProject/config/config.xml").assertIsFile()
        file("unzip/AppPluginTestProject/config/scripts/myscript").assertDoesNotExist()
        file("unzip/AppPluginTestProject/read.me").assertIsFile()
    }

    def checkExcludeDefaultScripts() {
        when:
        buildFile << """
            apply plugin: 'java'
            apply plugin:'application'

            applicationBinDir = '.'
            applicationDistribution.with {
                into('config') {
                    from(processResources) {
                        includeEmptyDirs = false
                    }
                }
                exclude('scripts')
            }

            startScripts {
                mainClassName = 'Ok'
                defaultJvmOpts = ['-Dmyopt=']
                quoteJvmOpts { system, jvmOpt ->
                    switch(jvmOpt) {
                        case '-Dmyopt=': return system.equals('nix') ? (jvmOpt + '\$MY_ENV_VAR') : (jvmOpt + '%MY_ENV_VAR%')
                        default: return jvmOpt
                    }
                }
                token 'myprop', 'myvalue'
                classpath += files('config')
                windowsStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript')
                }
                windowsStartScripts.exclude('**/windowsStartScript.txt')
                unixStartScripts.from('src/main/resources/scripts') {
                    exclude('myscript.bat')
                }
                unixStartScripts.exclude('**/unixStartScript.txt')
            }

            """
        then:
        succeeds('distZip')
        and:
        file('build/distributions/AppPluginTestProject.zip').usingNativeTools().unzipTo(file("unzip"))
        file("unzip/AppPluginTestProject/AppPluginTestProject.bat").assertDoesNotExist()
        file("unzip/AppPluginTestProject/AppPluginTestProject").assertDoesNotExist()
        def nixMyScript = file("unzip/AppPluginTestProject/myscript")
        nixMyScript.assertIsFile()
        nixMyScript.text =~ '-Dmyopt=\\$MY_ENV_VAR'
        def winMyScript = file("unzip/AppPluginTestProject/myscript.bat")
        winMyScript.assertIsFile()
        winMyScript.text =~ /-Dmyopt=%MY_ENV_VAR%/
        file("unzip/AppPluginTestProject/lib/AppPluginTestProject.jar").assertIsFile()
        file("unzip/AppPluginTestProject/config/config.xml").assertIsFile()
        file("unzip/AppPluginTestProject/config/scripts/myscript").assertDoesNotExist()
        file("unzip/AppPluginTestProject/read.me").assertIsFile()
    }

}
