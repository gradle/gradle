/*
 * Copyright 2010 the original author or authors.
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
package org.gradle.configuration;

import org.gradle.api.DefaultTask;
import org.gradle.api.internal.DocumentationRegistry;
import org.gradle.api.internal.tasks.options.OptionReader;
import org.gradle.api.model.ObjectFactory;
import org.gradle.api.provider.Property;
import org.gradle.api.tasks.TaskAction;
import org.gradle.api.tasks.options.Option;
import org.gradle.execution.selection.BuildTaskSelector;
import org.gradle.initialization.BuildClientMetaData;
import org.gradle.initialization.layout.ResolvedBuildLayout;
import org.gradle.internal.logging.text.StyledTextOutput;
import org.gradle.internal.logging.text.StyledTextOutputFactory;
import org.gradle.util.GradleVersion;
import org.gradle.work.DisableCachingByDefault;

import javax.inject.Inject;

import static org.gradle.internal.logging.text.StyledTextOutput.Style.UserInput;

@DisableCachingByDefault(because = "Produces only non-cacheable console output")
public abstract class Help extends DefaultTask {
    private final Property<String> taskPath = getObjectFactory().property(String.class);

    private final Property<TaskDetailsModel> taskModel = getObjectFactory().property(TaskDetailsModel.class).convention(taskPath.map(this::mapFromTaskPath));

    public Help() {
        // optimization: so value does not need to be recomputed during execution
        taskModel.finalizeValueOnRead();
    }

    private Property<String> getTaskPath() {
        return taskPath;
    }

    @Inject
    protected ObjectFactory getObjectFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected StyledTextOutputFactory getTextOutputFactory() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildClientMetaData getClientMetaData() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected BuildTaskSelector.BuildSpecificSelector getTaskSelector() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected OptionReader getOptionReader() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected ResolvedBuildLayout getResolvedBuildLayout() {
        throw new UnsupportedOperationException();
    }

    @Inject
    protected DocumentationRegistry getDocumentationRegistry() {
        throw new UnsupportedOperationException();
    }

    @TaskAction
    void displayHelp() {
        StyledTextOutput output = getTextOutputFactory().create(Help.class);
        BuildClientMetaData metaData = getClientMetaData();
        if (getTaskPath().isPresent()) {
            printTaskHelp(output);
        } else {
            printDefaultHelp(output, metaData);
        }
    }

    private void printTaskHelp(StyledTextOutput output) {
        TaskDetailsModel taskDetailModel = taskModel.get();
        TaskDetailPrinter taskDetailPrinter = new TaskDetailPrinter(taskDetailModel.getTaskPath(), taskDetailModel.getTasks());
        taskDetailPrinter.print(output);
    }

    private void printDefaultHelp(StyledTextOutput output, BuildClientMetaData metaData) {
        output.println();
        output.formatln("Welcome to Gradle %s.", GradleVersion.current().getVersion());
        output.println();

        if (getResolvedBuildLayout().isBuildDefinitionMissing()) {
            output.append("Directory '");
            output.append(getResolvedBuildLayout().getCurrentDirectory().getAbsolutePath());
            output.println("' does not contain a Gradle build.");
            output.println();
            output.text("To create a new build in this directory, run ");
            metaData.describeCommand(output.withStyle(UserInput), "init");
            output.println();
            output.println();
            output.append("For more detail on the 'init' task, see ");
            output.withStyle(UserInput).append(getDocumentationRegistry().getDocumentationFor("build_init_plugin"));
            output.println();
            output.println();
            output.append("For more detail on creating a Gradle build, see ");
            output.withStyle(UserInput).append(getDocumentationRegistry().getDocumentationFor("tutorial_using_tasks")); // this is the "build script basics" chapter, we're missing some kind of "how to write a Gradle build chapter"
            output.println();
        } else {
            output.text("To run a build, run ");
            metaData.describeCommand(output.withStyle(UserInput), "<task> ...");
            output.println();
            output.println();
            output.text("To see a list of available tasks, run ");
            metaData.describeCommand(output.withStyle(UserInput), "tasks");
            output.println();
            output.println();
            output.text("To see more detail about a task, run ");
            metaData.describeCommand(output.withStyle(UserInput), "help --task <task>");
            output.println();
        }
        output.println();
        output.text("To see a list of command-line options, run ");
        metaData.describeCommand(output.withStyle(UserInput), "--help");
        output.println();
        output.println();
        output.append("For more detail on using Gradle, see ");
        output.withStyle(UserInput).append(getDocumentationRegistry().getDocumentationFor("command_line_interface"));
        output.println();
        output.println();
        output.text("For troubleshooting, visit ");
        output.withStyle(UserInput).text("https://help.gradle.org");
        output.println();
    }

    @Option(option = "task", description = "The task to show help for.")
    public void setTaskPath(String taskPath) {
        this.getTaskPath().set(taskPath);
    }

    private TaskDetailsModel mapFromTaskPath(String taskPath) {
        return TaskDetailsModel.from(taskPath, getTaskSelector(), getOptionReader());
    }
}
