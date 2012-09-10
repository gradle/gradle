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

package org.gradle.api.plugins.buildcomparison.gradle

import org.gradle.integtests.fixtures.CrossVersionIntegrationSpec
import org.gradle.integtests.fixtures.ExecutionResult
import org.gradle.integtests.fixtures.GradleExecuter
import org.gradle.integtests.fixtures.TargetVersions
import org.gradle.util.TestFile
import org.jsoup.Jsoup
import org.jsoup.nodes.Document

@TargetVersions(["1.0", "1.1"])
class Pre12CompareGradleBuildsCrossVersionSpec extends CrossVersionIntegrationSpec {

    ExecutionResult result

    void applyPlugin(TestFile file = buildFile) {
        versionGuard(file) { "apply plugin: 'compare-gradle-builds'" }
    }

    def "can compare identical builds with source pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                sourceBuild.gradleVersion "${previous.version}"
            }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        runComparisonWithCurrent()
        sourceWasInferred()

        then:
        sourceBuildVersion == previous.version
        targetBuildVersion == current.version
    }

    def "can compare identical builds with target pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                targetBuild.gradleVersion "${previous.version}"
            }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        runComparisonWithCurrent()
        targetWasInferred()

        then:
        sourceBuildVersion == current.version
        targetBuildVersion == previous.version
    }

    def "can compare different builds with source pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                sourceBuild.gradleVersion "${previous.version}"
            }

            compileJava { options.debug = !options.debug }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        failBecauseNotIdentical()
        sourceWasInferred()

        then:
        sourceBuildVersion == previous.version
        targetBuildVersion == current.version
    }

    def "can compare different builds with target pre 1.2"() {
        given:
        applyPlugin()
        buildFile << "apply plugin: 'java'"
        versionGuard { """
            compareGradleBuilds {
                targetBuild.gradleVersion "${previous.version}"
            }

            compileJava { options.debug = !options.debug }
        """ }

        and:
        file("src/main/java/Thing.java") << "class Thing {}"

        when:
        failBecauseNotIdentical()
        targetWasInferred()

        then:
        sourceBuildVersion == current.version
        targetBuildVersion == previous.version
    }

    protected versionGuard(TestFile file = buildFile, Closure string) {
        file << "\nif (GradleVersion.current().version == '${current.version}') {\n"
        file << string()
        file << "\n}\n"
    }

    protected ExecutionResult runComparisonWithCurrent() {
        result = currentExecuter().run()
        result
    }

    protected GradleExecuter currentExecuter() {
        current.executer().withForkingExecuter().withTasks("compareGradleBuilds")
    }

    Document html(path = "build/reports/compareGradleBuilds/index.html") {
        Jsoup.parse(file(path), "utf8")
    }

    String getSourceBuildVersion(Document html = this.html()) {
        html.body().select("table")[0].select("tr")[2].select("td")[0].text()
    }

    String getTargetBuildVersion(Document html = this.html()) {
        html.body().select("table")[0].select("tr")[2].select("td")[1].text()
    }

    void failBecauseNotIdentical() {
        result = currentExecuter().runWithFailure()
        result.assertHasCause("The build outcomes were not found to be identical. See the report at: file:///")
    }

    void sourceWasInferred(Document html = this.html()) {
        hasInferredLogWarning("source")
        hasInferredHtmlWarning("source", html)
    }

    void targetWasInferred(Document html = this.html()) {
        hasInferredLogWarning("target")
        hasInferredHtmlWarning("target", html)
    }

    void hasInferredLogWarning(String buildName) {
        assert result.output.contains("The build outcomes for the $buildName build will be inferred from the")
    }

    void hasInferredHtmlWarning(String buildName, Document html) {
        assert html.body().select(".warning.inferred-outcomes").text().contains("Build outcomes were not able to be determined for the $buildName build")
    }

}
