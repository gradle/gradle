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

package org.gradle.testkit.runner.internal.spock

import spock.lang.Specification
import spock.lang.Subject

import java.nio.file.Path

class InjectGradleRunnerInterceptorBaseTest extends Specification {
    @Subject
    def injectGradleRunnerInterceptorBase = Spy(InjectGradleRunnerInterceptorBase)

    def 'null projectDirProvider throws IllegalArgumentException'() {
        when:
        injectGradleRunnerInterceptorBase.determineProjectDir(null, '"gradleRunner"')

        then:
        IllegalArgumentException iae = thrown()
        iae.message == 'The project dir provider closure for the GradleRunner "gradleRunner" returned \'null\''
    }

    def 'unsupported projectDirProvider class throws IllegalArgumentException'() {
        when:
        injectGradleRunnerInterceptorBase.determineProjectDir(new Object(), '"gradleRunner"')

        then:
        IllegalArgumentException iae = thrown()
        iae.message.startsWith '''
            The project dir provider closure for the GradleRunner "gradleRunner" returned an object of the unsupported type 'java.lang.Object'
            \tsupported types:
        '''.stripIndent().trim()
    }

    def 'null result from projectDirProvider extraction throws IllegalArgumentException'() {
        when:
        injectGradleRunnerInterceptorBase.determineProjectDir(Mock(Path), '"gradleRunner"')

        then:
        IllegalArgumentException iae = thrown()
        iae.message == 'The extracted directory from the project dir provider closure result for the GradleRunner "gradleRunner" is \'null\''
    }
}
