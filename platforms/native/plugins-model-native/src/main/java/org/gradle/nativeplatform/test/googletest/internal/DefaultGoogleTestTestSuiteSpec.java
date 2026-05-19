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
package org.gradle.nativeplatform.test.googletest.internal;

import org.gradle.nativeplatform.internal.AbstractNativeComponentSpec;

@SuppressWarnings("deprecation")
public class DefaultGoogleTestTestSuiteSpec extends AbstractNativeComponentSpec implements org.gradle.nativeplatform.test.googletest.GoogleTestTestSuiteSpec {
    private org.gradle.nativeplatform.NativeComponentSpec testedComponent;

    @Override
    protected String getTypeName() {
        return "Google test suite";
    }

    @Override
    public org.gradle.nativeplatform.NativeComponentSpec getTestedComponent() {
        return testedComponent;
    }

    @Override
    public void setTestedComponent(org.gradle.platform.base.ComponentSpec testedComponent) {
        this.testedComponent = (org.gradle.nativeplatform.NativeComponentSpec) testedComponent;
    }

    @Override
    public void testing(org.gradle.platform.base.ComponentSpec testedComponent) {
        this.testedComponent = (org.gradle.nativeplatform.NativeComponentSpec) testedComponent;
    }
}
