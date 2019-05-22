/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.process.internal;

import org.apache.tools.ant.Project;
import org.gradle.api.Action;
import org.gradle.process.internal.worker.WorkerProcessContext;

import java.io.Serializable;

import static org.hamcrest.CoreMatchers.*;
import static org.junit.Assert.assertThat;

public class RemoteProcess implements Action<WorkerProcessContext>, Serializable {
    public void execute(WorkerProcessContext workerProcessContext) {
        // Check environment
        assertThat(System.getProperty("test.system.property"), equalTo("value"));
        assertThat(System.getenv().get("TEST_ENV_VAR"), equalTo("value"));

        // Check ClassLoaders
        ClassLoader antClassLoader = Project.class.getClassLoader();
        ClassLoader thisClassLoader = getClass().getClassLoader();
        ClassLoader systemClassLoader = ClassLoader.getSystemClassLoader();

        assertThat(antClassLoader, sameInstance(systemClassLoader));
        assertThat(thisClassLoader, not(sameInstance(systemClassLoader)));
        assertThat(thisClassLoader.getParent().getParent(), equalTo(systemClassLoader));
        try {
            assertThat(thisClassLoader.loadClass(Project.class.getName()), sameInstance((Object) Project.class));
        } catch (ClassNotFoundException e) {
            throw new RuntimeException(e);
        }

        // Send some messages
        TestListenerInterface sender = workerProcessContext.getServerConnection().addOutgoing(TestListenerInterface.class);
        workerProcessContext.getServerConnection().connect();
        sender.send("message 1", 1);
        sender.send("message 2", 2);
    }
}
