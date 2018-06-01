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

import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.runtime.model.SpecInfo

/**
 * <h3>General</h3>
 * This global Spock extension adds three interceptors that automatically fill in all shared and non-shared fields and all
 * feature method parameters that are of type {@link org.gradle.testkit.runner.GradleRunner} and are still unassigned
 * (i. e. have value {@code null}) at injection time.
 *
 * <h3>Shared Fields</h3>
 *
 * {@link spock.lang.Shared @Shared} fields are filled in and cleaned up in a specification interceptor. This means that all
 * iterations of all feature methods see the same value with the same underlying data on disk and also all setup and
 * cleanup methods see those values. They are created before the specification starts and are cleaned up after the
 * specification ends.
 *
 * <h3>Non-shared Fields</h3>
 *
 * Non-shared fields are filled in and cleaned up in an iteration interceptor. This means that the fields are filled in
 * at the start of each iteration. Iteration means that for data-driven feature methods with a {@code where:} block the
 * fields are filled at the start of each individual iteration and at the start means before any setup methods. The
 * filled in objects and their underlying data on disk is available until the end of the iteration, i. e. until after
 * all cleanup methods.
 *
 * <h3>Parameters</h3>
 *
 * Parameters are filled in and cleaned up in a feature method interceptor. This means that each call of the feature
 * method (i. e. each individual iteration) gets individual injected values with individual underlying data on disk and
 * they are cleaned up as soon as the feature method is finished. Those values and their underlying data on disk are not
 * available during setup methods or in cleanup methods.
 * <p/>
 * As of Spock 1.0 special care has to be taken for data-driven features (methods with a {@code where:} block) due to
 * <a href="https://github.com/spockframework/spock/issues/651">issue #651</a> and
 * <a href="https://github.com/spockframework/spock/issues/652">issue #652</a>.<br/>
 * This means for a data-driven feature where a parameter has to be injected
 * <ul>
 *     <li>all data variables and all to-be-injected parameters have to be defined as method parameters</li>
 *     <li>all method parameters have to be assigned a value in the {@code where:} block</li>
 *     <li>
 *         the order of the method parameters has to be identical to the order of the data variables in the
 *         {@code where:} block
 *     </li>
 *     <li>the to-be-injected parameters have to be set to {@code null} in the {@code where:} block</li>
 * </ul>
 * <b>Example:</b>
 * <pre>
 * def test(a, b, GradleRunner gradleRunner) {
 *     expect:
 *     true
 *
 *     where:
 *     a    | b
 *     'a1' | 'b1'
 *     'a2' | 'b2'
 *
 *     and:
 *     gradleRunner = null
 * }
 * </pre>
 *
 * <h3>{@code GradleRunner} instances</h3>
 *
 * When injecting {@code GradleRunner} instances, they by default have a newly created temporary directory per instance
 * as project directory. Alternatively you can specify what should be used as project directory by annotating the
 * respective field or parameter with a
 * {@link org.gradle.testkit.runner.spock.ProjectDirProvider @ProjectDirProvider} annotation.
 */
class InjectGradleRunnerExtension extends AbstractGlobalExtension {
    @Override
    void visitSpec(SpecInfo spec) {
        // fill shared fields
        spec.addInterceptor new InjectGradleRunnerIntoFieldsInterceptor()

        // fill non-shared fields and parameters for all feature iterations
        spec.allFeatures.each {
            it.addIterationInterceptor new InjectGradleRunnerIntoFieldsInterceptor(false)
            it.featureMethod.addInterceptor new InjectGradleRunnerIntoParametersInterceptor()
        }
    }
}
