/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.util

import org.gradle.api.JavaVersion
import org.gradle.internal.os.OperatingSystem
import org.testcontainers.DockerClientFactory

import java.util.function.BooleanSupplier

/**
 * Usage:
 * <pre>
 * <code>@</code>Requires(TestPrecondition.JDK17_OR_LATER)
 * def "test with environment expectations"() {
 *     // the test is executed with Java 17 or later
 * }
 * </pre>
 *
 * @see Requires
 */
public class TestPrecondition implements BooleanSupplier {

    private BooleanSupplier supplier;

    TestPrecondition(BooleanSupplier supplier) {
        this.supplier = supplier
    }

    @Override
    boolean getAsBoolean() {
        return supplier.getAsBoolean();
    }
}

