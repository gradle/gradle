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

package org.gradle.api.internal.initialization;

import org.gradle.api.internal.initialization.loadercache.ClassLoaderId;

import java.util.Arrays;

/**
 * Provides implementations of classloader ids
 */
public class ClassLoaderIds {

    public enum Type {
        SCRIPT,
        TEST_TASK_CLASSPATH
    }

    private static ClassLoaderId of(Type type, String... attributes) {
        return new DefaultClassLoaderId(type, attributes);
    }

    public static ClassLoaderId buildScript(String fileName, String operationId) {
        return of(Type.SCRIPT, fileName, operationId);
    }

    public static ClassLoaderId testTaskClasspath(String testTaskPath) {
        return of(Type.TEST_TASK_CLASSPATH, testTaskPath);
    }

    private static class DefaultClassLoaderId implements ClassLoaderId {
        private final Type type;
        private final String[] attributes;

        public DefaultClassLoaderId(Type type, String[] attributes) {
            this.type = type;
            this.attributes = attributes;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) {
                return true;
            }
            if (o == null || getClass() != o.getClass()) {
                return false;
            }

            DefaultClassLoaderId that = (DefaultClassLoaderId) o;

            return type == that.type && Arrays.equals(attributes, that.attributes);
        }

        @Override
        public int hashCode() {
            int result = type.hashCode();
            result = 31 * result + Arrays.hashCode(attributes);
            return result;
        }

        @Override
        public String getDisplayName() {
            return type + "[" + Arrays.toString(attributes) + "]";
        }

        @Override
        public String toString() {
            return "ClassLoaderId{type=" + type + ", attributes=" + Arrays.toString(attributes) + '}';
        }
    }
}
