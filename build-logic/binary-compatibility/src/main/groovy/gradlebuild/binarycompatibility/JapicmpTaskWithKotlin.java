/*
 * Copyright 2022 the original author or authors.
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

package gradlebuild.binarycompatibility;

import me.champeau.gradle.japicmp.JapicmpTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.Configuration;
import org.gradle.api.artifacts.VersionCatalog;
import org.gradle.api.artifacts.VersionCatalogsExtension;
import org.gradle.api.artifacts.dsl.DependencyHandler;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.tasks.CacheableTask;

import java.lang.reflect.Field;

@CacheableTask
public abstract class JapicmpTaskWithKotlin extends JapicmpTask {

    public JapicmpTaskWithKotlin() {
        super();
        addKotlinCompilerToJapicmpClasspath();
        getMaxWorkerHeap().convention("1g");
    }

    // it's easier to do this via reflection than to copy the whole task
    private void addKotlinCompilerToJapicmpClasspath() {
        try {
            Field additionalJapicmpClasspathField = JapicmpTask.class.getDeclaredField("additionalJapicmpClasspath");
            additionalJapicmpClasspathField.setAccessible(true);
            ConfigurableFileCollection additionalJapicmpClasspath = (ConfigurableFileCollection) additionalJapicmpClasspathField.get(this);
            additionalJapicmpClasspath.from(resolveKotlinCompilerEmbeddable());
        } catch (Exception e) {
            throw new RuntimeException("Got an error while patching JapicmpTask task", e);
        }
    }

    private Configuration resolveKotlinCompilerEmbeddable() {
        Project project = getProject();
        DependencyHandler dependencies = project.getDependencies();
        VersionCatalog libs = project.getExtensions().getByType(VersionCatalogsExtension.class).named("libs");
        return project.getConfigurations().detachedConfiguration(
            dependencies.create(libs.findLibrary("kotlinCompilerEmbeddable").get().get())
        );
    }

}
