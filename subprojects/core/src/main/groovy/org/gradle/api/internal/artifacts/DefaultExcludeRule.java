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

import org.gradle.api.InvalidUserDataException;
import org.gradle.api.artifacts.ExcludeRule;
import org.gradle.util.DeprecationLogger;

import java.util.HashMap;
import java.util.Map;

/**
 * @author Hans Dockter
 *
 *         DefaultExcludeRule is a value object
 */
public class DefaultExcludeRule implements ExcludeRule {
    private Map<String, String> excludeArgs;

    private String group;
    private String module;

    public DefaultExcludeRule(Map<String, String> excludeArgs) {
        DeprecationLogger.nagUserOfReplacedMethod("DefaultExcludeRule(Map<String, String>)", "DefaultExcludeRule(String,String)");
        this.excludeArgs = excludeArgs;
        this.group = excludeArgs.get(GROUP_KEY);
        this.module = excludeArgs.get(MODULE_KEY);
    }

    public DefaultExcludeRule(String group, String module) {
        if (group == null && module==null) {
            throw new InvalidUserDataException("Name or Module must not be null!");
        }
        this.excludeArgs = new HashMap<String, String>();
        setGroup(group);
        setModule(module);
    }

    public String getGroup() {
        return group;
    }

    public void setGroup(String groupValue) {
        this.group = groupValue;
        if(groupValue!=null) this.excludeArgs.put(GROUP_KEY, groupValue);

    }

    public String getModule() {
        return module;
    }

    public void setModule(String moduleValue) {
        this.module = moduleValue;
        if(moduleValue!=null) this.excludeArgs.put(MODULE_KEY, module);
    }

    public Map<String, String> getExcludeArgs() {
        DeprecationLogger.nagUserWith("The getExcludeArgs method has been deprecated and will be removed in the next version of Gradle. Please use the getGroup() method or the getModule() method instead.");
        return excludeArgs;
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

        if (excludeArgs != null ? !excludeArgs.equals(that.excludeArgs) : that.excludeArgs != null) {
            return false;
        }

        return true;
    }

    @Override
    public int hashCode() {
        return excludeArgs != null ? excludeArgs.hashCode() : 0;
    }
}
