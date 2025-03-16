/*
 * Copyright 2023 the original author or authors.
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

package org.gradle.internal.classpath;

/**
 * Class with the same methods as {@link InterceptorTestReceiver} but intercepted via
 * a different class {@link CompositeCallInterceptionTestInterceptorsDeclaration}.
 */
public class CompositeInterceptorTestReceiver {
    public String intercepted = null;
    private String testString = "testString";
    private boolean testFlag = false;

    public void test() {}
    public void test(CompositeInterceptorTestReceiver arg) {}
    public void testVararg(Object... arg) {}

    public void callNonIntercepted() {
        intercepted = "callNotIntercepted()-not-intercepted";
    }

    public String getTestString() {
        return testString;
    }

    public void setTestString(String newValue) {
        testString = newValue;
    }

    public boolean isTestFlag() {
        return testFlag;
    }

    public void setTestFlag(boolean newValue) {
        testFlag = newValue;
    }

    @Override
    public String toString() {
        return "CompositeInterceptorTestReceiver";
    }
}
