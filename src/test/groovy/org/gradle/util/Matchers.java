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
package org.gradle.util;

import org.hamcrest.BaseMatcher;
import org.hamcrest.Description;
import org.hamcrest.Factory;
import org.hamcrest.Matcher;
import org.gradle.api.dependencies.ResolveInstructionModifier;
import org.gradle.api.dependencies.ResolveInstruction;
import org.gradle.api.filter.FilterSpec;

import java.util.regex.Pattern;

import groovy.lang.Closure;

public class Matchers {
    @Factory
    public static <T> Matcher<T> reflectionEquals(T equalsTo) {
        return new ReflectionEqualsMatcher<T>(equalsTo);
    }

    @Factory
    public static Matcher<String> containsLine(final String line) {
        return new BaseMatcher<String>() {
            public boolean matches(Object o) {
                return Pattern.compile(String.format("^%s$", Pattern.quote(line)), Pattern.MULTILINE).matcher(o.toString()).find();
            }

            public void describeTo(Description description) {
                description.appendText("contains line ").appendValue(line);
            }
        };
    }

    @Factory
    public static Matcher<ResolveInstructionModifier> modifierMatcher(final FilterSpec<ResolveInstruction> acceptanceFilter) {
        return new BaseMatcher<ResolveInstructionModifier>() {
            public void describeTo(Description description) {
                description.appendText("matching resolve instruction modifier");
            }

            public boolean matches(Object actual) {
                ResolveInstructionModifier modifier = (ResolveInstructionModifier) actual;
                return acceptanceFilter.isSatisfiedBy(modifier.modify(new ResolveInstruction()));
            }
        };
    }

    @Factory
    public static Matcher<Closure> modifierClosureMatcher(final FilterSpec<ResolveInstruction> acceptanceFilter) {
        return new BaseMatcher<Closure>() {
            public void describeTo(Description description) {
                description.appendText("matching closure modifier");
            }

            public boolean matches(Object actual) {
                Closure modifier = (Closure) actual;
                return acceptanceFilter.isSatisfiedBy((ResolveInstruction) ConfigureUtil.configure(modifier, new ResolveInstruction()));
            }
        };
    }
}
