package org.gradle.kotlin.dsl.resolver

import org.gradle.kotlin.dsl.fixtures.AbstractIntegrationTest

import org.gradle.util.TextUtil.normaliseFileSeparators

import org.hamcrest.CoreMatchers.equalTo
import org.hamcrest.MatcherAssert.assertThat

import org.junit.Test


class SourceDistributionResolverIntegrationTest : AbstractIntegrationTest() {

    @Test
    fun `can download source distribution`() {

        withBuildScript("""

            val resolver = ${SourceDistributionResolver::class.qualifiedName}(project)
            for (sourceDir in resolver.sourceDirs()) {
                val path = sourceDir.toPath()
                val relativePath = path.parent.parent.parent.parent.relativize(path)
                println("*" + relativePath)
            }

        """)

        assertThat(
            build().output.linesPrefixedBy("*").map(::normaliseFileSeparators).toSet(),
            equalTo(expectedSourceDirs)
        )
    }

    private
    fun String.linesPrefixedBy(prefix: String) =
        lineSequence().filter { it.startsWith(prefix) }.map { it.removePrefix(prefix) }

    private
    val expectedSourceDirs = setOf(
        "announce/src/main/java", "announce/src/main/resources",
        "antlr/src/main/java", "antlr/src/main/resources",
        "base-services/src/main/java", "base-services-groovy/src/main/java",
        "build-cache/src/main/java", "build-cache-http/src/main/java",
        "build-cache-http/src/main/resources",
        "build-comparison/src/main/groovy", "build-comparison/src/main/java", "build-comparison/src/main/resources",
        "build-init/src/main/groovy", "build-init/src/main/java",
        "build-init/src/main/resources", "build-option/src/main/java",
        "cli/src/main/java", "code-quality/src/main/groovy", "code-quality/src/main/resources",
        "composite-builds/src/main/java", "composite-builds/src/main/resources",
        "core/src/main/java", "core/src/main/resources", "core-api/src/main/java",
        "dependency-management/src/main/java", "dependency-management/src/main/resources",
        "diagnostics/src/main/java", "diagnostics/src/main/resources",
        "docs/src/main/resources",
        "ear/src/main/java", "ear/src/main/resources",
        "files/src/main/java",
        "ide/src/main/java", "ide/src/main/resources",
        "ide-native/src/main/groovy", "ide-native/src/main/java", "ide-native/src/main/resources",
        "ide-play/src/main/java", "ide-play/src/main/resources", "installation-beacon/src/main/java",
        "internal-android-performance-testing/src/main/java",
        "internal-integ-testing/src/main/groovy", "internal-integ-testing/src/main/resources",
        "internal-performance-testing/src/main/groovy", "internal-performance-testing/src/main/resources",
        "internal-testing/src/main/groovy", "ivy/src/main/java", "ivy/src/main/resources",
        "jacoco/src/main/java", "jacoco/src/main/resources",
        "javascript/src/main/java", "javascript/src/main/resources",
        "jvm-services/src/main/java", "language-groovy/src/main/java",
        "language-java/src/main/java", "language-java/src/main/resources",
        "language-jvm/src/main/java", "language-jvm/src/main/resources",
        "language-native/src/main/java", "language-native/src/main/resources",
        "language-scala/src/main/java", "language-scala/src/main/resources",
        "launcher/src/main/java", "launcher/src/main/resources",
        "logging/src/main/java", "maven/src/main/java", "maven/src/main/resources",
        "messaging/src/main/java", "model-core/src/main/java",
        "model-groovy/src/main/java", "native/src/main/java", "osgi/src/main/java", "osgi/src/main/resources",
        "persistent-cache/src/main/java", "platform-base/src/main/java", "platform-base/src/main/resources",
        "platform-jvm/src/main/java", "platform-jvm/src/main/resources",
        "platform-native/src/main/java", "platform-native/src/main/resources",
        "platform-play/src/main/java", "platform-play/src/main/resources",
        "plugin-development/src/main/java", "plugin-development/src/main/resources",
        "plugin-use/src/main/java", "plugin-use/src/main/resources",
        "plugins/src/main/java", "plugins/src/main/resources",
        "process-services/src/main/java", "publish/src/main/java",
        "publish/src/main/resources", "reporting/src/main/java", "reporting/src/main/resources",
        "resources/src/main/java", "resources-gcs/src/main/java",
        "resources-gcs/src/main/resources", "resources-http/src/main/java", "resources-http/src/main/resources",
        "resources-s3/src/main/java", "resources-s3/src/main/resources",
        "resources-sftp/src/main/java", "resources-sftp/src/main/resources",
        "scala/src/main/java", "scala/src/main/resources", "signing/src/main/java", "signing/src/main/resources",
        "snapshots/src/main/java",
        "test-kit/src/main/java", "testing-base/src/main/java", "testing-base/src/main/resources",
        "testing-junit-platform/src/main/java", "testing-jvm/src/main/java", "testing-jvm/src/main/resources",
        "testing-native/src/main/java", "testing-native/src/main/resources",
        "tooling-api/src/main/java", "tooling-api-builders/src/main/java", "tooling-api-builders/src/main/resources",
        "tooling-native/src/main/java", "tooling-native/src/main/resources",
        "version-control/src/main/java", "version-control/src/main/resources",
        "workers/src/main/java", "workers/src/main/resources", "wrapper/src/main/java")
}
