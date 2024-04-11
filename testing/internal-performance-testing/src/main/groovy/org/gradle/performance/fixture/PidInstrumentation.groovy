/*
 * Copyright 2018 the original author or authors.
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

package org.gradle.performance.fixture

import groovy.transform.CompileStatic
import groovy.transform.PackageScope

/**
 * Instruments a Gradle build so we can query its process ID.
 */
@CompileStatic
@PackageScope
class PidInstrumentation {

    private final File pidFile = createPidFile()
    private final File pidFileInitScript = createPidFileInitScript(pidFile)

    List<String> getGradleArgs() {
        ["--init-script", pidFileInitScript.absolutePath]
    }

    String getPid() {
        pidFile.text
    }

    private static File createPidFile() {
        def pidFile = File.createTempFile("build-under-test", ".pid")
        pidFile.deleteOnExit()
        pidFile
    }

    private static File createPidFileInitScript(File pidFile) {
        def pidFileInitScript = File.createTempFile("pid-instrumentation", ".gradle")
        pidFileInitScript.deleteOnExit()
        pidFileInitScript.text = """
            def e
            if (gradleVersion == '2.0') {
              e = services.get(org.gradle.internal.nativeplatform.ProcessEnvironment)
            } else {
              e = services.get(org.gradle.internal.nativeintegration.ProcessEnvironment)
            }
            new File(new URI('${pidFile.toURI()}')).text = e.pid
        """
        pidFileInitScript
    }
}
