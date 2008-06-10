/*
 * Copyright 2007-2008 the original author or authors.
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
 
package org.gradle

import org.apache.commons.lang.builder.EqualsBuilder
import org.apache.commons.lang.builder.HashCodeBuilder

/**
 * @author Hans Dockter
 */
class StartParameter {
    String buildFileName
    List taskNames
    File currentDir
    boolean recursive
    boolean searchUpwards
    Map projectProperties = [:]
    Map systemPropertiesArgs = [:]
    File gradleUserHomeDir

    static StartParameter newInstance(Map args, StartParameter startParameterSrc) {
        StartParameter startParameter = new StartParameter()
        startParameter.buildFileName = startParameterSrc.buildFileName
        startParameter.taskNames = startParameterSrc.taskNames
        startParameter.currentDir = startParameterSrc.currentDir
        startParameter.recursive = startParameterSrc.recursive
        startParameter.searchUpwards = startParameterSrc.searchUpwards
        startParameter.projectProperties = startParameterSrc.projectProperties
        startParameter.systemPropertiesArgs = startParameterSrc.systemPropertiesArgs
        startParameter.gradleUserHomeDir = startParameterSrc.gradleUserHomeDir

        args.each { key, value ->
            startParameter."$key" = value
        }
        startParameter
    }

    public boolean equals(Object obj) {
        EqualsBuilder.reflectionEquals(this, obj)
    }

    public int hashCode() {
        HashCodeBuilder.reflectionHashCode(this)
    }
}
