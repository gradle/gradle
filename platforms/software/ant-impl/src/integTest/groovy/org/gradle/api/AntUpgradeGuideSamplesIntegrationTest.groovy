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

    private static final String KOTLIN_REPLACE_TOKENS = '''\
tasks.register<Copy>("processTemplates") {
    from("src/templates")
    into(layout.buildDirectory.dir("templates"))

    val tokens = mapOf("version" to "1.0")
    inputs.properties(tokens)
    val pattern = Regex(tokens.keys.joinToString("|") { Regex.escape("@$it@") })
    filter { line: String -> pattern.replace(line) { tokens.getValue(it.value.removeSurrounding("@")) } }
}
'''

    private static final String GROOVY_REPLACE_TOKENS = '''\
import java.util.regex.Pattern

tasks.register("processTemplates", Copy) {
    from("src/templates")
    into(layout.buildDirectory.dir("templates"))

    def tokens = [version: "1.0"]
    inputs.properties(tokens)
    def pattern = tokens.keySet().collect { Pattern.quote("@${it}@") }.join("|")
    filter { String line -> line.replaceAll(pattern) { String match -> tokens[match[1..-2]] } }
}
'''

    // Tokens at the start and end of the content, chained, unknown, empty, adjacent, unterminated and containing the delimiter
    private static final String TOKENS_INPUT = '''\
@foo@
version=@version@ and @version@@version@
@@version@ @unknown@ @ @@ user@example.com
@empty@|@a@b@|@a@|@long
@longer
@longer@ @lon@
@special@ @multiline@ @multiline@@version@
@version@'''

    private static final String GENERATE_JAXB_CLASSES_TASK = '''\
import org.gradle.api.DefaultTask;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.DirectoryProperty;
import org.gradle.api.file.RegularFileProperty;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.CacheableTask;
import org.gradle.api.tasks.Classpath;
import org.gradle.api.tasks.Input;
import org.gradle.api.tasks.InputFile;
import org.gradle.api.tasks.OutputDirectory;
import org.gradle.api.tasks.PathSensitive;
import org.gradle.api.tasks.PathSensitivity;
import org.gradle.api.tasks.TaskAction;
import org.gradle.process.ExecOperations;

import javax.inject.Inject;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

@CacheableTask
public abstract class GenerateJaxbClasses extends DefaultTask {
    @Classpath
    public abstract ConfigurableFileCollection getToolClasspath();

    @InputFile
    @PathSensitive(PathSensitivity.RELATIVE)
    public abstract RegularFileProperty getSchema();

    @Input
    public abstract Property<String> getPackageName();

    @OutputDirectory
    public abstract DirectoryProperty getOutputDir();

    @Inject
    protected abstract ExecOperations getExecOperations();

    @TaskAction
    public void generate() throws IOException {
        File buildFile = new File(getTemporaryDir(), "build.xml");
        Files.writeString(buildFile.toPath(), """
            <project name="jaxb">
                <taskdef name="xjc" classname="com.sun.tools.xjc.XJCTask"/>
                <target name="generate">
                    <xjc schema="${schema}" destdir="${destdir}" package="${package}"/>
                </target>
            </project>
            """);

        getExecOperations().javaexec(spec -> {
            spec.classpath(getToolClasspath());
            spec.getMainClass().set("org.apache.tools.ant.Main");
            spec.args(
                "-buildfile", buildFile.getAbsolutePath(),
                "-Dschema=" + getSchema().get().getAsFile().getAbsolutePath(),
                "-Ddestdir=" + getOutputDir().get().getAsFile().getAbsolutePath(),
                "-Dpackage=" + getPackageName().get(),
                "generate"
            );
        });
    }
}
'''

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

    def setup() {
        executer.withRepositoryMirrors() // So the samples and these integ tests can match verbatim.
    }

    def "can run a tool's Ant task in a separate process with the Kotlin DSL"() {
        file("src/main/schema/order.xsd") << ORDER_SCHEMA
        file("buildSrc/src/main/java/GenerateJaxbClasses.java") << GENERATE_JAXB_CLASSES_TASK
        buildKotlinFile << '''
            val antTool = configurations.dependencyScope("antTool")
            val antClasspath = configurations.resolvable("antClasspath") {
                extendsFrom(antTool)
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                antTool("org.apache.ant:ant:1.10.17")
                antTool("org.glassfish.jaxb:jaxb-xjc:4.0.9")
            }

            tasks.register<GenerateJaxbClasses>("generateJaxbClasses") {
                toolClasspath.from(antClasspath)
                schema = layout.projectDirectory.file("src/main/schema/order.xsd")
                packageName = "com.example.order"
                outputDir = layout.buildDirectory.dir("generated/jaxb")
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
        file("buildSrc/src/main/java/GenerateJaxbClasses.java") << GENERATE_JAXB_CLASSES_TASK
        buildFile << '''
            def antToolScope = configurations.dependencyScope("antTool")
            def antClasspath = configurations.resolvable("antClasspath") {
                extendsFrom(antToolScope)
            }

            repositories {
                mavenCentral()
            }

            dependencies {
                antTool("org.apache.ant:ant:1.10.17")
                antTool("org.glassfish.jaxb:jaxb-xjc:4.0.9")
            }

            tasks.register("generateJaxbClasses", GenerateJaxbClasses) {
                toolClasspath.from(antClasspath)
                schema = layout.projectDirectory.file("src/main/schema/order.xsd")
                packageName = "com.example.order"
                outputDir = layout.buildDirectory.dir("generated/jaxb")
            }
        '''

        when:
        succeeds("generateJaxbClasses")

        then:
        file("build/generated/jaxb/com/example/order/Order.java").assertIsFile()
        file("build/generated/jaxb/com/example/order/ObjectFactory.java").assertIsFile()
    }

    def "can replace tokens with a filter with the Kotlin DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildKotlinFile << KOTLIN_REPLACE_TOKENS

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

    def "can replace tokens with a filter with the Groovy DSL"() {
        file("src/templates/app.properties") << "version=@version@"
        buildFile << GROOVY_REPLACE_TOKENS

        when:
        succeeds("processTemplates")

        then:
        file("build/templates/app.properties").text == "version=1.0"
    }

    def "filter replaces tokens like Ant's ReplaceTokens with the Kotlin DSL"() {
        file("src/templates/tokens.txt") << TOKENS_INPUT
        buildKotlinFile << KOTLIN_REPLACE_TOKENS.replace(
            'mapOf("version" to "1.0")',
            'mapOf("foo" to "@bar@", "bar" to "@baz@", "baz" to "foobar", "version" to "1.0", "empty" to "", "a@b" to "key with a delimiter", "longer" to "x", "special" to "\\$1 \\\\ \\n", "multiline" to "first\\nsecond")'
        ) << '''
            tasks.register<Copy>("withAnt") {
                from("src/templates")
                into(layout.buildDirectory.dir("ant"))
                filter<org.apache.tools.ant.filters.ReplaceTokens>("tokens" to mapOf("foo" to "@bar@", "bar" to "@baz@", "baz" to "foobar", "version" to "1.0", "empty" to "", "a@b" to "key with a delimiter", "longer" to "x", "special" to "\\$1 \\\\ \\n", "multiline" to "first\\nsecond"))
            }
        '''

        when:
        succeeds("processTemplates", "withAnt")

        then:
        file("build/templates/tokens.txt").text == file("build/ant/tokens.txt").text
        file("build/templates/tokens.txt").text.startsWith("@bar@\n")
    }

    def "filter replaces tokens like Ant's ReplaceTokens with the Groovy DSL"() {
        file("src/templates/tokens.txt") << TOKENS_INPUT
        buildFile << GROOVY_REPLACE_TOKENS.replace(
            '[version: "1.0"]',
            '[foo: "@bar@", bar: "@baz@", baz: "foobar", version: "1.0", empty: "", "a@b": "key with a delimiter", longer: "x", special: \'\\$1 \\\\ \\n\', multiline: "first\\nsecond"]'
        ) << '''
            tasks.register("withAnt", Copy) {
                from("src/templates")
                into(layout.buildDirectory.dir("ant"))
                filter(org.apache.tools.ant.filters.ReplaceTokens, tokens: [foo: "@bar@", bar: "@baz@", baz: "foobar", version: "1.0", empty: "", "a@b": "key with a delimiter", longer: "x", special: '\\$1 \\\\ \\n', multiline: "first\\nsecond"])
            }
        '''

        when:
        succeeds("processTemplates", "withAnt")

        then:
        file("build/templates/tokens.txt").text == file("build/ant/tokens.txt").text
        file("build/templates/tokens.txt").text.startsWith("@bar@\n")
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
