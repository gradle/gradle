/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.api.internal;

import groovy.lang.GroovyClassLoader;
import groovy.lang.MetaBeanProperty;
import groovy.lang.MetaMethod;

import java.lang.reflect.Constructor;
import java.util.Formatter;

public class GroovySourceGenerationBackedClassGenerator extends AbstractClassGenerator {

    protected <T> ClassBuilder<T> start(Class<T> type) {
        return new ClassBuilderImpl<T>(type);
    }

    private static class ClassBuilderImpl<T> implements ClassBuilder<T> {
        private final Formatter src;
        private final Class<T> type;
        private final String className;
        private boolean dynamicAware;

        private ClassBuilderImpl(Class<T> type) {
            className = type.getSimpleName() + "_Generated";
            src = new Formatter();
            this.type = type;
        }

        public void startClass(boolean isConventionAware, boolean isDynamicAware) {
            dynamicAware = isDynamicAware;
            if (type.getPackage() != null) {
                src.format("package %s;%n", type.getPackage().getName());
            }
            src.format("public class %s extends %s ", className, type.getName().replaceAll("\\$", "."));
            if (isConventionAware) {
                src.format("implements org.gradle.api.internal.IConventionAware ");
            }
            if (isDynamicAware) {
                src.format(isConventionAware ? ", " : "implements ");
                src.format("org.gradle.api.internal.DynamicObjectAware ");
            }
            src.format("{%n");
        }

        public void addConstructor(Constructor<?> constructor) {
            src.format("public %s(", className);
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                Class<?> paramType = constructor.getParameterTypes()[i];
                if (i > 0) {
                    src.format(",");
                }
                src.format("%s p%d", paramType.getCanonicalName(), i);
            }
            src.format(") { super(");
            for (int i = 0; i < constructor.getParameterTypes().length; i++) {
                if (i > 0) {
                    src.format(",");
                }
                src.format("p%d", i);
            }
            src.format("); }%n");
        }

        public void mixInDynamicAware() {
            src.format("private org.gradle.api.internal.DynamicObjectHelper dynamicObject = new org.gradle.api.internal.DynamicObjectHelper(this, new org.gradle.api.internal.plugins.DefaultConvention())%n");
            src.format("public void setConvention(org.gradle.api.plugins.Convention convention) { dynamicObject.setConvention(convention); getConventionMapping().setConvention(convention) }%n");
            src.format("public org.gradle.api.plugins.Convention getConvention() { return dynamicObject.getConvention() }%n");
            src.format("public org.gradle.api.internal.DynamicObject getAsDynamicObject() { return dynamicObject }%n");
        }

        public void mixInConventionAware() {
            if (dynamicAware) {
                src.format("private org.gradle.api.internal.ConventionMapping mapping = new org.gradle.api.internal.ConventionAwareHelper(this, getConvention())%n");
            } else {
                src.format("private org.gradle.api.internal.ConventionMapping mapping = new org.gradle.api.internal.ConventionAwareHelper(this, new org.gradle.api.internal.plugins.DefaultConvention())%n");
            }
            src.format("public void setConventionMapping(org.gradle.api.internal.ConventionMapping conventionMapping) { this.mapping = conventionMapping }%n");
            src.format("public org.gradle.api.internal.ConventionMapping getConventionMapping() { return mapping }%n");
        }

        public void mixInGroovyObject() {
        }

        public void addDynamicMethods() {
            src.format("def hasProperty(String name) { getAsDynamicObject().hasProperty(name); }%n");
            src.format("void setProperty(String name, Object value) { getAsDynamicObject().setProperty(name, value); }%n");
            src.format("def propertyMissing(String name) { getAsDynamicObject().getProperty(name); }%n");
            src.format("def methodMissing(String name, Object params) { getAsDynamicObject().invokeMethod(name, (Object[])params); }%n");
        }

        public void addGetter(MetaBeanProperty property) {
            MetaMethod getter = property.getGetter();
            String returnTypeName = getter.getReturnType().getCanonicalName();
            src.format("private boolean %sSet;%n", property.getName());
            src.format("public %s %s() { getConventionMapping().getConventionValue(super.%s(), '%s', %sSet); }%n",
                    returnTypeName, getter.getName(), getter.getName(), property.getName(), property.getName());
        }

        public void addSetter(MetaBeanProperty property) throws Exception {
            MetaMethod setter = property.getSetter();
            if (setter.getReturnType().equals(Void.TYPE)) {
                src.format("public void %s(%s v) { super.%s(v); %sSet = true; }%n", setter.getName(),
                        setter.getParameterTypes()[0].getTheClass().getCanonicalName(), setter.getName(),
                        property.getName());
            } else {
                String returnTypeName = setter.getReturnType().getCanonicalName();
                src.format("public %s %s(%s v) { %s r = super.%s(v); %sSet = true; return r; }%n", returnTypeName,
                        setter.getName(), setter.getParameterTypes()[0].getTheClass().getCanonicalName(),
                        returnTypeName, setter.getName(), property.getName());
            }
        }

        public Class<? extends T> generate() {
            src.format("}");

            GroovyClassLoader classLoader = new GroovyClassLoader(type.getClassLoader());
            return classLoader.parseClass(src.toString());
        }
    }
}
