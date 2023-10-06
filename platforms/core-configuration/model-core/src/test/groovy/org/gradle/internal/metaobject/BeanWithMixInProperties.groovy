/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.internal.metaobject

class BeanWithMixInProperties implements PropertyMixIn {
    String prop

    @Override
    PropertyAccess getAdditionalProperties() {
        return new PropertyAccess() {
            @Override
            boolean hasProperty(String name) {
                return name == "dyno"
            }

            @Override
            DynamicInvokeResult tryGetProperty(String name) {
                if (name == "dyno") {
                    return DynamicInvokeResult.found("ok")
                }
                return DynamicInvokeResult.notFound();
            }

            @Override
            DynamicInvokeResult trySetProperty(String name, Object value) {
                if (name == "dyno") {
                    return DynamicInvokeResult.found();
                }
                return DynamicInvokeResult.notFound();
            }

            @Override
            Map<String, ?> getProperties() {
                return [dyno: "ok"]
            }
        }
    }

    Object propertyMissing(String name) {
        throw new AssertionError("should not be called")
    }

    void propertyMissing(String name, Object value) {
        throw new AssertionError("should not be called")
    }
}
