/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.integtests.fixtures.polyglot;

import org.apache.commons.lang3.stream.Streams;
import org.gradle.integtests.fixtures.extensions.AbstractMultiTestInterceptor;
import org.gradle.test.fixtures.dsl.GradleDsl;
import org.spockframework.runtime.extension.IMethodInvocation;

import javax.annotation.Nonnull;
import java.lang.annotation.Annotation;
import java.util.Arrays;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

public class PolyglotDslTestInterceptor extends AbstractMultiTestInterceptor {
    private final static String DSL_LANGUAGE = "org.gradle.internal.runner.dsl";

    private final Set<GradleDsl> classSpecificSkippedDSLs;

    public PolyglotDslTestInterceptor(Class<?> target) {
        super(target);
        classSpecificSkippedDSLs = skippedDSLs(target.getAnnotations());
    }

    public static GradleDsl getCurrentDsl() {
        String property = System.getProperty(DSL_LANGUAGE);
        Objects.requireNonNull(property, "Should be used from tests marked with @" + PolyglotDslTest.class.getSimpleName());
        return GradleDsl.valueOf(property);
    }

    @Override
    protected void createExecutions() {
        for (GradleDsl dsl : GradleDsl.values()) {
            if (!classSpecificSkippedDSLs.contains(dsl)) {
                add(new DslExecution(dsl));
            }
        }
    }

    private static class DslExecution extends Execution {
        private final GradleDsl dsl;

        private DslExecution(GradleDsl dsl) {
            this.dsl = dsl;
        }

        @Override
        protected String getDisplayName() {
            return dsl.getLanguageCodeName();
        }

        @Override
        public String toString() {
            return getDisplayName();
        }

        @Override
        protected void before(IMethodInvocation invocation) {
            System.setProperty(DSL_LANGUAGE, dsl.toString());
        }

        @Override
        protected void after() {
            System.clearProperty(DSL_LANGUAGE);
        }

        @Override
        public boolean isTestEnabled(TestDetails testDetails) {
            Set<GradleDsl> methodSpecificSkippedDSLs = skippedDSLs(testDetails.getAnnotations());
            return !methodSpecificSkippedDSLs.contains(dsl);
        }
    }

    @Nonnull
    private static Set<GradleDsl> skippedDSLs(Annotation[] annotations) {
        return Arrays.stream(annotations)
            .flatMap((Function<Annotation, Stream<SkipDsl>>) a -> {
                if (a instanceof SkipDsl) {
                    return Streams.of((SkipDsl) a);
                } else if (a instanceof SkipDsl.List) {
                    SkipDsl.List al = (SkipDsl.List) a;
                    return Arrays.stream(al.value());
                } else {
                    return null;
                }
            })
            .map(SkipDsl::dsl)
            .collect(Collectors.toSet());
    }
}
