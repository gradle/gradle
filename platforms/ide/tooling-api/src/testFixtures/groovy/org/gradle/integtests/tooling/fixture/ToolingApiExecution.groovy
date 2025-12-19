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

package org.gradle.integtests.tooling.fixture

import org.gradle.api.internal.jvm.JavaVersionParser
import org.gradle.integtests.fixtures.executer.GradleDistribution
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor
import org.gradle.util.GradleVersion
import org.spockframework.runtime.extension.IMethodInvocation

import java.util.function.Predicate
import java.util.stream.Collectors

class ToolingApiExecution extends AbstractMultiTestInterceptor.Execution {

    private static final GradleVersion INSTALLATION_GRADLE_VERSION
    private static final GradleVersionPredicate GRADLE_VERSION_PREDICATE = new GradleVersionPredicate()

    static {
        // If we are testing a non-current tooling API version, we will have loaded the class using its classloader and thus
        // GradleVersion.current() will report that version. In order to set up the testing infrastructure, we need to
        // know the version of Gradle being built
        def currentVersionOverride = System.getProperty("org.gradle.integtest.currentVersion")
        if (currentVersionOverride != null) {
            INSTALLATION_GRADLE_VERSION = GradleVersion.version(currentVersionOverride)
        } else {
            INSTALLATION_GRADLE_VERSION = GradleVersion.current()
        }
    }

    final GradleDistribution toolingApi
    final GradleDistribution gradle

    private final GradleVersion toolingApiVersion
    private final GradleVersion gradleVersion

    ToolingApiExecution(GradleDistribution loadedDistribution, GradleDistribution packagedDistribution) {
        if (isClassloadedVersionCurrent()) {
            // Gradle current -> TAPI {source}
            this.gradle = packagedDistribution
            this.toolingApi = loadedDistribution
        } else {
            // TAPI {target} -> Gradle current
            this.gradle = loadedDistribution
            this.toolingApi = packagedDistribution
        }
        this.toolingApiVersion = GradleVersion.version(toolingApi.version.version)
        this.gradleVersion = GradleVersion.version(gradle.version.version)
    }

    private static boolean isClassloadedVersionCurrent() {
        return INSTALLATION_GRADLE_VERSION == GradleVersion.current()
    }

    @Override
    protected String getDisplayName() {
        return "TAPI ${displayName(toolingApiVersion)} -> Gradle ${displayName(gradleVersion)}"
    }

    @Override
    String toString() {
        return displayName
    }

    private static String displayName(GradleVersion version) {
        if (version == INSTALLATION_GRADLE_VERSION) {
            return "current"
        }
        return version.version
    }

    @Override
    boolean isTestEnabled(AbstractMultiTestInterceptor.TestDetails testDetails) {
        // We cannot use JavaVersionParser.parseCurrentMajorVersion, since that method
        // is new and the target distribution version of the class sometimes shadows the
        // version of this class that has the new method.
        int currentJavaVersion = JavaVersionParser.parseMajorVersion(System.getProperty("java.version"))
        return toolingApiSupported(testDetails, currentJavaVersion) && daemonSupported(testDetails, currentJavaVersion)
    }

    private boolean daemonSupported(AbstractMultiTestInterceptor.TestDetails testDetails, int jvmVersion) {
        List<TargetGradleVersion> gradleVersionAnnotations = testDetails.getAnnotations(TargetGradleVersion)
        return toVersionPredicate(gradleVersionAnnotations).test(this.gradleVersion) && gradle.daemonWorksWith(jvmVersion)
    }

    private boolean toolingApiSupported(AbstractMultiTestInterceptor.TestDetails testDetails, int jvmVersion) {
        List<ToolingApiVersion> toolingVersionAnnotations = testDetails.getAnnotations(ToolingApiVersion)
        return toVersionPredicate(toolingVersionAnnotations).test(this.toolingApiVersion) && toolingApi.clientWorksWith(jvmVersion)
    }

    private static Predicate<GradleVersion> toVersionPredicate(List<?> annotations) {
        if (annotations.isEmpty()) {
            return (v) -> true;
        }
        List<Predicate<GradleVersion>> predicates = annotations.stream().map { annotation ->
            GRADLE_VERSION_PREDICATE.toPredicate(constraintFor(annotation))
        }.collect(Collectors.toList())
        return (v) -> predicates.stream().allMatch { it.test(v) }
    }

    private static String constraintFor(annotation) {
        if(annotation.value() == "current"){
            return "=${INSTALLATION_GRADLE_VERSION.baseVersion.version}"
        }
        return annotation.value()
    }

    @Override
    protected void before(IMethodInvocation invocation) {
        ((ToolingApiSpecification) invocation.getInstance()).setTargetDist(gradle)
    }
}
