/*
 * Copyright 2010 the original author or authors.
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

package org.gradle

import org.apache.commons.io.IOUtils
import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.GradleVersion
import org.gradle.util.internal.GUtil
import spock.lang.Shared

import java.nio.charset.StandardCharsets
import java.util.zip.ZipEntry
import java.util.zip.ZipFile

import static org.hamcrest.CoreMatchers.containsString
import static org.hamcrest.CoreMatchers.equalTo
import static org.hamcrest.MatcherAssert.assertThat

abstract class DistributionIntegrationSpec extends AbstractIntegrationSpec {

    protected static final THIRD_PARTY_LIB_COUNT = 144
    //stdlib-kotlin and configuration-problems-base are already in other counts
    public static final int CORRECT_FOR_JARS_THAT_ARE_ALREADY_IN_OTHER_COUNTS = 2

    @Shared
    String baseVersion = GradleVersion.current().baseVersion.version

    def coreLibsFileNames = [
        "gradle-base-asm-",
        "gradle-base-services-",
        "gradle-base-services-groovy-",
        "gradle-build-cache-",
        "gradle-build-cache-base-",
        "gradle-build-cache-local-",
        "gradle-build-cache-packaging-",
        "gradle-build-cache-spi-",
        "gradle-build-events-",
        "gradle-build-operations-",
        "gradle-build-option-",
        "gradle-build-process-services-",
        "gradle-build-state-",
        "gradle-cli-",
        "gradle-client-services-",
        "gradle-concurrent-",
        "gradle-configuration-problems-base-",
        "gradle-core-",
        "gradle-core-api-",
        "gradle-daemon-main-",
        "gradle-daemon-protocol-",
        "gradle-daemon-server-",
        "gradle-daemon-services-",
        "gradle-declarative-dsl-api-",
        "gradle-declarative-dsl-core-",
        "gradle-declarative-dsl-evaluator-",
        "gradle-declarative-dsl-provider-",
        "gradle-declarative-dsl-tooling-models-",
        "gradle-enterprise-logging-",
        "gradle-enterprise-operations-",
        "gradle-enterprise-workers-",
        "gradle-execution-",
        "gradle-file-collections-",
        "gradle-file-temp-",
        "gradle-file-watching-",
        "gradle-files-",
        "gradle-functional-",
        "gradle-gradle-cli-",
        "gradle-gradle-cli-main-",
        "gradle-hashing-",
        "gradle-input-tracking-",
        "gradle-installation-beacon-",
        "gradle-instrumentation-agent-services-",
        "gradle-internal-instrumentation-api-",
        "gradle-io-",
        "gradle-jvm-services-",
        "gradle-launcher-",
        "gradle-logging-",
        "gradle-logging-api-",
        "gradle-messaging-",
        "gradle-model-core-",
        "gradle-model-groovy-",
        "gradle-native-",
        "gradle-normalization-java-",
        "gradle-persistent-cache-",
        "gradle-problems-",
        "gradle-problems-api-",
        "gradle-process-services-",
        "gradle-resources-",
        "gradle-runtime-api-info-",
        "gradle-serialization-",
        "gradle-service-lookup-",
        "gradle-service-provider-",
        "gradle-service-registry-builder-",
        "gradle-service-registry-impl-",
        "gradle-snapshots-",
        "gradle-stdlib-java-extensions-",
        "gradle-stdlib-kotlin-extensions-",
        "gradle-time-",
        "gradle-toolchains-jvm-shared-",
        "gradle-tooling-api-",
        "gradle-tooling-api-provider-",
        "gradle-worker-main-",
        "gradle-wrapper-shared-",
    ]

    abstract String getDistributionLabel()

    abstract int getMaxDistributionSizeBytes()

    /**
     * Change this whenever you add or remove subprojects for distribution core modules (lib/).
     */
    int getCoreLibJarsCount() {
        74
    }

    boolean coreLibsContains(String jarName) {
        coreLibsFileNames.any {
            jarName.startsWith(it)
        }
    }

    /**
     * Change this whenever you add or remove subprojects for distribution-packaged plugins (lib/plugins).
     */
    int getPackagedPluginsJarCount() {
        81
    }

    /**
     * Change this whenever you add or remove subprojects for distribution java agents (lib/agents).
     */
    int getAgentJarsCount() {
        1
    }

    /**
     * Change this if you added or removed dependencies.
     */
    int getThirdPartyLibJarsCount() {
        THIRD_PARTY_LIB_COUNT
    }

    int getLibJarsCount() {
        coreLibJarsCount - CORRECT_FOR_JARS_THAT_ARE_ALREADY_IN_OTHER_COUNTS + packagedPluginsJarCount + agentJarsCount + thirdPartyLibJarsCount
    }

    def "distribution size should not exceed a certain number"() {
        expect:
        def size = getZip().size()

        assert size <= getMaxDistributionSizeBytes(): "Distribution content needs to be verified. If the increase is expected, raise the size by ${Math.ceil((size - getMaxDistributionSizeBytes()) / 1024 / 1024)}"
    }

    def "no duplicate jar entries in distribution"() {
        given:
        def entriesByPath = zipEntries.groupBy { it.name }
        def dupes = entriesByPath.findAll { it.value.size() > 1 }

        when:
        def dupesWithCount = dupes.collectEntries { [it.key, it.value.size()] }

        then:
        dupesWithCount.isEmpty()
    }

    def "all files under lib directory are jars"() {
        when:
        def nonJarLibEntries = libZipEntries.findAll { !it.name.endsWith(".jar") }

        then:
        nonJarLibEntries.isEmpty()
    }

    def "no additional jars are added to the distribution"() {
        when:
        def jarLibEntries = libZipEntries.findAll { it.name.endsWith(".jar") }
//        gradle-8.10-20240623220000+0000/lib/gradle-runtime-api-info-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-kotlin-dsl-extensions-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-installation-beacon-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-api-metadata-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-daemon-server-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-daemon-main-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-gradle-cli-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-tooling-api-provider-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-launcher-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-gradle-cli-main-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-kotlin-dsl-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-declarative-dsl-provider-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-client-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-state-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-daemon-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-daemon-protocol-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-toolchains-jvm-shared-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-events-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-tooling-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-core-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-jvm-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-file-collections-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-execution-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-model-groovy-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-model-core-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-core-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/groovy-ant-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/ant-junit-1.10.13.jar
//gradle-8.10-20240623220000+0000/lib/ant-1.10.13.jar
//gradle-8.10-20240623220000+0000/lib/ant-launcher-1.10.13.jar
//gradle-8.10-20240623220000+0000/lib/gradle-resources-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-worker-main-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-problems-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-configuration-problems-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-logging-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-option-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-process-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-messaging-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-instrumentation-agent-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-process-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-problems-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-base-services-groovy-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-base-asm-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-base-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-normalization-java-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-enterprise-logging-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-logging-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-internal-instrumentation-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/asm-commons-9.7.jar
//gradle-8.10-20240623220000+0000/lib/asm-tree-9.7.jar
//gradle-8.10-20240623220000+0000/lib/asm-9.7.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-cache-local-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-cache-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-cache-packaging-8.10.jar
//gradle-8.10-20240623220000+0000/lib/commons-compress-1.26.1.jar
//gradle-8.10-20240623220000+0000/lib/commons-codec-1.16.1.jar
//gradle-8.10-20240623220000+0000/lib/gradle-persistent-cache-8.10.jar
//gradle-8.10-20240623220000+0000/lib/commons-io-2.15.1.jar
//gradle-8.10-20240623220000+0000/lib/commons-lang-2.6.jar
//gradle-8.10-20240623220000+0000/lib/commons-lang3-3.14.0.jar
//gradle-8.10-20240623220000+0000/lib/gradle-service-registry-builder-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-service-registry-impl-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-service-provider-8.10.jar
//gradle-8.10-20240623220000+0000/lib/error_prone_annotations-2.26.1.jar
//gradle-8.10-20240623220000+0000/lib/fastutil-8.5.2-min.jar
//gradle-8.10-20240623220000+0000/lib/gradle-serialization-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-file-watching-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-snapshots-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-functional-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-cache-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-wrapper-shared-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-files-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-hashing-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-input-tracking-8.10.jar
//gradle-8.10-20240623220000+0000/lib/guava-32.1.2-jre.jar
//gradle-8.10-20240623220000+0000/lib/groovy-json-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-console-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-groovydoc-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-docgenerator-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-templates-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-xml-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-astbuilder-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-dateutil-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-datetime-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-nio-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-sql-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-test-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-swing-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/groovy-3.0.21.jar
//gradle-8.10-20240623220000+0000/lib/gson-2.10.jar
//gradle-8.10-20240623220000+0000/lib/junit-4.13.2.jar
//gradle-8.10-20240623220000+0000/lib/hamcrest-core-1.3.jar
//gradle-8.10-20240623220000+0000/lib/javax.inject-1.jar
//gradle-8.10-20240623220000+0000/lib/jansi-1.18.jar
//gradle-8.10-20240623220000+0000/lib/jcl-over-slf4j-1.7.36.jar
//gradle-8.10-20240623220000+0000/lib/gradle-declarative-dsl-evaluator-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-declarative-dsl-core-8.10.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-compiler-embeddable-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-reflect-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlinx-serialization-json-jvm-1.6.2.jar
//gradle-8.10-20240623220000+0000/lib/kotlinx-serialization-core-jvm-1.6.2.jar
//gradle-8.10-20240623220000+0000/lib/gradle-stdlib-kotlin-extensions-8.10.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-stdlib-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/annotations-24.0.1.jar
//gradle-8.10-20240623220000+0000/lib/gradle-concurrent-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-service-lookup-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-io-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-enterprise-operations-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-file-temp-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-declarative-dsl-api-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-operations-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-time-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-build-cache-spi-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-stdlib-java-extensions-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-kotlin-dsl-tooling-models-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-enterprise-workers-8.10.jar
//gradle-8.10-20240623220000+0000/lib/tomlj-1.0.0.jar
//gradle-8.10-20240623220000+0000/lib/jsr305-3.0.2.jar
//gradle-8.10-20240623220000+0000/lib/jul-to-slf4j-1.7.36.jar
//gradle-8.10-20240623220000+0000/lib/kryo-2.24.0.jar
//gradle-8.10-20240623220000+0000/lib/log4j-over-slf4j-1.7.36.jar
//gradle-8.10-20240623220000+0000/lib/minlog-1.2.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/objenesis-2.6.jar
//gradle-8.10-20240623220000+0000/lib/slf4j-api-1.7.36.jar
//gradle-8.10-20240623220000+0000/lib/trove4j-1.0.20200330.jar
//gradle-8.10-20240623220000+0000/lib/xml-apis-1.4.01.jar
//gradle-8.10-20240623220000+0000/lib/javaparser-core-3.17.0.jar
//gradle-8.10-20240623220000+0000/lib/gradle-cli-8.10.jar
//gradle-8.10-20240623220000+0000/lib/gradle-kotlin-dsl-shared-runtime-8.10.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-script-runtime-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-scripting-common-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-scripting-jvm-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-scripting-compiler-embeddable-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-scripting-compiler-impl-embeddable-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-sam-with-receiver-compiler-plugin-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-assignment-compiler-plugin-embeddable-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/kotlinx-metadata-jvm-0.5.0.jar
//gradle-8.10-20240623220000+0000/lib/gradle-declarative-dsl-tooling-models-8.10.jar
//gradle-8.10-20240623220000+0000/lib/failureaccess-1.0.1.jar
//gradle-8.10-20240623220000+0000/lib/kotlin-daemon-embeddable-1.9.23.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-freebsd-amd64-libcpp-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-aarch64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-osx-aarch64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-osx-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-windows-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-windows-amd64-min-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-windows-i386-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-windows-i386-min-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-aarch64-ncurses5-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-aarch64-ncurses6-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-amd64-ncurses5-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/native-platform-linux-amd64-ncurses6-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-linux-aarch64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-linux-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-osx-aarch64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-osx-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-windows-amd64-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-windows-amd64-min-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-windows-i386-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/file-events-windows-i386-min-0.22-milestone-26.jar
//gradle-8.10-20240623220000+0000/lib/ant-antlr-1.10.14.jar
//gradle-8.10-20240623220000+0000/lib/antlr4-runtime-4.7.2.jar
//gradle-8.10-20240623220000+0000/lib/qdox-1.12.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-kotlin-dsl-provider-plugins-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugin-development-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-build-configuration-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-build-init-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-build-profile-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-antlr-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-enterprise-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-unit-test-fixtures-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-tooling-api-builders-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-kotlin-dsl-tooling-builders-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-declarative-dsl-tooling-builders-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-junit-platform-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/junit-platform-launcher-1.8.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/junit-platform-engine-1.8.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/junit-platform-commons-1.8.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-application-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-ide-plugins-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-jacoco-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-ide-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-base-ide-plugins-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-tooling-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-ide-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-ear-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-code-quality-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-groovy-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-java-library-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-jvm-test-fixtures-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-scala-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-war-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-java-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-test-report-aggregation-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-jvm-test-suite-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-signing-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-java-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-version-catalog-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-java-platform-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-language-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-language-groovy-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-language-java-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-language-jvm-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-jvm-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-toolchains-jvm-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-configuration-cache-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-core-serialization-codecs-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-platform-jvm-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-composite-builds-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-platform-native-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-workers-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-ivy-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-maven-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugin-use-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-publish-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-test-suites-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-plugins-distribution-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-diagnostics-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-platform-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-version-control-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-dependency-management-serialization-codecs-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-dependency-management-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-test-kit-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-resources-s3-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-build-cache-http-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-resources-http-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-resources-sftp-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-reporting-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-configuration-cache-base-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-bean-serialization-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-core-kotlin-extensions-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-flow-services-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-security-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-resources-gcs-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-jvm-infrastructure-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-testing-base-infrastructure-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-guava-serialization-codecs-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-stdlib-serialization-codecs-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-graph-serialization-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/aws-java-sdk-s3-1.12.651.jar
//gradle-8.10-20240623220000+0000/lib/plugins/aws-java-sdk-kms-1.12.651.jar
//gradle-8.10-20240623220000+0000/lib/plugins/aws-java-sdk-sts-1.12.651.jar
//gradle-8.10-20240623220000+0000/lib/plugins/aws-java-sdk-core-1.12.651.jar
//gradle-8.10-20240623220000+0000/lib/plugins/bcpg-jdk18on-1.78.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/bcutil-jdk18on-1.78.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/bcprov-jdk18on-1.78.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/testng-6.3.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/bsh-2.0b6.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-api-services-storage-v1-rev20220705-1.32.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-api-client-1.34.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-oauth-client-1.34.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-http-client-gson-1.42.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-http-client-apache-v2-1.42.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/google-http-client-1.42.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/httpclient-4.5.14.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-wrapper-main-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/opencensus-contrib-http-util-0.31.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/httpcore-4.4.14.jar
//gradle-8.10-20240623220000+0000/lib/plugins/maven-settings-builder-3.9.5.jar
//gradle-8.10-20240623220000+0000/lib/plugins/plexus-sec-dispatcher-2.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/plexus-cipher-2.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/ivy-2.5.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jmespath-java-1.12.651.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jackson-core-2.16.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jackson-databind-2.16.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jackson-annotations-2.16.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jatl-0.2.3.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jcifs-1.3.17.jar
//gradle-8.10-20240623220000+0000/lib/plugins/org.eclipse.jgit.ssh.apache-5.13.3.202401111512-r.jar
//gradle-8.10-20240623220000+0000/lib/plugins/sshd-sftp-2.12.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/sshd-core-2.12.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/sshd-common-2.12.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jcommander-1.78.jar
//gradle-8.10-20240623220000+0000/lib/plugins/org.eclipse.jgit-5.13.3.202401111512-r.jar
//gradle-8.10-20240623220000+0000/lib/plugins/joda-time-2.12.2.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jsch-0.2.16.jar
//gradle-8.10-20240623220000+0000/lib/plugins/jsoup-1.15.3.jar
//gradle-8.10-20240623220000+0000/lib/plugins/maven-builder-support-3.9.5.jar
//gradle-8.10-20240623220000+0000/lib/plugins/maven-model-3.9.5.jar
//gradle-8.10-20240623220000+0000/lib/plugins/maven-repository-metadata-3.9.5.jar
//gradle-8.10-20240623220000+0000/lib/plugins/maven-settings-3.9.5.jar
//gradle-8.10-20240623220000+0000/lib/plugins/plexus-interpolation-1.26.jar
//gradle-8.10-20240623220000+0000/lib/plugins/plexus-utils-3.5.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/dd-plist-1.27.jar
//gradle-8.10-20240623220000+0000/lib/plugins/snakeyaml-2.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/opentest4j-1.3.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-java-compiler-plugin-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/gradle-instrumentation-declarations-8.10.jar
//gradle-8.10-20240623220000+0000/lib/plugins/opencensus-api-0.31.1.jar
//gradle-8.10-20240623220000+0000/lib/plugins/eddsa-0.3.0.jar
//gradle-8.10-20240623220000+0000/lib/plugins/grpc-context-1.27.2.jar
//gradle-8.10-20240623220000+0000/lib/agents/gradle-instrumentation-agent-8.10.jar]


        then:
        //ME: This is not a foolproof way of checking that additional jars have not been accidentally added to the distribution
        //but should be good enough. If this test fails for you and you did not intend to add new jars to the distribution
        //then there is something to be fixed. If you intentionally added new jars to the distribution and this is now failing please
        //accept my sincere apologies that you have to manually bump the numbers here.
        jarLibEntries.size() == libJarsCount
    }

    protected List<? extends ZipEntry> getLibZipEntries() {
        zipEntries.findAll { !it.isDirectory() && it.name.tokenize("/")[1] == "lib" }
    }

    protected List<? extends ZipEntry> getZipEntries() {
        ZipFile zipFile = new ZipFile(zip)

        try {
            zipFile.entries().toList()
        } finally {
            zipFile.close()
        }
    }

    protected TestFile unpackDistribution(type = getDistributionLabel(), TestFile into = testDirectory) {
        TestFile zip = getZip(type)
        zip.usingNativeTools().unzipTo(into)
        assert into.listFiles().size() == 1
        into.listFiles()[0]
    }

    protected TestFile getZip(String type = getDistributionLabel()) {
        switch (type) {
            case 'bin':
                buildContext.binDistribution
                break
            case 'all':
                buildContext.allDistribution
                break
            case 'docs':
                buildContext.docsDistribution
                break
            case 'src':
                buildContext.srcDistribution
                break
            default:
                throw new RuntimeException("Unknown distribution type '$type'")
        }
    }

    protected void checkMinimalContents(TestFile contentsDir) {
        // Check it can be executed
        executer.inDirectory(contentsDir).usingExecutable('bin/gradle').withTasks("help").run()

        // Scripts
        contentsDir.file('bin/gradle').assertIsFile()
        contentsDir.file('bin/gradle.bat').assertIsFile()

        // Top level files
        contentsDir.file('LICENSE').assertIsFile()

        // Core libs
        def coreLibs = contentsDir.file("lib").listFiles().findAll {
            it.name.startsWith("gradle-") && !it.name.startsWith("gradle-api-metadata") && !it.name.startsWith("gradle-kotlin-dsl")
        }

        coreLibs.each { assert coreLibsContains(it.name) }
        assert coreLibs.size() == coreLibJarsCount

        coreLibs.each { println(it.name) }
        coreLibs.each { assertIsGradleJar(it) }

        def toolingApiJar = contentsDir.file("lib/gradle-tooling-api-${baseVersion}.jar")
        toolingApiJar.assertIsFile()
        assert toolingApiJar.length() < 500 * 1024 // tooling api jar is the small plain tooling api jar version and not the fat jar.

        // Kotlin DSL
        assertIsGradleJar(contentsDir.file("lib/gradle-kotlin-dsl-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-kotlin-dsl-extensions-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-kotlin-dsl-shared-runtime-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/gradle-kotlin-dsl-tooling-models-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-kotlin-dsl-provider-plugins-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-kotlin-dsl-tooling-builders-${baseVersion}.jar"))

        // Plugins
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-dependency-management-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-version-control-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-scala-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-code-quality-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-antlr-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-maven-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-signing-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ear-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-ide-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-native-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-platform-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-jvm-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-java-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-language-groovy-${baseVersion}.jar"))
        assertIsGradleJar(contentsDir.file("lib/plugins/gradle-enterprise-${baseVersion}.jar"))

        // Agents
        assertIsGradleJar(contentsDir.file("lib/agents/gradle-instrumentation-agent-${baseVersion}.jar"))

        // Docs
        contentsDir.file('README').assertIsFile()

        // Others
        assertIsGradleApiMetadataJar(contentsDir.file("lib/gradle-api-metadata-${baseVersion}.jar"))

        // Jars that must not be shipped
        assert !contentsDir.file("lib/tools.jar").exists()
        assert !contentsDir.file("lib/plugins/tools.jar").exists()
    }

    protected static void assertDocsExist(TestFile contentsDir, String version) {
        // Javadoc
        contentsDir.file('docs/javadoc/index.html').assertIsFile()
        contentsDir.file('docs/javadoc/index.html').assertContents(containsString("Gradle API ${version}"))
        contentsDir.file('docs/javadoc/org/gradle/api/Project.html').assertIsFile()

        // Userguide
        contentsDir.file('docs/userguide/userguide.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide.html').assertContents(containsString("Gradle User Manual</h1>"))
        contentsDir.file('docs/userguide/userguide_single.html').assertIsFile()
        contentsDir.file('docs/userguide/userguide_single.html').assertContents(containsString("<h1>Gradle User Manual: Version ${version}</h1>"))
        contentsDir.file('docs/userguide/userguide.pdf').assertIsFile()

        // DSL reference
        contentsDir.file('docs/dsl/index.html').assertIsFile()
        contentsDir.file('docs/dsl/index.html').assertContents(containsString("<title>Gradle DSL Version ${version}</title>"))
    }

    protected void assertIsGradleJar(TestFile jar) {
        jar.assertIsFile()
        assertThat(jar.name, jar.manifest.mainAttributes.getValue('Implementation-Version'), equalTo(baseVersion))
        assertThat(jar.name, jar.manifest.mainAttributes.getValue('Implementation-Title'), equalTo('Gradle'))
    }

    private static void assertIsGradleApiMetadataJar(TestFile jar) {
        new JarTestFixture(jar.canonicalFile).with {
            def apiDeclaration = GUtil.loadProperties(IOUtils.toInputStream(content("gradle-api-declaration.properties"), StandardCharsets.UTF_8))
            assert apiDeclaration.size() == 2
            assert apiDeclaration.getProperty("includes").contains(":org/gradle/api/**:")
            assert apiDeclaration.getProperty("excludes").split(":").size() == 1
        }
    }
}
