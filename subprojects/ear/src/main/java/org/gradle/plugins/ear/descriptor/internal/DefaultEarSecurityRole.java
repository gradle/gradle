/*
 * Copyright 2011 the original author or authors.
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
package org.gradle.plugins.ear.descriptor.internal;

import com.google.common.base.Objects;
import org.gradle.plugins.ear.descriptor.EarSecurityRole;

import javax.inject.Inject;

public class DefaultEarSecurityRole implements EarSecurityRole {

    private String description;
    private String roleName;

    @Inject
    public DefaultEarSecurityRole() {
    }

    public DefaultEarSecurityRole(String roleName) {
        this.roleName = roleName;
    }

    public DefaultEarSecurityRole(String roleName, String description) {
        this.roleName = roleName;
        this.description = description;
    }

    @Override
    public String getDescription() {
        return description;
    }

    @Override
    public void setDescription(String description) {
        this.description = description;
    }

    @Override
    public String getRoleName() {
        return roleName;
    }

    @Override
    public void setRoleName(String roleName) {
        this.roleName = roleName;
    }

    @Override
    public int hashCode() {
        int result;
        result = description != null ? description.hashCode() : 0;
        result = 31 * result + (roleName != null ? roleName.hashCode() : 0);
        return result;
    }

    @Override
    public boolean equals(Object o) {
        if (this==o) {
            return true;
        }
        if (!(o instanceof DefaultEarSecurityRole)) {
            return false;
        }
        DefaultEarSecurityRole that = (DefaultEarSecurityRole) o;
        return Objects.equal(description, that.description) && Objects.equal(roleName, that.roleName);
    }
}
