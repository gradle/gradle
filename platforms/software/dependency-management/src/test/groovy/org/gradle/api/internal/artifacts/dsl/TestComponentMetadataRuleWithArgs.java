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

package org.gradle.api.internal.artifacts.dsl;

import org.gradle.api.artifacts.ComponentMetadataContext;
import org.gradle.api.artifacts.ComponentMetadataRule;

public class TestComponentMetadataRuleWithArgs implements ComponentMetadataRule {

    public static int instanceCount = 0;
    public static Object[] constructorParams;

    public TestComponentMetadataRuleWithArgs(String arg1, long arg2) {
        instanceCount++;
        constructorParams = new Object[]{arg1, arg2};
    }

    @Override
    public void execute(ComponentMetadataContext componentMetadataContext) {
        // Do nothing for now
    }
}
