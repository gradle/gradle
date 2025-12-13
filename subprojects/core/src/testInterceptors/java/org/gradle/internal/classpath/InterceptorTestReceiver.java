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

import org.gradle.api.provider.MapProperty;
import org.gradle.util.TestUtil;

public class InterceptorTestReceiver {
    private String testString = "testString";
    public String intercepted = null;
    private boolean testFlag = false;
    private final MapProperty<String, String> richProperty = TestUtil.propertyFactory().mapProperty(String.class, String.class);

    public void test() {}
    public void test(InterceptorTestReceiver arg) {}
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
        return "InterceptorTestReceiver";
    }

    public MapProperty<String, String> getRichProperty() {
        return richProperty;
    }

    /**
     * A controlling object class that has the same richProperty as InterceptorTestReceiver,
     * so we can check that the original property with the same name is not intercepted, but this one is.
     */
    public abstract static class ControllingObject {

        public String intercepted = null;

        public abstract MapProperty<String, String> getRichProperty();
    }
}
