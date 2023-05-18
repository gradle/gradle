/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.snapshot.impl;

import org.gradle.api.Action;
import org.gradle.api.internal.lambdas.SerializableLambdas;

import java.io.Serializable;
import java.util.function.Consumer;

public class TestLambdas {

    private static final String STATIC_TEST_FIELD = "some string";

    private final String testField = "some instance string";

    public static Action<?> createUntrackableLambda() {
        return arg -> {};
    }

    public static SerializableLambdas.SerializableAction<?> createSerializableLambda() {
        return arg -> {};
    }

    public static SerializableLambdas.SerializableAction<?> createMethodRefLambda() {
        return TestLambdas::testMethod;
    }

    public static SerializableLambdas.SerializableAction<?> createClassCapturingLambda() {
        return arg -> System.out.println(STATIC_TEST_FIELD);
    }

    public static SerializableLambdas.SerializableAction<?> createInstanceCapturingLambda() {
        TestLambdas instance = new TestLambdas();
        return arg -> System.out.println(instance.testField);
    }

    public static SerializableConsumer createSerializableConsumerLambda() {
        return TestLambdas::testMethod;
    }

    public interface SerializableConsumer extends Consumer<Object>, Serializable {}

    private static void testMethod(Object arg) {}
}
