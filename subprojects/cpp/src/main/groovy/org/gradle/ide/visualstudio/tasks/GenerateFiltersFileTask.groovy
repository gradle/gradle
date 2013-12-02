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
package org.gradle.ide.visualstudio.tasks
import org.gradle.api.Incubating
import org.gradle.ide.visualstudio.tasks.internal.AbsoluteFileNameTransformer
import org.gradle.ide.visualstudio.tasks.internal.VisualStudioFiltersFile
import org.gradle.ide.visualstudio.internal.VisualStudioProject
import org.gradle.plugins.ide.api.GeneratorTask
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObject
import org.gradle.plugins.ide.internal.generator.generator.PersistableConfigurationObjectGenerator

@Incubating
class GenerateFiltersFileTask extends GeneratorTask<PersistableConfigurationObject> {
    VisualStudioProject vsProject

    GenerateFiltersFileTask() {
        generator = new VisualStudioConfigurationObjectGenerator();
    }

    void setVisualStudioProject(VisualStudioProject vsProject) {
        this.vsProject = vsProject
        setOutputFile(vsProject.getFiltersFile())
    }

    @Override
    File getInputFile() {
        return null
    }

    private class VisualStudioConfigurationObjectGenerator extends PersistableConfigurationObjectGenerator<PersistableConfigurationObject> {
        public PersistableConfigurationObject create() {
            return new VisualStudioFiltersFile(new AbsoluteFileNameTransformer())
        }

        public void configure(PersistableConfigurationObject object) {
            VisualStudioFiltersFile filtersFile = object as VisualStudioFiltersFile;
            VisualStudioProject vsProject = GenerateFiltersFileTask.this.vsProject
            vsProject.sourceFiles.each {
                filtersFile.addSource(it)
            }
            vsProject.headerFiles.each {
                filtersFile.addHeader(it)
            }
        }
    }
}