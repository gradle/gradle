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

package org.gradle.execution

import spock.lang.Specification
import org.gradle.StartParameter
import org.gradle.api.internal.GradleInternal

/**
 * by Szczepan Faber, created at: 1/8/13
 */
class OnlyWhenConfigureOnDemandTest extends Specification {

    private delegate = Mock(BuildConfigurationAction)
    private onlyWhen = new OnlyWhenConfigureOnDemand(delegate)
    private context = Mock(BuildExecutionContext)
    private startParameter = Mock(StartParameter)
    private gradle = Mock(GradleInternal)

    def "does not do anything when configure on demand is off"() {
        given:
        startParameter.configureOnDemand >> false

        when:
        onlyWhen.configure(context)

        then:
        1 * context.gradle >> gradle
        1 * gradle.startParameter >> startParameter
        1 * startParameter.configureOnDemand >> false
        1 * context.proceed()
        0 * _._
    }

    def "delegates when configure on demand is off"() {
        given:
        startParameter.configureOnDemand >> false

        when:
        onlyWhen.configure(context)

        then:
        1 * context.gradle >> gradle
        1 * gradle.startParameter >> startParameter
        1 * startParameter.configureOnDemand >> true
        1 * delegate.configure(context)
        0 * _._
    }
}
