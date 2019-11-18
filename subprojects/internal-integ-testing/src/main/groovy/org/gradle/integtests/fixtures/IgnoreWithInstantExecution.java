/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures;

import org.spockframework.runtime.extension.ExtensionAnnotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;


/**
 * Ignore this test with instant execution.
 *
 * This is a temporary measure to work around Spock imposed limitations.
 *
 * One is that Spock prevents overriding test methods and annotate them with
 * {@link FailsWithInstantExecution}.
 *
 * Another example is partially failing unrolled tests.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.METHOD, ElementType.TYPE})
@ExtensionAnnotation(IgnoreWithInstantExecutionExtension.class)
public @interface IgnoreWithInstantExecution {

    Reason value();

    /**
     * Reason for ignoring a test with instant execution.
     */
    enum Reason {

        /**
         * Use this reason on tests that are explicitly not supported with instant execution.
         */
        UNSUPPORTED,

        /**
         * Use this reason on tests in super classes that fail on some subclasses.
         * Spock doesn't allow to override test methods and annotate them.
         */
        FAILS_IN_SUBCLASS,

        /**
         * Use this reason on tests that fail <code>:verifyTestFilesCleanup</code> with instant execution.
         */
        FAILS_TO_CLEANUP,

        /**
         * Use this reason on tests that intermittently fail with instant execution.
         */
        FLAKY,

        /**
         * Use this reason on tests that take a long time to fail, slowing down the CI feedback.
         * Use sparingly, only in dramatic cases.
         */
        LONG_TIMEOUT,

        /**
         * Use this reason if you don't know what is happening and want to postpone the investigation.
         */
        REQUIRES_INVESTIGATION
    }
}
