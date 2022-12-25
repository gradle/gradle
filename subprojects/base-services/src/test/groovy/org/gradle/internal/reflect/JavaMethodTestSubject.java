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

package org.gradle.internal.reflect;

@SuppressWarnings("UnusedDeclaration")
public class JavaMethodTestSubject implements JavaMethodTestSubjectInterface {
    private static String myStaticProperty;

    final IllegalStateException failure = new IllegalStateException();
    private String myProp = "myValue";

    public static String getStaticProperty() {
        return myStaticProperty;
    }

    public static void setStaticProperty(String value) {
        myStaticProperty = value;
    }

    public String doSomeStuff(int a, Integer b) {
        return String.format("%s.%s", a, b);
    }

    public String getMyProperty() {
        return myProp;
    }

    public void setMyProperty(String value) {
        myProp = value;
    }

    public void setMyProperty(Object value) {
        myProp = value.toString();
    }

    public void throwsException() {
        throw failure;
    }

    static class TestCheckedException extends Exception {
        public TestCheckedException(Throwable cause) {
            super(cause);
        }
    }

    public void throwsCheckedException() throws TestCheckedException {
        throw new TestCheckedException(failure);
    }

    protected String protectedMethod() {
        return "parent";
    }

    protected String overridden() {
        return "parent";
    }

}
