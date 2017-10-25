/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.performance.generator

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.integtests.tooling.fixture.TextUtil
import org.junit.Rule
import org.junit.rules.TemporaryFolder

class BazelJavaTestProjectIntegrationTest extends AbstractIntegrationSpec {
    @Rule
    TemporaryFolder tmpDir = new TemporaryFolder()

    File generatedDir

    def setup() {
        generatedDir = tmpDir.newFolder()
    }

    def "generating multi-project bazel build unsupported"() {
        given:
        def testProject = JavaTestProject.SMALL_JAVA_MULTI_PROJECT
        def generatedProjectPath = TextUtil.normaliseFileSeparators(generatedDir.absolutePath + "/" + testProject.projectName)

        when:
        new TestProjectGenerator(testProject.config).generate(generatedDir)

        then:
        !new File(generatedProjectPath, "BUILD").exists()
        !new File(generatedProjectPath, "WORKSPACE").exists()
    }

    def "generates monolithic Bazel build"() {
        given:
        def testProject = JavaTestProject.MEDIUM_MONOLITHIC_JAVA_PROJECT
        def generatedProjectPath = TextUtil.normaliseFileSeparators(generatedDir.absolutePath + "/" + testProject.projectName)

        when:
        new TestProjectGenerator(testProject.config).generate(generatedDir)

        then:
        def generatedBazelBuild = new File(generatedProjectPath, "BUILD")
        def generatedBazelWorkspace = new File(generatedProjectPath, "WORKSPACE")

        generatedBazelBuild.exists()
        generatedBazelWorkspace.exists()

        and:
        generatedBazelBuild.text ==
"""java_library(
    name = "assemble_all",
    srcs = glob(["src/main/java/**/*.java"]),
    deps = []
)


java_library(
    name = "commons_lang_commons_lang",
    visibility = ["//visibility:public"],
    exports = ["@commons_lang_commons_lang//jar"],
    runtime_deps = [],
)

java_library(
    name = "commons_httpclient_commons_httpclient",
    visibility = ["//visibility:public"],
    exports = ["@commons_httpclient_commons_httpclient//jar"],
    runtime_deps = [":junit_junit",":commons_logging_commons_logging",":commons_codec_commons_codec"],
)

java_library(
    name = "commons_codec_commons_codec",
    visibility = ["//visibility:public"],
    exports = ["@commons_codec_commons_codec//jar"],
    runtime_deps = [],
)

java_library(
    name = "org_slf_j_jcl_over_slf_j",
    visibility = ["//visibility:public"],
    exports = ["@org_slf_j_jcl_over_slf_j//jar"],
    runtime_deps = [":org_slf_j_slf_j_api"],
)


java_library(
    name = "com_googlecode_reflectasm",
    visibility = ["//visibility:public"],
    exports = ["@com_googlecode_reflectasm//jar"],
    runtime_deps = [":asm_asm"],
)"""

        generatedBazelWorkspace.text ==
"""maven_jar(
    name = "commons_lang_commons_lang",
    artifact = "commons-lang:commons-lang:2.5",
    sha1 = "b0236b252e86419eef20c31a44579d2aee2f0a69",
)

maven_jar(
    name = "commons_httpclient_commons_httpclient",
    artifact = "commons-httpclient:commons-httpclient:3.0",
    sha1 = "336a280d178bb957e5233189f0f32e067366c4e5",
)

maven_jar(
    name = "commons_codec_commons_codec",
    artifact = "commons-codec:commons-codec:1.2",
    sha1 = "397f4731a9f9b6eb1907e224911c77ea3aa27a8b",
)

maven_jar(
    name = "org_slf_j_jcl_over_slf_j",
    artifact = "org.slf4j:jcl-over-slf4j:1.7.10",
    sha1 = "ce49a188721bc39cb9710b346ae9b7ec27b0f36b",
)


maven_jar(
    name = "com_googlecode_reflectasm",
    artifact = "com.googlecode:reflectasm:1.01",
    sha1 = "85067c083609bb2827f4546fb5d76a36bee90357",
)"""
    }
}
