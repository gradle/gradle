package org.gradle.api.tasks.diagnostics;

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.api.internal.project.ProjectInternal;
import org.gradle.configuration.ProjectTasksPrettyPrinter;
import org.gradle.execution.Dag;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.jmock.lib.legacy.ClassImposteriser;
import org.junit.Before;
import org.junit.Test;
import org.junit.Assert;
import static org.junit.Assert.*;
import org.junit.runner.RunWith;

import java.util.Set;
import java.util.SortedMap;
import java.util.TreeMap;

@RunWith(org.jmock.integration.junit4.JMock.class)
public class TaskListTaskTest {
    private final JUnit4Mockery context = new JUnit4Mockery();
    private ProjectTasksPrettyPrinter printer;
    private ProjectInternal project;
    private TaskListTask task;

    @Before
    public void setup() {
        context.setImposteriser(ClassImposteriser.INSTANCE);
        printer = context.mock(ProjectTasksPrettyPrinter.class);
        project = context.mock(ProjectInternal.class);

        context.checking(new Expectations(){{
            allowing(project).getRootProject();
            will(returnValue(project));
            allowing(project).getPath();
            will(returnValue(":path"));
        }});

        task = new TaskListTask(project, "list", new Dag());
        task.setPrinter(printer);
    }

    @Test
    public void isDagNeutral() {
        assertTrue(task.isDagNeutral());
    }

    @Test
    public void usesTaskListPrettyPrinterToWriteReport() {
        final SortedMap<Project, Set<Task>> tasks = new TreeMap<Project, Set<Task>>();

        context.checking(new Expectations(){{
            one(project).getAllTasks(true);
            will(returnValue(tasks));
            one(printer).getPrettyText(tasks);
            will(returnValue("<report>"));
        }});

        task.execute();
    }

}
