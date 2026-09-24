/*
 * Copyright 2026 the original author or authors.
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

package gradlebuild.docs;

import org.gradle.api.DefaultTask;
import org.gradle.api.Plugin;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.file.ConfigurableFileCollection;
import org.gradle.api.file.Directory;
import org.gradle.api.file.ProjectLayout;
import org.gradle.api.provider.Provider;
import org.gradle.api.tasks.SourceSet;
import org.gradle.api.tasks.SourceSetContainer;
import org.gradle.api.tasks.Sync;
import org.gradle.api.tasks.TaskContainer;
import org.gradle.api.tasks.TaskProvider;
import org.gradle.api.tasks.testing.Test;
import org.gradle.api.tasks.wrapper.Wrapper;
import org.gradle.language.base.plugins.LifecycleBasePlugin;

import java.io.File;

/**
 * Tests the documentation snippets under {@code src/snippets} with Exemplar.
 * <p>
 * Replaces the parts of the {@code org.gradle.samples} plugin (gradle/guides) that this build used:
 * every snippet is installed once per DSL into {@code build/working/samples/testing/<name>/<dsl>},
 * together with the Gradle wrapper scripts, its {@code tests*} directories and, unless it has one,
 * a generated sanity check. The {@code docsTest} task then runs them.
 */
public abstract class GradleSnippetsTestingPlugin implements Plugin<Project> {
    public static final String DOCS_TEST_SOURCE_SET_NAME = "docsTest";
    public static final String INSTALL_TASK_NAME = "installSnippetsForTest";

    private static final String[] INSTALL_EXCLUDES = {"**/build/**", "**/.gradle/**"};
    private static final String[] TESTED_INSTALL_EXCLUDES = {"README", "gradle/wrapper/**"};

    @Override
    public void apply(Project project) {
        project.getPluginManager().apply("groovy-base");

        ProjectLayout layout = project.getLayout();
        TaskContainer tasks = project.getTasks();
        File snippetsRoot = layout.getProjectDirectory().dir("src/snippets").getAsFile();
        Provider<Directory> testingRoot = layout.getBuildDirectory().dir("working/samples/testing");

        TaskProvider<Wrapper> wrapper = tasks.register("generateWrapperForSamples", Wrapper.class, task -> {
            task.setDescription("Generates the wrapper scripts installed into each snippet.");
            task.setJarFile(new File(task.getTemporaryDir(), "gradle/wrapper/gradle-wrapper.jar"));
            task.setScriptFile(new File(task.getTemporaryDir(), "gradlew"));
        });
        ConfigurableFileCollection wrapperFiles = project.getObjects().fileCollection();
        wrapperFiles.from(wrapper.map(DefaultTask::getTemporaryDir));
        wrapperFiles.builtBy(wrapper);

        TaskProvider<GenerateSnippetSanityCheck> sanityCheck = tasks.register("generateSanityCheckTests", GenerateSnippetSanityCheck.class, task -> {
            task.setDescription("Generates the Exemplar sanity check (`gradle tasks -q`) for snippets without their own.");
            task.getOutputFile().convention(layout.getBuildDirectory().file("tmp/" + task.getName() + "/sanityCheck.sample.conf"));
        });

        TaskProvider<Sync> install = tasks.register(INSTALL_TASK_NAME, Sync.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Installs every documentation snippet, once per DSL, for docsTest.");
            task.into(testingRoot);
            for (DocsSnippets.Snippet snippet : DocsSnippets.discover(snippetsRoot)) {
                File snippetDir = snippet.getDirectory();
                for (String dsl : snippet.getDsls()) {
                    task.into(snippet.getInstallName() + "/" + dsl, spec -> {
                        spec.exclude(TESTED_INSTALL_EXCLUDES);
                        spec.from(wrapperFiles);
                        spec.from(new File(snippetDir, "common"), from -> from.exclude(INSTALL_EXCLUDES));
                        spec.from(new File(snippetDir, dsl), from -> from.exclude(INSTALL_EXCLUDES));
                        spec.from(new File(snippetDir, "tests"));
                        spec.from(new File(snippetDir, "tests-common"));
                        spec.from(new File(snippetDir, "tests-" + dsl));
                        if (!snippet.hasExplicitSanityCheck()) {
                            spec.from(sanityCheck);
                        }
                    });
                }
            }
        });

        SourceSet docsTestSourceSet = project.getExtensions().getByType(SourceSetContainer.class).create(DOCS_TEST_SOURCE_SET_NAME);
        TaskProvider<Test> docsTest = tasks.register(DOCS_TEST_SOURCE_SET_NAME, Test.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Runs the documentation snippets as Exemplar tests.");
            task.setTestClassesDirs(docsTestSourceSet.getOutput().getClassesDirs());
            task.setClasspath(docsTestSourceSet.getRuntimeClasspath());
            task.setWorkingDir(layout.getProjectDirectory().getAsFile());
            task.dependsOn(install);
        });
        tasks.named(LifecycleBasePlugin.CHECK_TASK_NAME, task -> task.dependsOn(docsTest));

        // Kept so existing CI invocations (`docs:docsTest docs:checkSamples`) keep working.
        tasks.register("checkSamples", Task.class, task -> {
            task.setGroup(LifecycleBasePlugin.VERIFICATION_GROUP);
            task.setDescription("Installs the documentation snippets for testing. Kept for CI compatibility.");
            task.dependsOn(install);
        });
    }
}
