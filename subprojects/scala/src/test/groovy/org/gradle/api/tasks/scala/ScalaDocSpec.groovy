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
package org.gradle.api.tasks.scala

import org.gradle.api.internal.AbstractTask
import org.gradle.api.tasks.AbstractSpockTaskTest
import org.gradle.util.WrapUtil

import static org.gradle.api.tasks.compile.AbstractCompileTest.TEST_PATTERN_1
import static org.gradle.api.tasks.compile.AbstractCompileTest.TEST_PATTERN_2
import static org.gradle.api.tasks.compile.AbstractCompileTest.TEST_PATTERN_3

public class ScalaDocSpec extends AbstractSpockTaskTest {
    ScalaDoc scalaDoc

    @Override
    public AbstractTask getTask() {
        return scalaDoc
    }

    def setup() {
        scalaDoc = createTask(ScalaDoc)
    }

    def "test Scala Includes"() {
        expect:
        scalaDoc.include(TEST_PATTERN_1, TEST_PATTERN_2) == scalaDoc
        scalaDoc.getIncludes().equals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2))

        scalaDoc.include(TEST_PATTERN_3) == scalaDoc
        scalaDoc.getIncludes().equals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3))
    }

    def "test Scala Excludes"() {
        expect:
        scalaDoc.exclude(TEST_PATTERN_1, TEST_PATTERN_2) == scalaDoc
        scalaDoc.getExcludes().equals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2))

        scalaDoc.exclude(TEST_PATTERN_3) == scalaDoc
        scalaDoc.getExcludes().equals(WrapUtil.toLinkedSet(TEST_PATTERN_1, TEST_PATTERN_2, TEST_PATTERN_3))
    }
}
