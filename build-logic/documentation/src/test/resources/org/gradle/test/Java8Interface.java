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

package org.gradle.test;

import java.io.IOException;
import java.util.function.Supplier;

/**
 * An interface that uses Java 8 source features.
 */
public interface Java8Interface extends CombinedInterface, JavaInterface {
    default String getName() {
        try (Writer writer = new StringWriter()) {
            Supplier<String> methodReference = this::toString;
            Supplier<String> lambda = () -> this.toString();
        } catch (IOException ignore) {
        }
        return "foo";
    }
}
