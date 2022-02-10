/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.integtests.tooling.r25

import org.gradle.integtests.tooling.fixture.ContinuousBuildToolingApiSpecification
import org.gradle.tooling.ProjectConnection
import org.gradle.tooling.model.GradleProject

class ContinuousBuildCrossVersionSpec extends ContinuousBuildToolingApiSpecification {

    def "can run continuous build with tooling api"() {
        when:
        def javaSrcFile = sourceDir.file("Thing.java") << 'public class Thing {}'

        then:
        runBuild {
            succeeds()
            waitBeforeModification javaSrcFile
            javaSrcFile.text = 'public class Thing { public static final int FOO=1; }'
            succeeds()
        }
    }

    def "can recover from failures"() {
        when:
        def javaSrcFile = sourceDir.file("Thing.java") << 'public class Thing {}'

        then:
        runBuild {
            succeeds()
            waitBeforeModification javaSrcFile
            javaSrcFile.text = 'public class Thing { *******'
            fails()
            waitBeforeModification javaSrcFile
            javaSrcFile.text = 'public class Thing {} '
            succeeds()
        }
    }

    def "client can request continuous mode when building a model, but request is effectively ignored"() {
        when:
        // take care to not use runBuild which implicitly calls cancel
        // we want to make sure it doesn't need cancellation
        GradleProject project = withConnection { ProjectConnection connection ->
            connection.model(GradleProject.class)
                .withArguments("--continuous")
                .setStandardOutput(stdout)
                .setStandardError(stderr)
                .get()
        }

        then:
        project != null
        def logOutput = stdout.toString()
        !logOutput.contains(waitingMessage)
        !logOutput.contains(exitingContinuousBuildNoInputsDetectedMessage)
    }

    private String getExitingContinuousBuildNoInputsDetectedMessage() {
        return usesNativeWatchers()
            ? "Exiting continuous build as Gradle did not detect any file system inputs."
            : "Exiting continuous build as no executed tasks declared file system inputs."
    }

}
