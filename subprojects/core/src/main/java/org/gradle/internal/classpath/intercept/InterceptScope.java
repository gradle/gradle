/*
 * Copyright 2022 the original author or authors.
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

package org.gradle.internal.classpath.intercept;

import java.util.Objects;

/**
 * A scope for the CallInterceptor. It defines what methods/properties or constructors the given
 * CallInterceptor is interested in. Use static methods to obtain instances of this class.
 */
public abstract class InterceptScope {

    private enum CallType {
        METHOD("call method"),
        GET_PROPERTY("get property"),
        SET_PROPERTY("set property"),
        CONSTRUCTOR("call constructor");

        final String descriptorStringPrefix;

        CallType(String descriptorStringPrefix) {
            this.descriptorStringPrefix = descriptorStringPrefix;
        }
    }

    protected final CallType callType;

    private InterceptScope(CallType callType) {
        this.callType = callType;
    }

    /**
     * The returned scope includes calls to all constructors of class {@code constructorClass}.
     *
     * @param constructorClass the class whose constructors are to be intercepted
     * @return the scope object
     */
    public static InterceptScope constructorsOf(Class<?> constructorClass) {
        return new ConstructorScope(constructorClass);
    }

    /**
     * The returned scope includes calls to all methods named {@code methodName}.
     *
     * @param methodName the name of the method to intercept
     * @return the scope object
     */
    public static InterceptScope methodsNamed(String methodName) {
        return new NamedMemberScope(CallType.METHOD, methodName);
    }

    /**
     * The returned scope includes reads of all properties named {@code propertyName}.
     * This scope doesn't include calls to the getter method corresponding to this property,
     * use additional explicit {@link #methodsNamed(String)} scope to intercept that.
     *
     * @param propertyName the name of the property to intercept reads of
     * @return the scope object
     */
    public static InterceptScope readsOfPropertiesNamed(String propertyName) {
        return new NamedMemberScope(CallType.GET_PROPERTY, propertyName);
    }

    /**
     * The returned scope includes writes of all properties named {@code propertyName}.
     * This scope doesn't include calls to the setter method corresponding to this property,
     * use additional explicit {@link #methodsNamed(String)} scope to intercept that.
     *
     * @param propertyName the name of the property to intercept reads of
     * @return the scope object
     */
    public static InterceptScope writesOfPropertiesNamed(String propertyName) {
        return new NamedMemberScope(CallType.SET_PROPERTY, propertyName);
    }

    abstract String getCallSiteName();
    abstract String getTargetDescription();

    @Override
    public String toString() {
        return "InterceptScope{" + callType.descriptorStringPrefix + " " + getTargetDescription() +"}";
    }

    private static class NamedMemberScope extends InterceptScope {
        private final String memberName;

        NamedMemberScope(CallType callType, String memberName) {
            super(callType);
            this.memberName = memberName;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            NamedMemberScope that = (NamedMemberScope) o;
            return callType == that.callType && Objects.equals(memberName, that.memberName);
        }

        @Override
        public int hashCode() {
            return Objects.hash(callType, memberName);
        }

        @Override
        String getCallSiteName() {
            return memberName;
        }

        @Override
        String getTargetDescription() {
            return memberName;
        }
    }

    private static class ConstructorScope extends InterceptScope {
        private final Class<?> constructorClass;

        ConstructorScope(Class<?> constructorClass) {
            super(CallType.CONSTRUCTOR);
            this.constructorClass = constructorClass;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }
            ConstructorScope that = (ConstructorScope) o;
            return constructorClass.equals(that.constructorClass);
        }

        @Override
        public int hashCode() {
            return Objects.hash(constructorClass);
        }

        @Override
        String getCallSiteName() {
            return "<$constructor$>";
        }

        @Override
        String getTargetDescription() {
            return constructorClass.getName();
        }
    }
}
