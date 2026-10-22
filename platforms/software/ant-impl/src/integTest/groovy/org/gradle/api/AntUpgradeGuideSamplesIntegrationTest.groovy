/*
 * Copyright 2026 the original author or authors.
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

package org.gradle.api

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

/**
 * Runs the samples of the Ant sections of the upgrade guide (upgrading_version_9.adoc),
 * ensuring they behave as advertised.
 */
class AntUpgradeGuideSamplesIntegrationTest extends AbstractIntegrationSpec {

    private static final String ORDER_SCHEMA = '''\
<xs:schema xmlns:xs="http://www.w3.org/2001/XMLSchema">
    <xs:element name="order">
        <xs:complexType>
            <xs:sequence>
                <xs:element name="id" type="xs:string"/>
            </xs:sequence>
        </xs:complexType>
    </xs:element>
</xs:schema>
'''

    private static final String XJC_ANT_BUILD_FILE = '''\
<project name="jaxb">
    <taskdef name="xjc" classname="com.sun.tools.xjc.XJCTask"/>
    <target name="generate">
        <xjc schema="${schema}" destdir="${destdir}" package="com.example.order"/>
    </target>
</project>
'''

    def setup() {
        executer.withRepositoryMirrors() // So the samples and these integ tests can match verbatim.
    }

    def "can run a tool's Ant task in a separate process with the Kotlin DSL"() {
        file("src/main/schema/order.xsd") << ORDER_SCHEMA
        file("src/main/ant/build.xml") << XJC_ANT_BUILD_FILE
        buildKotlinFile << '''
            val antClasspath = configurations.create("antClasspath")

            repositories {
                mavenCentral()
            }

            dependencies {
                antClasspath("org.apache.ant:ant:1.10.17")
                antClasspath("org.glassfish.jaxb:jaxb-xjc:4.0.9")
            }

            tasks.register<JavaExec>("generateJaxbClasses") {
                val buildFile = layout.projectDirectory.file("src/main/ant/build.xml")
                val schema = layout.projectDirectory.file("src/main/schema/order.xsd")
                val outputDir = layout.buildDirectory.dir("generated/jaxb")
                inputs.file(buildFile)
                inputs.file(schema)
                outputs.dir(outputDir)

                classpath = antClasspath
                mainClass = "org.apache.tools.ant.Main"
                args(
                    "-buildfile", buildFile.asFile.absolutePath,
                    "-Dschema=${schema.asFile.absolutePath}",
                    "-Ddestdir=${outputDir.get().asFile.absolutePath}",
                    "generate"
                )
            }
        '''

        when:
        succeeds("generateJaxbClasses")

        then:
        file("build/generated/jaxb/com/example/order/Order.java").assertIsFile()
        file("build/generated/jaxb/com/example/order/ObjectFactory.java").assertIsFile()
    }

    def "can run a tool's Ant task in a separate process with the Groovy DSL"() {
        file("src/main/schema/order.xsd") << ORDER_SCHEMA
        file("src/main/ant/build.xml") << XJC_ANT_BUILD_FILE
        buildFile << '''
            configurations.create("antClasspath")

            repositories {
                mavenCentral()
            }

            dependencies {
                antClasspath("org.apache.ant:ant:1.10.17")
                antClasspath("org.glassfish.jaxb:jaxb-xjc:4.0.9")
            }

            tasks.register("generateJaxbClasses", JavaExec) {
                def buildFile = layout.projectDirectory.file("src/main/ant/build.xml")
                def schema = layout.projectDirectory.file("src/main/schema/order.xsd")
                def outputDir = layout.buildDirectory.dir("generated/jaxb")
                inputs.file(buildFile)
                inputs.file(schema)
                outputs.dir(outputDir)

                classpath = configurations.antClasspath
                mainClass = "org.apache.tools.ant.Main"
                args(
                    "-buildfile", buildFile.asFile.absolutePath,
                    "-Dschema=${schema.asFile.absolutePath}",
                    "-Ddestdir=${outputDir.get().asFile.absolutePath}",
                    "generate"
                )
            }
        '''

        when:
        succeeds("generateJaxbClasses")

        then:
        file("build/generated/jaxb/com/example/order/Order.java").assertIsFile()
        file("build/generated/jaxb/com/example/order/ObjectFactory.java").assertIsFile()
    }

