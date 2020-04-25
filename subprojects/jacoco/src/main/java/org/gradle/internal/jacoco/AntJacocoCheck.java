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
import groovy.lang.GroovyObjectSupport;
import org.gradle.api.Action;
import org.gradle.api.file.FileCollection;
import org.gradle.api.internal.project.IsolatedAntBuilder;
import org.gradle.internal.reflect.JavaMethod;
import org.gradle.testing.jacoco.tasks.rules.JacocoLimit;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRule;
import org.gradle.testing.jacoco.tasks.rules.JacocoViolationRulesContainer;

import java.util.HashMap;
import java.util.Hashtable;
import java.util.Map;

import static com.google.common.collect.Iterables.filter;

public class AntJacocoCheck extends AbstractAntJacocoReport<JacocoViolationRulesContainer> {

    private static final String VIOLATIONS_ANT_PROPERTY = "jacocoViolations";
    private static final Predicate<JacocoViolationRule> RULE_ENABLED_PREDICATE = new Predicate<JacocoViolationRule>() {
        @Override
        public boolean apply(JacocoViolationRule rule) {
            return rule.isEnabled();
        }
    };

    public AntJacocoCheck(IsolatedAntBuilder ant) {
        super(ant);
    }

    public JacocoCheckResult execute(FileCollection classpath, final String projectName,
                                     final FileCollection allClassesDirs, final FileCollection allSourcesDirs,
                                     final FileCollection executionData, final JacocoViolationRulesContainer violationRules) {
        final JacocoCheckResult jacocoCheckResult = new JacocoCheckResult();

        configureAntReportTask(classpath, new Action<GroovyObjectSupport>() {
            @Override
            public void execute(GroovyObjectSupport antBuilder) {
                try {
                    invokeJacocoReport(antBuilder, projectName, allClassesDirs, allSourcesDirs, executionData, violationRules);
                } catch (Exception e) {
                    String violations = getViolations(antBuilder);
                    jacocoCheckResult.setSuccess(false);
                    jacocoCheckResult.setFailureMessage(violations != null ? violations : e.getMessage());
                }
            }
        });

        return jacocoCheckResult;
    }

    @Override
    protected void configureReport(final GroovyObjectSupport antBuilder, final JacocoViolationRulesContainer violationRules) {
        if (!violationRules.getRules().isEmpty()) {
            Map<String, Object> checkArgs = ImmutableMap.<String, Object>of("failonviolation", violationRules.isFailOnViolation(), "violationsproperty", VIOLATIONS_ANT_PROPERTY);
            antBuilder.invokeMethod("check", new Object[] {checkArgs, new Closure<Object>(this, this) {
                @SuppressWarnings("UnusedDeclaration")
                public Object doCall(Object ignore) {
                    for (final JacocoViolationRule rule : filter(violationRules.getRules(), RULE_ENABLED_PREDICATE)) {
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
                                return null;
                            }
                        }});
                    }
                    return null;
                }
            }});
        }
    }

    private String getViolations(GroovyObjectSupport antBuilder) {
        Object project = antBuilder.getProperty("project");
        Hashtable<?, ?> properties = JavaMethod.of(project, Hashtable.class, "getProperties").invoke(project, new Object[0]);
        return (String) properties.get(VIOLATIONS_ANT_PROPERTY);
    }
}
