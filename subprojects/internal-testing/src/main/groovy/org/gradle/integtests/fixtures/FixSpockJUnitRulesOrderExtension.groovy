/*
 * Copyright 2019 the original author or authors.
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

package org.gradle.integtests.fixtures

import org.spockframework.runtime.AbstractRunListener
import org.spockframework.runtime.extension.AbstractGlobalExtension
import org.spockframework.junit4.TestRuleInterceptor
import org.spockframework.runtime.model.FeatureInfo
import org.spockframework.runtime.model.FieldInfo
import org.spockframework.runtime.model.SpecInfo


/**
 * Workaround for https://github.com/spockframework/spock/issues/1050
 *
 * TODO Remove once Spock is upgraded to a fixed version
 */
class FixSpockJUnitRulesOrderExtension extends AbstractGlobalExtension {
    @Override
    void visitSpec(SpecInfo spec) {
        spec.addListener(new AbstractRunListener() {
            @Override
            void beforeFeature(FeatureInfo feature) {
                feature.iterationInterceptors.findAll { it instanceof TestRuleInterceptor }.each { TestRuleInterceptor interceptor ->
                    interceptor.ruleFields.sort(new Comparator<FieldInfo>() {
                        @Override
                        int compare(FieldInfo o1, FieldInfo o2) {
                            def o1Class = o1.reflection.declaringClass
                            def o2Class = o2.reflection.declaringClass
                            if (o1Class != o2Class) {
                                if (o1Class.isAssignableFrom(o2Class)) {
                                    return 1
                                }
                                if (o2Class.isAssignableFrom(o1Class)) {
                                    return -1
                                }
                            }
                            return 0
                        }
                    })
                }
            }
        })
    }
}
