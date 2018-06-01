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

package org.gradle.testkit.runner.spock

import java.lang.annotation.Retention
import java.lang.annotation.Target

import static java.lang.annotation.ElementType.FIELD
import static java.lang.annotation.ElementType.PARAMETER
import static java.lang.annotation.RetentionPolicy.RUNTIME

/**
 * This annotation can be used to set the project directory of a {@link org.gradle.testkit.runner.GradleRunner} field or
 * parameter that gets filled automatically.
 * <p/>
 * This annotation needs one argument, which is a closure that has to return what should be used as project directory
 * for the Gradle runner. The valid return types for the given closure are defined by the availability of a {@code get}
 * method for the type or one of its supertypes or interfaces in the class
 * {@link org.gradle.testkit.runner.internal.spock.ProjectDirProviderCategory}. These currently are {@link File},
 * {@link java.nio.file.Path} and {@link org.junit.rules.TemporaryFolder}.
 * <p/>
 * The closure must not return {@code null}.
 * <p/>
 * This annotation makes only sense on unassigned {@code GradleRunner} fields and parameters. On any other field or
 * parameter this annotation will be ignored.<br/>
 * <b>Example:</b>
 * <pre>
 * @Rule
 * private TemporaryFolder testProjectDir
 *
 * def test(@ProjectDirProvider({ testProjectDir }) GradleRunner gradleRunner) {
 *     expect:
 *     gradleRunner.build()
 * }
 * </pre>
 */
@Retention(RUNTIME)
@Target([FIELD, PARAMETER])
@interface ProjectDirProvider {
    /**
     * A closure that has to return what should be used as project directory for the annotated Gradle runner. The valid
     * return types for the given closure are defined by the availability of a {@code get} method for the type or one of
     * its supertypes or interfaces in the class {@link org.gradle.testkit.runner.internal.spock.ProjectDirProviderCategory}.
     * These currently are {@link File}, {@link java.nio.file.Path} and {@link org.junit.rules.TemporaryFolder}.
     * <p/>
     * The closure must not return {@code null}.
     */
    Class<Closure<?>> value()
}
