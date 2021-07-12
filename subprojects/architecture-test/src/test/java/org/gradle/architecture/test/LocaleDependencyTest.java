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

package org.gradle.architecture.test;

import com.tngtech.archunit.core.domain.JavaClass;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.junit.ArchUnitRunner;
import com.tngtech.archunit.lang.ArchCondition;
import com.tngtech.archunit.lang.ArchRule;
import com.tngtech.archunit.lang.ConditionEvents;
import com.tngtech.archunit.lang.SimpleConditionEvent;
import org.junit.runner.RunWith;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

@RunWith(ArchUnitRunner.class)
@AnalyzeClasses(packages = { "org.gradle", "org.gradleinternal" })
public class LocaleDependencyTest {

    @ArchTest
    static final ArchRule toLowerCase_not_called_without_locale = noClasses()
        .should(call("java.lang.String.toLowerCase()", "org.gradle.util.internal.TextUtil.toLowerCaseUserLocale(java.lang.String)"))
        .as("toLowerCase() must be called with a locale parameter or TextUtil.toLowerCaseUserLocale() should be used");

    @ArchTest
    static final ArchRule toUpperCase_not_called_without_locale = noClasses()
        .should(call("java.lang.String.toUpperCase()", "org.gradle.util.internal.TextUtil.toUpperCaseUserLocale(java.lang.String)"))
        .as("toUpperCase() must be called with a locale parameter or TextUtil.toUpperCaseUserLocale() should be used");

    private static ArchCondition<JavaClass> call(String name, String replacement) {
        return new ArchCondition<JavaClass>(name + " must be called with a locale parameter or " + replacement + " should be used") {
            @Override
            public void check(JavaClass javaClass, ConditionEvents events) {
                for (JavaMethodCall call : javaClass.getMethodCallsFromSelf()) {
                    if (name.equals(call.getTarget().getFullName())) {
                        if (!call.getOrigin().getFullName().equals(replacement)) {
                            events.add(new SimpleConditionEvent(call, true, call.getDescription()));
                        }
                    }
                }
            }
        };
    }
}
