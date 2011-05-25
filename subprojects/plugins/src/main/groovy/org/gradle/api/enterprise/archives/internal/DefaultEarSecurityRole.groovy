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
package org.gradle.api.enterprise.archives.internal

import org.gradle.api.enterprise.archives.EarSecurityRole;

/**
 * @author David Gileadi
 */
class DefaultEarSecurityRole implements EarSecurityRole {

    String description
    String roleName

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
    public int hashCode() {

        final int prime = 31;
        int result = 1;
        result = prime * result + ((roleName == null) ? 0 : roleName.hashCode());
        return result;
    }

    @Override
    public boolean equals(Object obj) {

        if (this == obj) {
            return true;
        }
        if (obj == null) {
            return false;
        }
        if (getClass() != obj.getClass()) {
            return false;
        }
        DefaultEarSecurityRole other = (DefaultEarSecurityRole) obj;
        if (roleName == null) {
            if (other.roleName != null) {
                return false;
            }
        } else if (!roleName.equals(other.roleName)) {
            return false;
        }
        return true;
    }
}
