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
package org.gradle.api.reporting

import org.gradle.api.Task
import org.gradle.api.internal.AsmBackedClassGenerator
import org.gradle.api.internal.DependencyInjectingInstantiator
import org.gradle.api.internal.project.AbstractProject
import org.gradle.api.internal.project.taskfactory.AnnotationProcessingTaskFactory
import org.gradle.api.internal.project.taskfactory.TaskFactory
import org.gradle.internal.reflect.DirectInstantiator
import org.gradle.internal.reflect.Instantiator
import org.gradle.internal.service.DefaultServiceRegistry
import org.gradle.util.GUtil
import org.gradle.util.HelperUtil
import org.junit.Test
import spock.lang.Specification

import static org.junit.Assert.assertTrue

class GenerateBuildDashboardSpec extends Specification {

    private DefaultServiceRegistry serviceRegistry = new DefaultServiceRegistry();
    private Instantiator instantiator = new DependencyInjectingInstantiator(serviceRegistry);
    private final AnnotationProcessingTaskFactory rootFactory = new AnnotationProcessingTaskFactory(new TaskFactory(new AsmBackedClassGenerator()));

    @Test
    def "does no work if html report is disabled"() {
        setup:
        GenerateBuildDashboard task = createTask("dashboard")
        when:
        task.reports.html.enabled = false
        and:
        task.run()
        then:
        !task.didWork
    }

    public Task createTask(String name) {
        AbstractProject project = HelperUtil.createRootProject();
        serviceRegistry.add(Instantiator.class, new DirectInstantiator());
        Task task = rootFactory.createChild(project, instantiator).createTask(GUtil.map(Task.TASK_TYPE, GenerateBuildDashboard.class, Task.TASK_NAME, name));
        assertTrue(GenerateBuildDashboard.class.isAssignableFrom(task.getClass()));
        return GenerateBuildDashboard.class.cast(task);
    }
}
