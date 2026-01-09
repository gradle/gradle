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
import org.gradle.api.GradleException;
import org.gradle.api.internal.project.antbuilder.AntBuilderDelegate;
import org.gradle.api.plugins.internal.ant.AntWorkAction;
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

public abstract class AntJacocoCheck extends AntWorkAction<JacocoCoverageParameters> {
    private static final String VIOLATIONS_ANT_PROPERTY = "jacocoViolations";
    private static final Predicate<JacocoViolationRule> RULE_ENABLED_PREDICATE = new Predicate<JacocoViolationRule>() {
        @Override
        public boolean apply(JacocoViolationRule rule) {
            return rule.isEnabled();
        }
    };

    @Override
    protected String getActionName() {
        return "jacoco-coverage";
    }

    @Override
    public void execute(AntBuilderDelegate antBuilder) {
        JacocoCoverageParameters params = getParameters();
        antBuilder.taskdef("jacocoReport", "org.jacoco.ant.ReportTask");
        try {
            antBuilder.createNode("jacocoReport", Collections.emptyMap(), () -> {
                antBuilder.createNode("executiondata", Collections.emptyMap(), () -> {
                    antBuilder.addFiles("resources", params.getExecutionData().filter(File::exists));
                });

                Map<String, Object> structureArgs = ImmutableMap.of("name", params.getProjectName().get());
                antBuilder.createNode("structure", structureArgs, () -> {
                    antBuilder.createNode("classfiles", Collections.emptyMap(), () -> {
                        antBuilder.addFiles("resources", params.getAllClassesDirs().filter(File::exists));
                    });
                    final Map<String, Object> sourcefilesArgs;
                    String encoding = params.getEncoding().getOrNull();
                    if (encoding == null) {
                        sourcefilesArgs = Collections.emptyMap();
                    } else {
                        sourcefilesArgs = Collections.singletonMap("encoding", encoding);
                    }
                    antBuilder.createNode("sourcefiles", sourcefilesArgs, () -> {
                        antBuilder.addFiles("resources", params.getAllSourcesDirs().filter(File::exists));
                    });
                });

                Set<JacocoViolationRule> rules = filter(params.getRules().get(), RULE_ENABLED_PREDICATE);
                if (!rules.isEmpty()) {
                    Map<String, Object> checkArgs = ImmutableMap.of(
                        "failonviolation", params.getFailOnViolation().get(),
                        "violationsproperty", VIOLATIONS_ANT_PROPERTY);

                    antBuilder.createNode("check", checkArgs, () -> {
                        for (final JacocoViolationRule rule : rules) {
                            Map<String, Object> ruleArgs = ImmutableMap.of(
                                "element", rule.getElement(),
                                "includes", Joiner.on(':').join(rule.getIncludes()),
                                "excludes", Joiner.on(':').join(rule.getExcludes())
                            );
                            antBuilder.createNode("rule", ruleArgs, () -> {
                                for (JacocoLimit limit : rule.getLimits()) {
                                    Map<String, Object> limitArgs = new HashMap<>();
                                    limitArgs.put("counter", limit.getCounter());
                                    limitArgs.put("value", limit.getValue());

                                    if (limit.getMinimum() != null) {
                                        limitArgs.put("minimum", limit.getMinimum());
                                    }
                                    if (limit.getMaximum() != null) {
                                        limitArgs.put("maximum", limit.getMaximum());
                                    }

                                    antBuilder.createNode("limit", limitArgs);
                                }
                            });
                        }
                    });
                }
            });
        } catch (Exception e) {
            String violations = getViolations(antBuilder);
            throw new GradleException(violations == null ? e.getMessage() : violations, e);
        }

        GFileUtils.touch(params.getDummyOutputFile().get().getAsFile());
    }

    @Nullable
    private String getViolations(AntBuilderDelegate antBuilder) {
        return Cast.uncheckedCast(antBuilder.getProjectProperties().get(VIOLATIONS_ANT_PROPERTY));
    }

}
