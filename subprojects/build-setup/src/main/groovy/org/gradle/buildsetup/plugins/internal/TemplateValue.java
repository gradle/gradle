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

package org.gradle.buildsetup.plugins.internal;

public class TemplateValue {
    private final String value;

    public TemplateValue(String value) {
        this.value = value;
    }

    public String getGroovyComment() {
        return value.replace("\\", "\\\\");
    }

    public String getGroovyString() {
        return value.replace("\\", "\\\\").replace("'", "\\'");
    }

    @Override
    public String toString() {
        return String.format(">>>%s<<<", value);
    }
}