    def "can use Ant from the build script classpath with the Kotlin DSL"() {
        file("someFile.txt") << "content"
        buildKotlinFile << '''
            import org.apache.tools.ant.taskdefs.Checksum

            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath("org.apache.ant:ant:1.10.17")
                }
            }

            tasks.register("checksum") {
                val inputFile = layout.projectDirectory.file("someFile.txt")
                val outputDir = layout.buildDirectory.dir("checksums")
                inputs.file(inputFile)
                outputs.dir(outputDir)

                doLast {
                    val antProject = org.apache.tools.ant.Project()
                    antProject.init()

                    val checksum = Checksum()
                    checksum.project = antProject
                    checksum.setFile(inputFile.asFile)
                    checksum.setTodir(outputDir.get().asFile)
                    checksum.execute()
                }
            }
        '''

        when:
        succeeds("checksum")

        then:
        file("build/checksums/someFile.txt.MD5").assertIsFile()
    }

    def "can use Ant from the build script classpath with the Groovy DSL"() {
        file("someFile.txt") << "content"
        buildFile << '''
            import org.apache.tools.ant.taskdefs.Checksum

            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath("org.apache.ant:ant:1.10.17")
                }
            }

            tasks.register("checksum") {
                def inputFile = layout.projectDirectory.file("someFile.txt")
                def outputDir = layout.buildDirectory.dir("checksums")
                inputs.file(inputFile)
                outputs.dir(outputDir)

                doLast {
                    def antProject = new org.apache.tools.ant.Project()
                    antProject.init()

                    def checksum = new Checksum()
                    checksum.project = antProject
                    checksum.file = inputFile.asFile
                    checksum.todir = outputDir.get().asFile
                    checksum.execute()
                }
            }
        '''

        when:
        succeeds("checksum")

        then:
        file("build/checksums/someFile.txt.MD5").assertIsFile()
    }

    def "can replace tokens with a filter written by hand with the Kotlin DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildKotlinFile << '''
            tasks.register<Copy>("processTemplates") {
                from("src/templates")
                into(layout.buildDirectory.dir("templates"))

                val tokens = mapOf("version" to "1.0")
                inputs.properties(tokens)
                filter { line: String ->
                    tokens.entries.fold(line) { result, (name, value) -> result.replace("@$name@", value) }
                }
            }
        '''

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

    def "can replace tokens with a filter written by hand with the Groovy DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildFile << '''
            def tokens = [version: "1.0"]

            tasks.register("processTemplates", Copy) {
                inputs.properties(tokens)
                from("src/templates")
                into(layout.buildDirectory.dir("templates"))
                filter { String line ->
                    tokens.inject(line) { result, token -> result.replace("@${token.key}@", token.value) }
                }
            }
        '''

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

    def "can replace tokens with Ant's ReplaceTokens with the Kotlin DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildKotlinFile << '''
            import org.apache.tools.ant.filters.ReplaceTokens

            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath("org.apache.ant:ant:1.10.17")
                }
            }

            tasks.register<Copy>("processTemplates") {
                from("src/templates")
                into(layout.buildDirectory.dir("templates"))
                filter<ReplaceTokens>("tokens" to mapOf("version" to "1.0"))
            }
        '''

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

    def "can replace tokens with Ant's ReplaceTokens with the Groovy DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildFile << '''
            import org.apache.tools.ant.filters.ReplaceTokens

            buildscript {
                repositories {
                    mavenCentral()
                }
                dependencies {
                    classpath("org.apache.ant:ant:1.10.17")
                }
            }

            tasks.register("processTemplates", Copy) {
                from("src/templates")
                into(layout.buildDirectory.dir("templates"))
                filter(ReplaceTokens, tokens: [version: "1.0"])
            }
        '''

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

}
