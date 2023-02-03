/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.api.internal.artifacts;

import org.gradle.api.artifacts.ExcludeRule;

public class DefaultExcludeRule implements ExcludeRule {
    private String group;
    private String module;

    public DefaultExcludeRule(){
    }

    public DefaultExcludeRule(String group, String module) {
        this.group = group;
        this.module = module;
    }

    @Override
    public String getGroup() {
        return group;
    }

    public void setGroup(String groupValue) {
        this.group = groupValue;
    }

    @Override
    public String getModule() {
        return module;
    }

    public void setModule(String moduleValue) {
        this.module = moduleValue;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }

        DefaultExcludeRule that = (DefaultExcludeRule) o;

        if (group != null ? !group.equals(that.group) : that.group != null) {
            return false;
        }
        if (module != null ? !module.equals(that.module) : that.module != null) {
            return false;
        }
        return true;
    }

    @Override
    public int hashCode() {
        int result = group != null ? group.hashCode() : 0;
        result = 31 * result + (module != null ? module.hashCode() : 0);
        return result;
    }
}
