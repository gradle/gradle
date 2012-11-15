/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.publish.ivy.internal;

import org.gradle.api.Action;
import org.gradle.api.XmlProvider;
import org.gradle.api.internal.tasks.DefaultTaskDependency;
import org.gradle.api.internal.tasks.TaskResolver;
import org.gradle.api.tasks.TaskDependency;
import org.gradle.listener.ActionBroadcast;

import java.io.File;

public class DefaultIvyModuleDescriptor implements IvyModuleDescriptorInternal {

    private final ActionBroadcast<XmlProvider> xmlActions = new ActionBroadcast<XmlProvider>();
    private File file;

    private final DefaultTaskDependency builtBy;

    public DefaultIvyModuleDescriptor(TaskResolver taskResolver) {
        builtBy = new DefaultTaskDependency(taskResolver);
    }

    public void withXml(Action<? super XmlProvider> action) {
        xmlActions.add(action);
    }

    public File getFile() {
        return file;
    }

    public void setFile(File descriptorFile) {
        this.file = descriptorFile;
    }

    public Action<XmlProvider> getXmlAction() {
        return xmlActions;
    }

    public void builtBy(Object... tasks) {
        builtBy.add(tasks);
    }

    public TaskDependency getBuildDependencies() {
        return builtBy;
    }
}
