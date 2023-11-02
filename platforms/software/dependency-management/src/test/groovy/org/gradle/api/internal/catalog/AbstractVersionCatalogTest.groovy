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

package org.gradle.api.internal.catalog

import com.google.common.collect.Interners
import org.gradle.api.problems.Problems
import org.gradle.api.problems.internal.DefaultProblems
import org.gradle.api.problems.internal.emitters.NoOpProblemEmitter
import org.gradle.internal.operations.BuildOperationProgressEventEmitter
import org.gradle.util.TestUtil
import spock.lang.Specification
import spock.lang.Subject

import java.util.function.Supplier

class AbstractVersionCatalogTest extends Specification {
    @Subject
    DefaultVersionCatalogBuilder builder = createVersionCatalogBuilder()

    def createVersionCatalogBuilder() {
        def supplier = Stub(Supplier)
        def problems = new DefaultProblems(new NoOpProblemEmitter())
        new DefaultVersionCatalogBuilder(
            "libs",
            Interners.newStrongInterner(),
            Interners.newStrongInterner(),
            TestUtil.objectFactory(),
            supplier) {
            @Override
            protected Problems getProblemService() {
                problems
            }
        }
    }

}
