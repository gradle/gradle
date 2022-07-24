/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.testkit.runner

import org.gradle.testkit.runner.fixtures.InjectsPluginClasspath
import org.gradle.testkit.runner.fixtures.InspectsBuildOutput
import org.gradle.testkit.runner.fixtures.PluginUnderTest
import org.gradle.util.GradleVersion
import org.gradle.util.UsesNativeServices

import static org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading.IMPLEMENTATION_CLASSPATH_PROP_KEY
import static org.gradle.testkit.runner.internal.PluginUnderTestMetadataReading.PLUGIN_METADATA_FILE_NAME

@InjectsPluginClasspath
@UsesNativeServices
class GradleRunnerConventionalPluginClasspathInjectionIntegrationTest extends BaseGradleRunnerIntegrationTest {

    private final PluginUnderTest pluginUnderTest = new PluginUnderTest(file("pluginProject"))

    def setup() {
        buildFile << pluginUnderTest.useDeclaration
    }

    def "uses conventional plugin classpath if requested and is available"() {
        expect:
        pluginUnderTest.build().exposeMetadata {
            runner('helloWorld')
                .withPluginClasspath()
                .build()
        }
    }

    @InspectsBuildOutput
    def "does not use conventional plugin classpath if not requested"() {
        when:
        def result = pluginUnderTest.build().exposeMetadata {
            runner('helloWorld')
                .buildAndFail()
        }

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- $pluginRepositoriesDisplayName (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    @InspectsBuildOutput
    def "explicit classpath takes precedence over conventional classpath"() {
        given:
        def explicitClasspath = [file('does/not/exist')]

        when:
        def result = pluginUnderTest.exposeMetadata {
            runner('helloWorld')
                .withPluginClasspath()
                .withPluginClasspath(explicitClasspath)
                .buildAndFail()
        }

        then:
        execFailure(result).assertHasDescription("""
            |Plugin [id: 'com.company.helloworld'] was not found in any of the following sources:
            |
            |- Gradle Core Plugins (plugin is not in 'org.gradle' namespace)
            |- Gradle TestKit (classpath: ${explicitClasspath*.absolutePath.join(File.pathSeparator)})
            |- $pluginRepositoriesDisplayName (plugin dependency must include a version number for this source)
        """.stripMargin().trim())
    }

    def "throws if conventional classpath is requested and metadata cannot be found"() {
        when:
        runner('helloWorld')
            .withPluginClasspath()
            .buildAndFail()

        then:
        def t = thrown(InvalidPluginMetadataException)
        t.message == "Test runtime classpath does not contain plugin metadata file '$PLUGIN_METADATA_FILE_NAME'".toString()
    }

    def "throws if metadata contains an empty classpath"() {
        when:
        pluginUnderTest.implClasspath().exposeMetadata {
            runner('helloWorld')
                .withPluginClasspath()
                .buildAndFail()
        }

        then:
        def t = thrown(InvalidPluginMetadataException)
        t.message == "Plugin metadata file '${pluginUnderTest.metadataFile.toURI().toURL()}' has empty value for property named '$IMPLEMENTATION_CLASSPATH_PROP_KEY'".toString()
    }

    def "throws if metadata has no implementation classpath"() {
        when:
        pluginUnderTest.noImplClasspath().exposeMetadata {
            runner('helloWorld')
                .withPluginClasspath()
                .build()
        }

        then:
        def t = thrown(InvalidPluginMetadataException)
        t.message == "Plugin metadata file '${pluginUnderTest.metadataFile.toURI().toURL()}' does not contain expected property named '$IMPLEMENTATION_CLASSPATH_PROP_KEY'".toString()
    }

    private static String getPluginRepositoriesDisplayName() {
        return gradleVersion >= GradleVersion.version("4.4")
            ? "Plugin Repositories"
            : "Gradle Central Plugin Repository"
    }
}
