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

package org.gradle.test.fixtures.file

import org.spockframework.runtime.extension.ExtensionAnnotation

import java.lang.annotation.ElementType
import java.lang.annotation.Inherited
import java.lang.annotation.Retention
import java.lang.annotation.RetentionPolicy
import java.lang.annotation.Target

/**
 * Instructs Spock to use {@link CleanupTestDirectoryExtension} to clean up the test directory provided by a
 * {@link org.gradle.test.fixtures.file.TestDirectoryProvider}.  This is to work around the fact that using
 * a test directory provider as a TestRule causes spock to swallow any test failures and the test directory
 * is cleaned up even for failed tests.  {@link CleanupTestDirectoryExtension} on the other hand, registers
 * an interceptor and listener which cleans up the test directory only when the test passes.
 *
 * The annotation needs to know which field is the {@link org.gradle.test.fixtures.file.TestDirectoryProvider}
 * to clean up.  It defaults to "temporaryFolder", which is the field for AbstractIntegrationSpec.  Other specs
 * can set it accordingly using "fieldName".
 */
@Inherited
@Retention(RetentionPolicy.RUNTIME)
@Target([ElementType.TYPE])
@ExtensionAnnotation(CleanupTestDirectoryExtension)
@interface CleanupTestDirectory {
    String fieldName() default "temporaryFolder";
}
