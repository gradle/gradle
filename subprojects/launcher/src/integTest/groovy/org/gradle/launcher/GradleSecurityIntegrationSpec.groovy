/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.launcher

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import spock.lang.IgnoreIf
import static org.gradle.integtests.fixtures.GradleDistributionExecuter.getSystemPropertyExecuter
import static org.gradle.util.internal.GradleJvmSystem.GradleSecurityManager.SYSTEM_EXIT_NOT_PERMITTED

class GradleSecurityIntegrationSpec extends AbstractIntegrationSpec {

    //Only makes sense for forking executer so that we don't try to exit the current process
    @IgnoreIf({ !getSystemPropertyExecuter().forks })
    def "does not allow client build System.exit"() {
        when:
        buildFile << "System.exit(1);"

        then:
        fails()
        failure.error.contains(SYSTEM_EXIT_NOT_PERMITTED)
    }
}