/*
 * Copyright 2020 the original author or authors.
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

class BazelFileContentGenerator {

    private TestProjectGeneratorConfiguration config

    BazelFileContentGenerator(TestProjectGeneratorConfiguration config) {
        this.config = config
    }

    String generateWorkspace() {
        """
load("@bazel_tools//tools/build_defs/repo:http.bzl", "http_archive")
load("@bazel_tools//tools/build_defs/repo:maven_rules.bzl", "maven_dependency_plugin", "maven_jar")

RULES_JVM_EXTERNAL_TAG = "4.5"
RULES_JVM_EXTERNAL_SHA = "b17d7388feb9bfa7f2fa09031b32707df529f26c91ab9e5d909eb1676badd9a6"

http_archive(
    name = "rules_jvm_external",
    strip_prefix = "rules_jvm_external-%s" % RULES_JVM_EXTERNAL_TAG,
    sha256 = RULES_JVM_EXTERNAL_SHA,
    url = "https://github.com/bazelbuild/rules_jvm_external/archive/%s.zip" % RULES_JVM_EXTERNAL_TAG,
)

load("@rules_jvm_external//:defs.bzl", "maven_install")

maven_install(
    artifacts = [

    ],
    repositories = [
        "https://repo.maven.apache.org/maven2/"
    ],
)
"""
    }

    String generateBuildFile(Integer subProjectNumber, DependencyTree dependencyTree) {
        def isRoot = subProjectNumber == null
        def subProjectNumbers = dependencyTree.getChildProjectIds(subProjectNumber)
        def transitiveSubProjectNumbers = []
        subProjectNumbers?.each { n ->
            def transitive = dependencyTree.getChildProjectIds(n)
            transitiveSubProjectNumbers.addAll(transitive)
        }
        subProjectNumbers?.addAll(transitiveSubProjectNumbers)
        Set<String> subProjectDependencies = [] as HashSet
        if (subProjectNumbers?.size() > 0) {
            subProjectDependencies.addAll(subProjectNumbers.collect { "      \"//project$it:project$it\"".toString() })
        }
        if (subProjectNumber != null && subProjectNumber % 4 == 0 && subProjectNumber != 0) {
            subProjectDependencies.add("      \"//project0:project0\"")
        }
        if (!isRoot) {
            """
load("@rules_java//java:defs.bzl", "java_library")
load("//:junit.bzl", "junit_tests")

java_library(
    name = "project${subProjectNumber}",
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = ["-XepDisableAllChecks"],
    visibility = ["//visibility:public"],
    deps = [
${subProjectDependencies.join(",\n")}
    ]
)

junit_tests(
    name = "tests_for_project${subProjectNumber}",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        "project${subProjectNumber}",
        ${subProjectDependencies.join(",\n")}
    ],
)
"""
        } else {
            """
load("@rules_java//java:defs.bzl", "java_library")
load("//:junit.bzl", "junit_tests")

java_library(
    name = "${config.projectName}",
    srcs = glob(["src/main/java/**/*.java"]),
    javacopts = ["-XepDisableAllChecks"],
    visibility = ["//visibility:public"],
)

junit_tests(
    name = "tests_${config.projectName}",
    size = "small",
    srcs = glob(["src/test/java/**/*.java"]),
    deps = [
        "//:${config.projectName}"
    ]
)
"""
        }
    }

    String generateJunitHelper() {
        '''
# Copyright (C) 2016 The Android Open Source Project
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
# http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

# Skylark rule to generate a Junit4 TestSuite
# Assumes srcs are all .java Test files
# Assumes junit4 is already added to deps by the user.

# See https://github.com/bazelbuild/bazel/issues/1017 for background.

load("@rules_java//java:defs.bzl", "java_test")

_OUTPUT = """ import org.junit.runners.Suite;
        import org.junit.runner.RunWith;
        @RunWith(Suite.class)
        @Suite.SuiteClasses({ %s })
        public class %s {}
        """

_PREFIXES = ("org", "com", "edu")

def _SafeIndex(l, val):
    for i, v in enumerate(l):
        if val == v:
            return i
    return -1

def _AsClassName(fname):
    fname = [x.path for x in fname.files.to_list()][0]
    toks = fname[:-5].split("/")
    findex = -1
    for s in _PREFIXES:
        findex = _SafeIndex(toks, s)
        if findex != -1:
            break
    if findex == -1:
        fail("%s does not contain any of %s" % (fname, _PREFIXES))
    return ".".join(toks[findex:]) + ".class"

def _impl(ctx):
    classes = ",".join(
        [_AsClassName(x) for x in ctx.attr.srcs],
    )
    ctx.actions.write(output = ctx.outputs.out, content = _OUTPUT % (
        classes,
        ctx.attr.outname,
    ))

_GenSuite = rule(
    attrs = {
        "srcs": attr.label_list(allow_files = True),
        "outname": attr.string(),
    },
    outputs = {"out": "%{name}.java"},
    implementation = _impl,
)

def junit_tests(name, srcs, **kwargs):
    s_name = name.replace("-", "_") + "TestSuite"
    _GenSuite(
        name = s_name,
        srcs = srcs,
        outname = s_name,
    )
    java_test(
        name = name,
        test_class = s_name,
        srcs = srcs + [":" + s_name],
        **kwargs
    )
    '''
    }
}
