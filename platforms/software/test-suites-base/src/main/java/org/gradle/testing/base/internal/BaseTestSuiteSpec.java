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

package org.gradle.testing.base.internal;

import org.gradle.platform.base.ComponentSpec;
import org.gradle.platform.base.component.BaseComponentSpec;
import org.gradle.testing.base.TestSuiteSpec;

public class BaseTestSuiteSpec extends BaseComponentSpec implements TestSuiteSpec {
    private ComponentSpec testedComponent;

    @Override
    public ComponentSpec getTestedComponent() {
        return testedComponent;
    }

    @Override
    public void setTestedComponent(ComponentSpec testedComponent) {
        this.testedComponent = testedComponent;
    }

    @Override
    public void testing(ComponentSpec testedComponent) {
        this.testedComponent = testedComponent;
    }

}
