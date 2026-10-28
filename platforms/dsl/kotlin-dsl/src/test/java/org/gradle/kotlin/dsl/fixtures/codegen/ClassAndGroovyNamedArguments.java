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

package org.gradle.kotlin.dsl.fixtures.codegen;

import java.util.Map;
import java.util.function.Consumer;


public interface ClassAndGroovyNamedArguments {

    <T> void mapAndClass(Map<String, ?> args, Class<? extends T> type);

    <T> void mapAndClassAndVarargs(Map<String, ?> args, Class<? extends T> type, String... options);

    <T> void mapAndClassAndSAM(Map<String, ?> args, Class<? extends T> type, Consumer<? super T> action);
}
