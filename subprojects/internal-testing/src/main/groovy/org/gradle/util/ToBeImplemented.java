/*
 * Copyright 2017 the original author or authors.
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
package org.gradle.util;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Indicates a test that is explicitly testing for some failure that is to be fixed later.
 *
 * <p>This annotation replaces {@literal @}{@link groovy.transform.NotYetImplemented}.
 * The problem with {@code NotYetImplemented} is that it succeeds no matter what causes the marked test
 * to fail. Tests like that can pass because the expected failure is still present, or even if the
 * expected failure is replaced by some other failure. It's better to write a test that explicitly
 * tests for the expected failure, so when it fails for some other reason, it becomes noticeable.
 * The purpose of this annotation is to keep such tests easy to find in the code.</p>
 */
@Retention(RetentionPolicy.SOURCE)
@Target({ElementType.METHOD, ElementType.TYPE})
public @interface ToBeImplemented {
    String value() default "";
}
