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
package org.gradle.integtests.openapi;

import org.gradle.openapi.external.foundation.GradleInterfaceVersion2;
import org.gradle.openapi.external.foundation.ProjectVersion1;
import org.gradle.openapi.external.foundation.RequestVersion1;
import org.gradle.openapi.external.ui.SinglePaneUIVersion1;
import org.gradle.openapi.external.ui.UIFactory;

import java.io.File;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class CrossVersionBuilder {
    private File targetGradleHomeDir;
    private File currentDir;
    private String version;

    public String getVersion() {
        return version;
    }

    public void setVersion(String version) {
        this.version = version;
    }

    public File getCurrentDir() {
        return currentDir;
    }

    public void setCurrentDir(File currentDir) {
        this.currentDir = currentDir;
    }

    public File getTargetGradleHomeDir() {
        return targetGradleHomeDir;
    }

    public void setTargetGradleHomeDir(File targetGradleHomeDir) {
        this.targetGradleHomeDir = targetGradleHomeDir;
    }

    public void build() throws Exception {
        assert targetGradleHomeDir != null;
        assert currentDir != null;

        TestSingleDualPaneUIInteractionVersion1 testSingleDualPaneUIInteractionVersion1 = new TestSingleDualPaneUIInteractionVersion1(new TestAlternateUIInteractionVersion1(), new TestSettingsNodeVersion1());
        SinglePaneUIVersion1 singlePane = UIFactory.createSinglePaneUI(getClass().getClassLoader(), targetGradleHomeDir, testSingleDualPaneUIInteractionVersion1, false);

        String actualVersion = ((GradleInterfaceVersion2) singlePane.getGradleInterfaceVersion1()).getVersion();
        assertEquals(version, actualVersion);

        singlePane.setCurrentDirectory(currentDir);

        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) singlePane.getGradleInterfaceVersion1();

        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.REFRESH_TYPE);
        gradleInterface.addRequestObserver(testRequestObserver);

        singlePane.aboutToShow();

        gradleInterface.refreshTaskTree();

        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS);

        assertEquals(String.format("Execution failed%n%s", testRequestObserver.getOutput()), 0, (long) testRequestObserver.getResult());

        List<ProjectVersion1> rootProjects = gradleInterface.getRootProjects();
        assertTrue(!rootProjects.isEmpty());

        ProjectVersion1 rootProject = rootProjects.get(0);
        assertEquals(3, rootProject.getSubProjects().size());

        assertTrue(rootProject.getTasks().size() > 4);
    }
}
