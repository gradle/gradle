/*
 * Copyright 2009 the original author or authors.
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
package org.gradle.execution;

import org.apache.commons.io.FileUtils;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.util.HelperUtil;
import static org.hamcrest.Matchers.*;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.After;
import static org.junit.Assert.assertThat;
import org.junit.Before;

import java.io.File;
import java.io.IOException;

/**
 * @author Hans Dockter
 */
public class DefaultOutputHistoryWriterTest {
    private JUnit4Mockery context = new JUnit4Mockery();

    private File historyDir = HelperUtil.makeNewTestDir();
    private DefaultOutputHistoryWriter outputHistoryWriter = new DefaultOutputHistoryWriter();
    private Task taskStub = context.mock(Task.class);
    private Project projectStub = context.mock(Project.class);
    private static final String TASK_PATH = ":someProjectPath:someTaskName";
    private static final String CONVERTED_TASK_PATH = TASK_PATH.replace(":", "/");
    private static final String HISTORY_FILE_PATH = OutputHistoryWriter.HISTORY_DIR_NAME + "/" + CONVERTED_TASK_PATH;

    @Before
    public void setUp() {
        context.checking(new Expectations() {{
            allowing(taskStub).getPath();
            will(returnValue(TASK_PATH));
            allowing(taskStub).getProject();
            will(returnValue(projectStub));
            allowing(projectStub).getBuildDir();
            will(returnValue(historyDir));
        }});   
    }
    
    @After
    public void tearDown() {
        HelperUtil.deleteTestDir();
    }

    @org.junit.Test
    public void shouldHaveHistoryFileWhenTaskSuccessfullyExecuted() throws IOException {
        long timestampLowerLimit = System.currentTimeMillis();
        outputHistoryWriter.taskSuccessfullyExecuted(taskStub);
        File historyFile = new File(historyDir, HISTORY_FILE_PATH);
        assertThat(historyFile.isFile(), equalTo(true));
        long timestamp = Long.parseLong(FileUtils.readFileToString(historyFile));
        assertThat(timestamp, greaterThanOrEqualTo(timestampLowerLimit));
        assertThat(timestamp, lessThanOrEqualTo(System.currentTimeMillis()));
    }

    @org.junit.Test
    public void shouldHaveNoHistoryFileWhenTaskFailed() {
        outputHistoryWriter.taskFailed(taskStub);
        File historyFile = new File(historyDir, HISTORY_FILE_PATH);
        assertThat(historyFile.exists(), equalTo(false));
    }

    @org.junit.Test
    public void shouldDeleteExisitingHistoryFileWhenTaskFailed() throws IOException {
        File historyFile = new File(historyDir, HISTORY_FILE_PATH);
        FileUtils.touch(historyFile);
        outputHistoryWriter.taskFailed(taskStub);
        assertThat(historyFile.exists(), equalTo(false));
    }
}
