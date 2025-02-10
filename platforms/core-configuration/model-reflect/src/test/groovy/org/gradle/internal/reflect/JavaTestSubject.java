/*
 * Copyright 2013 the original author or authors.
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

import java.io.File;
import java.util.Collection;
import java.util.Set;

@SuppressWarnings("UnusedDeclaration")
public class JavaTestSubject {

    final IllegalStateException failure = new IllegalStateException();
    private String myProp = "myValue";
    private String myProp2;
    private boolean myBooleanProp = true;
    public String myField = "myFieldValue";
    public boolean myBooleanField = true;
    private static String myStaticProperty;

    public int publicField;

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

    public boolean isMyBooleanProperty() {
        return myBooleanProp;
    }

    public void setMyBooleanProperty(boolean value) {
        myBooleanProp = value;
    }

    public boolean getMyOtherBooleanProperty() {
        return true;
    }

    public String isNotABooleanProperty() {
        return null;
    }

    public static String getStaticProperty() {
        return myStaticProperty;
    }

    public static void setStaticProperty(String value) {
        myStaticProperty = value;
    }

    public void getVoidProperty() {
    }

    public String getParamProperty(String param) {
        return null;
    }

    public void setParamProperty() {
    }

    public void setParamProperty(String param, String someOther) {
    }

    public void setWriteOnly(String param) {
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

    protected String getProtectedProperty() {
        return null;
    }

    protected void setProtectedProperty(String value) {
    }

    private String getPrivateProperty() {
        return null;
    }

    private void setPrivateProperty(String value) {
    }

    public void setMultiValue(Set<String> values) { }

    public void setMultiValue(Iterable<?> values) { }

    public void setMyProperty2(String value) {
        myProp2 = value;
    }

    public void setMyProperty2(File value) {
        myProp2 = value.toString();
    }

    public String getMyProperty2() {
        return myProp2;
    }

    public void setMyProperty3(CharSequence value) {}
    public void setMyProperty3(Collection<?> value) {}
    public void setMyProperty3(Object value) {}
}
