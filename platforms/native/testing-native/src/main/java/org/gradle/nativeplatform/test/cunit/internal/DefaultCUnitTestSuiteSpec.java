/*
 * Copyright 2014 the original author or authors.
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
package org.gradle.nativeplatform.test.cunit.internal;

import org.gradle.nativeplatform.NativeComponentSpec;
import org.gradle.nativeplatform.internal.AbstractNativeComponentSpec;
import org.gradle.nativeplatform.test.cunit.CUnitTestSuiteSpec;
import org.gradle.platform.base.ComponentSpec;

public class DefaultCUnitTestSuiteSpec extends AbstractNativeComponentSpec implements CUnitTestSuiteSpec {
    private NativeComponentSpec testedComponent;

    @Override
    protected String getTypeName() {
        return "Cunit test suite";
    }

    @Override
    public NativeComponentSpec getTestedComponent() {
        return testedComponent;
    }

    @Override
    public void setTestedComponent(ComponentSpec testedComponent) {
        this.testedComponent = (NativeComponentSpec) testedComponent;
    }

    @Override
    public void testing(ComponentSpec testedComponent) {
        this.testedComponent = (NativeComponentSpec) testedComponent;
    }

}
