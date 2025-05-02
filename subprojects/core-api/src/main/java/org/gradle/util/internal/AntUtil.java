/*
 * Copyright 2021 the original author or authors.
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
package org.gradle.util.internal;

import org.apache.tools.ant.MagicNames;
import org.apache.tools.ant.Project;
import org.apache.tools.ant.ProjectHelper;
import org.apache.tools.ant.Task;
import org.gradle.api.internal.project.ant.AntLoggingAdapter;

public class AntUtil {
    /**
     * @return Factory method to create new Project instances
     */
    public static Project createProject() {
        final Project project = new Project();

        final ProjectHelper helper = ProjectHelper.getProjectHelper();
        project.addReference(MagicNames.REFID_PROJECT_HELPER, helper);
        helper.getImportStack().addElement("AntBuilder"); // import checks that stack is not empty

        project.addBuildListener(new AntLoggingAdapter());

        project.init();
        project.getBaseDir();
        return project;
    }

    public static void execute(Task task) {
        task.setProject(createProject());
        task.execute();
    }

    /**
     * Masks a string against Ant property expansion.
     * This needs to be used when adding a File as String property
     * via {@link groovy.ant.AntBuilder}.
     * @param string to mask
     * @return The masked String
     */
    public static String maskFilename(String string) {
            return string.replaceAll("\\$", "\\$\\$");
    }
}
