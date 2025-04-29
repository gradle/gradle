/*
 * Copyright 2024 the original author or authors.
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

package org.gradle.api.tasks.diagnostics

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.util.GradleVersion

/**
 * Integration tests for the {@link ArtifactTransformsReportTask}.
 */
class ArtifactTransformsReportTaskIntegrationTest extends AbstractIntegrationSpec {
    private static final String CACHEABILITY_NUDGE = "Some artifact transforms are not cacheable.  This can have negative performance impacts.  See more documentation here: https://docs.gradle.org/${GradleVersion.current().version}/userguide/artifact_transforms.html#sec:caching-transforms."

    def setup() {
        settingsFile << """
            rootProject.name = "myLib"
        """
    }

    def "if no transforms present in project, task reports complete absence"() {
        expect:
        succeeds ':artifactTransforms'
        reportsCompleteAbsenceOfArtifactTransforms("root project 'myLib'")
        assertsDoesntNudgeAboutCacheability()
    }

    def "if single transform is registered by buildscript, task reports it"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineTransform()}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: No
From Attributes:
    - color = blue
    - shape = square
To Attributes:
    - color = red
    - shape = circle
""")
    }

    def "if cacheable transform is registered by buildscript, task reports it"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineTransform()}
            ${defineTransform("OtherTransform", true)}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                registerTransform(OtherTransform) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: No
From Attributes:
    - color = blue
To Attributes:
    - color = red

--------------------------------------------------
OtherTransform
--------------------------------------------------
Type: OtherTransform
Cacheable: Yes
From Attributes:
    - shape = square
To Attributes:
    - shape = circle
""")

        assertsNudgesAboutCacheability()
    }

    def "if multiple non-cacheable transforms is registered by buildscript, task reports them without cacheability hint"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineTransform("EmptyTransform", true)}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                registerTransform(EmptyTransform) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: Yes
From Attributes:
    - color = blue
To Attributes:
    - color = red

--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: Yes
From Attributes:
    - shape = square
To Attributes:
    - shape = circle
""")

        assertsDoesntNudgeAboutCacheability()
    }

    def "report can filter transforms by type"() {
        given:
        buildFile << """
            ${defineAttributes()}
            ${defineTransform()}
            ${defineTransform("PrintingTransform")}

            dependencies {
                registerTransform(EmptyTransform) {
                    from.attribute(color, "blue")
                    to.attribute(color, "red")
                }

                registerTransform(PrintingTransform) {
                    from.attribute(shape, "square")
                    to.attribute(shape, "circle")
                }

                registerTransform(EmptyTransform) {
                    from.attribute(color, "yellow")
                    to.attribute(color, "purple")
                }

                registerTransform(PrintingTransform) {
                    from.attribute(shape, "rectangle")
                    to.attribute(shape, "triangle")
                }
            }
        """

        when:
        succeeds ':artifactTransforms', '--type', 'EmptyTransform'

        then:
        result.groupedOutput.task(":artifactTransforms").assertOutputContains("""--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: No
From Attributes:
    - color = blue
To Attributes:
    - color = red

--------------------------------------------------
EmptyTransform
--------------------------------------------------
Type: EmptyTransform
Cacheable: No
From Attributes:
    - color = yellow
To Attributes:
    - color = purple
""")

        assertsNudgesAboutCacheability()
    }

    private String defineAttributes() {
        return """
            def color = Attribute.of("color", String)
            def shape = Attribute.of("shape", String)
        """
    }

    private String defineTransform(String name = "EmptyTransform", boolean cacheable = false) {
        return """
            ${cacheable ? "@CacheableTransform" : ""}
            abstract class $name implements TransformAction<org.gradle.api.artifacts.transform.TransformParameters.None> {
                void transform(TransformOutputs outputs) {}
            }
        """
    }

    private void reportsCompleteAbsenceOfArtifactTransforms(String projectDisplayName = "project 'myLib'") {
        outputContains("There are no transforms registered in $projectDisplayName.")
    }

    private void assertsNudgesAboutCacheability() {
        outputContains(CACHEABILITY_NUDGE)
    }

    private void assertsDoesntNudgeAboutCacheability() {
        outputDoesNotContain(CACHEABILITY_NUDGE)
    }
}
