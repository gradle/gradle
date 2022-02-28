/*
 * Copyright 2022 the original author or authors.
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

class ConfigurationCacheValueSourceIntegrationTest extends AbstractConfigurationCacheIntegrationTest {

    def "#usage property from properties file used as build logic input"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildKotlinFile """

            import org.gradle.api.provider.*

            abstract class PropertyFromPropertiesFile : ValueSource<String, PropertyFromPropertiesFile.Parameters> {

                interface Parameters : ValueSourceParameters {

                    @get:InputFile
                    val propertiesFile: RegularFileProperty

                    @get:Input
                    val propertyName: Property<String>
                }

                override fun obtain(): String? = parameters.run {
                    propertiesFile.get().asFile.takeIf { it.isFile }?.inputStream()?.use {
                        java.util.Properties().apply { load(it) }
                    }?.get(propertyName.get()) as String?
                }
            }

            val isCi: Provider<String> = providers.of(PropertyFromPropertiesFile::class) {
                parameters {
                    propertiesFile.set(layout.projectDirectory.file("local.properties"))
                    propertyName.set("ci")
                }
            }

            if ($expression) {
                tasks.register("run") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("run") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when: "running without a file present"
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when: "running with an empty file"
        file("local.properties") << ""
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when: "running with the property present in the file"
        file("local.properties") << "ci=true"
        configurationCacheRun "run"

        then:
        output.count("ON CI") == 1
        configurationCache.assertStateStored()

        when: "running after changing the file without changing the property value"
        file("local.properties") << "\nunrelated.properties=foo"
        configurationCacheRun "run"

        then:
        output.count("ON CI") == 1
        // TODO(mlopatkin) maybe revisit this decision if we decide that ValueSources are free to read whatever files they want
        configurationCache.assertStateStored()

        when: "running after changing the property value"
        file("local.properties").text = "ci=false"
        configurationCacheRun "run"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        where:
        expression                                     | usage
        'isCi.map(String::toBoolean).getOrElse(false)' | 'mapped'
        'isCi.getOrElse("false") != "false"'           | 'raw'
    }

    def "can define and use custom value source in a Groovy script"() {
        given:
        def configurationCache = newConfigurationCacheFixture()
        buildFile.text = """

            import org.gradle.api.provider.*

            abstract class IsSystemPropertySet implements ValueSource<Boolean, Parameters> {
                interface Parameters extends ValueSourceParameters {
                    Property<String> getPropertyName()
                }
                @Override Boolean obtain() {
                    System.getProperties().get(parameters.getPropertyName().get()) != null
                }
            }

            def isCi = providers.of(IsSystemPropertySet) {
                parameters {
                    propertyName = "ci"
                }
            }
            if (isCi.get()) {
                tasks.register("build") {
                    doLast { println("ON CI") }
                }
            } else {
                tasks.register("build") {
                    doLast { println("NOT CI") }
                }
            }
        """

        when:
        configurationCacheRun "build"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateStored()

        when:
        configurationCacheRun "build"

        then:
        output.count("NOT CI") == 1
        configurationCache.assertStateLoaded()

        when:
        configurationCacheRun "build", "-Dci=true"

        then:
        output.count("ON CI") == 1
        output.contains("because a build logic input of type 'IsSystemPropertySet' has changed")
        configurationCache.assertStateStored()
    }
}
