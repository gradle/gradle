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
package org.gradle.plugins.ear.descriptor;

/**
 * A security-role element in a deployment descriptor like application.xml.
 */
public interface EarSecurityRole {

    /**
     * A description of the security role. Optional.
     */
    public String getDescription();

    public void setDescription(String description);

    /**
     * The name of the security role. Required.
     */
    public String getRoleName();

    public void setRoleName(String roleName);

}