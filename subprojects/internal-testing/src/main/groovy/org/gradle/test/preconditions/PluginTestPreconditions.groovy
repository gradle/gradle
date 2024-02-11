/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.test.preconditions

import org.gradle.test.precondition.TestPrecondition

class PluginTestPreconditions {
    static File locate(String shellCommand) {
        return [
            new File("/bin/$shellCommand"),
            new File("/usr/bin/$shellCommand"),
            new File("/usr/local/bin/$shellCommand"),
            new File("/opt/local/bin/$shellCommand")
        ].find { it.exists() }
    }

    static class BashAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return satisfied(UnitTestPreconditions.UnixDerivative) && locate("bash") != null
        }
    }

    static class DashAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return satisfied(UnitTestPreconditions.UnixDerivative) && locate("dash") != null
        }
    }

    static class StaticShAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return satisfied(UnitTestPreconditions.UnixDerivative) && locate("static-sh") != null
        }
    }

    static class ShellcheckAvailable implements TestPrecondition {
        @Override
        boolean isSatisfied() throws Exception {
            return satisfied(UnitTestPreconditions.UnixDerivative) && locate("shellcheck") != null
        }
    }
}
