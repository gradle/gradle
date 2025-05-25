/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.internal.jacoco;

import com.google.common.base.Joiner;
import com.google.common.base.Predicate;
import com.google.common.collect.ImmutableMap;
import groovy.lang.Closure;
import org.gradle.api.Action;
import org.gradle.api.GradleException;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.internal.Cast;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.util.internal.GFileUtils;
import org.jspecify.annotations.Nullable;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import static com.google.common.collect.Sets.filter;

public class AntJacocoCheck implements Action<AntBuilderDelegate> {
    private static final String VIOLATIONS_ANT_PROPERTY = "jacocoViolations";
    private static final Predicate<JacocoViolationRule> RULE_ENABLED_PREDICATE = new Predicate<JacocoViolationRule>() {
        @Override
        public boolean apply(JacocoViolationRule rule) {
            return rule.isEnabled();
        }
    };

    private final JacocoCoverageParameters params;

    public AntJacocoCheck(JacocoCoverageParameters params) {
        this.params = params;
    }

    @Override
    public void execute(AntBuilderDelegate antBuilder) {
        antBuilder.invokeMethod("taskdef", ImmutableMap.of(
            "name", "jacocoReport",
            "classname", "org.jacoco.ant.ReportTask"
        ));
        final Map<String, Object> emptyArgs = Collections.emptyMap();
        try {
            antBuilder.invokeMethod("jacocoReport", new Object[]{Collections.emptyMap(), new Closure<Object>(this, this) {
                @SuppressWarnings("UnusedDeclaration")
                public Object doCall(Object ignore) {
                    antBuilder.invokeMethod("executiondata", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                        public Object doCall(Object ignore) {
                            params.getExecutionData().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                            return Void.class;
                        }
                    }});
                    Map<String, Object> structureArgs = ImmutableMap.<String, Object>of("name", params.getProjectName().get());
                    antBuilder.invokeMethod("structure", new Object[]{structureArgs, new Closure<Object>(this, this) {
                        public Object doCall(Object ignore) {
                            antBuilder.invokeMethod("classfiles", new Object[]{emptyArgs, new Closure<Object>(this, this) {
                                public Object doCall(Object ignore) {
                                    params.getAllClassesDirs().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                                    return Void.class;
                                }
                            }});
                            final Map<String, Object> sourcefilesArgs;
                            String encoding = params.getEncoding().getOrNull();
                            if (encoding == null) {
                                sourcefilesArgs = emptyArgs;
                            } else {
                                sourcefilesArgs = Collections.singletonMap("encoding", encoding);
                            }
                            antBuilder.invokeMethod("sourcefiles", new Object[]{sourcefilesArgs, new Closure<Object>(this, this) {
                                public Object doCall(Object ignore) {
                                    params.getAllSourcesDirs().filter(File::exists).addToAntBuilder(antBuilder, "resources");
                                    return Void.class;
                                }
                            }});
                            return Void.class;
                        }
                    }});

                    Set<JacocoViolationRule> rules = filter(params.getRules().get(), RULE_ENABLED_PREDICATE);
                    if (!rules.isEmpty()) {
                        Map<String, Object> checkArgs = ImmutableMap.<String, Object>of(
                            "failonviolation", params.getFailOnViolation().get(),
                            "violationsproperty", VIOLATIONS_ANT_PROPERTY);

                        antBuilder.invokeMethod("check", new Object[] {checkArgs, new Closure<Object>(this, this) {
                            @SuppressWarnings("UnusedDeclaration")
                            public Object doCall(Object ignore) {
                                for (final JacocoViolationRule rule : rules) {
                                    Map<String, Object> ruleArgs = ImmutableMap.<String, Object>of("element", rule.getElement(), "includes", Joiner.on(':').join(rule.getIncludes()), "excludes", Joiner.on(':').join(rule.getExcludes()));
                                    antBuilder.invokeMethod("rule", new Object[] {ruleArgs, new Closure<Object>(this, this) {
                                        @SuppressWarnings("UnusedDeclaration")
                                        public Object doCall(Object ignore) {
                                            for (JacocoLimit limit : rule.getLimits()) {
                                                Map<String, Object> limitArgs = new HashMap<String, Object>();
                                                limitArgs.put("counter", limit.getCounter());
                                                limitArgs.put("value", limit.getValue());

                                                if (limit.getMinimum() != null) {
                                                    limitArgs.put("minimum", limit.getMinimum());
                                                }
                                                if (limit.getMaximum() != null) {
                                                    limitArgs.put("maximum", limit.getMaximum());
                                                }

                                                antBuilder.invokeMethod("limit", new Object[] {ImmutableMap.copyOf(limitArgs) });
                                            }
                                            return Void.class;
                                        }
                                    }});
                                }
                                return Void.class;
                            }
                        }});
                    }

                    return Void.class;
                }
            }});
        } catch (Exception e) {
            String violations = getViolations(antBuilder);
            throw new GradleException(violations == null ? e.getMessage() : violations);
        }

        GFileUtils.touch(params.getDummyOutputFile().get().getAsFile());
    }

    @Nullable
    private String getViolations(AntBuilderDelegate antBuilder) {
        return Cast.uncheckedCast(antBuilder.getProjectProperties().get(VIOLATIONS_ANT_PROPERTY));
    }
}
