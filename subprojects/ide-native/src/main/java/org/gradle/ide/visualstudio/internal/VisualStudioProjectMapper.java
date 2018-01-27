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

package org.gradle.ide.visualstudio.internal;

import org.apache.commons.lang.StringUtils;

import java.util.List;

public class VisualStudioProjectMapper {

    public static String getProjectName(VisualStudioTargetBinary targetBinary) {
        return getProjectName(targetBinary.getProjectPath(), targetBinary.getComponentName(), targetBinary.getProjectType());
    }

    public static String getProjectName(String projectPath, String componentName, VisualStudioTargetBinary.ProjectType type) {
        return projectPrefix(projectPath) + componentName + type.getSuffix();
    }

    public static String getConfigurationName(List<String> variantDimensions) {
        return makeName(variantDimensions);
    }

    private static String projectPrefix(String projectPath) {
        if (":".equals(projectPath)) {
            return "";
        }
        return projectPath.substring(1).replace(":", "_") + "_";
    }

    private static String makeName(Iterable<String> components) {
        StringBuilder builder = new StringBuilder();
        for (String component : components) {
            if (component != null && component.length() > 0) {
                if (builder.length() == 0) {
                    builder.append(component);
                } else {
                    builder.append(StringUtils.capitalize(component));
                }
            }
        }
        return builder.toString();
    }
}
