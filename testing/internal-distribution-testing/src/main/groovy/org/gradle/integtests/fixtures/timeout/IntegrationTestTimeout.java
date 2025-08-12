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

package org.gradle.integtests.fixtures.timeout;

import groovy.lang.Closure;
import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.util.concurrent.TimeUnit;

/**
 * Indicates that the execution of a method should time out
 * after the given duration has elapsed. The default time unit is seconds.
 *
 * This annotation can be used in similar way with {@link spock.lang.Timeout}, but shouldn't
 * be used together with {@link spock.lang.Timeout}. It should be annotated on
 * {@link org.gradle.integtests.fixtures.AbstractIntegrationSpec}. Upon timeout,
 * all threads' stack traces of current JVM (embedded executer) or forked JVM (forking executer)
 * are printed to help us debug deadlock issues.
 */

@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE, ElementType.METHOD})
@ExtensionAnnotation(IntegrationTestTimeoutExtension.class)
public @interface IntegrationTestTimeout {
    int DEFAULT_TIMEOUT_SECONDS = 600;

    /**
     * Returns the duration after which the execution of the annotated feature or fixture
     * method times out.
     *
     * @return the duration after which the execution of the annotated feature or
     * fixture method times out
     */
    int value();

    /**
     * Returns the duration's time unit.
     *
     * @return the duration's time unit
     */
    TimeUnit unit() default TimeUnit.SECONDS;

    /**
     * Only enables the timeout when the closure is evaluated to `true`.
     *
     * @return the closure
     */
    Class<? extends Closure> onlyIf() default AlwaysTrue.class;

    class AlwaysTrue extends Closure<Boolean> {
        public AlwaysTrue() {
            super(null);
        }

        @Override
        public Boolean call() {
            return true;
        }
    }
}
