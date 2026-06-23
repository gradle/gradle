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

package org.gradle.plugin.devel.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class PluginValidationFailureRenderingIntegrationSpec extends AbstractIntegrationSpec {

    def setup() {
        buildFile << """
            apply plugin: "java-gradle-plugin"
        """
    }

    def "renders single problem in failure block"() {
        file("src/main/java/MyTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                public String getNotAnnotated() { return null; }
                @TaskAction public void run() {}
            }
        """

        when:
        fails "validatePlugins"

        then:
        failure.assertHasResolution("For more on how to annotate task properties, please refer to https://docs.gradle.org/${distribution.version.version}/userguide/incremental_build.html#sec:task_input_output_annotations in the Gradle documentation.")
        failure.assertHasErrorOutput("""* What went wrong:
Execution failed for task ':validatePlugins' (registered by plugin 'org.gradle.java-gradle-plugin').
> Plugin validation failed with 1 problem
  Missing annotation
    Type 'MyTask' property 'notAnnotated' is missing an input or output annotation
      Properties must be annotated so that Gradle knows how to handle them during up-to-date checking
      For more information, please refer to ${docLink('missing_annotation')} in the Gradle documentation.
      Possible solutions:
        1. Add an input or output annotation.
        2. Mark it as @Internal.""")
    }

    def "renders multiple problems in failure block"() {
        file("src/main/java/MyTask.java") << """
            import org.gradle.api.*;
            import org.gradle.api.tasks.*;
            import org.gradle.work.*;

            @DisableCachingByDefault(because = "test task")
            public class MyTask extends DefaultTask {
                public String getNotAnnotatedOne() { return null; }
                public String getNotAnnotatedTwo() { return null; }
                @TaskAction public void run() {}
            }
        """

        when:
        fails "validatePlugins"

        then:
        failure.assertHasErrorOutput("""* What went wrong:
Execution failed for task ':validatePlugins' (registered by plugin 'org.gradle.java-gradle-plugin').
> Plugin validation failed with 2 problems
  Missing annotation
    Type 'MyTask' property 'notAnnotatedOne' is missing an input or output annotation
      Properties must be annotated so that Gradle knows how to handle them during up-to-date checking
      For more information, please refer to ${docLink('missing_annotation')} in the Gradle documentation.
      Possible solutions:
        1. Add an input or output annotation.
        2. Mark it as @Internal.
  Missing annotation
    Type 'MyTask' property 'notAnnotatedTwo' is missing an input or output annotation
      Properties must be annotated so that Gradle knows how to handle them during up-to-date checking
      For more information, please refer to ${docLink('missing_annotation')} in the Gradle documentation.
      Possible solutions:
        1. Add an input or output annotation.
        2. Mark it as @Internal.""")
    }

    private String docLink(String section) {
        "https://docs.gradle.org/${distribution.version.version}/userguide/validation_problems.html#${section}"
    }

}
