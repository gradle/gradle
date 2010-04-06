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
package org.gradle.integtests

import org.gradle.foundation.ProjectView
import org.gradle.gradleplugin.foundation.GradlePluginLord
import org.junit.runner.RunWith
import org.junit.Test
import org.junit.Before
import org.junit.After
import org.junit.Assert;
import org.gradle.util.GFileUtils
import org.gradle.openapi.wrappers.foundation.GradleInterfaceWrapper
import org.gradle.openapi.external.foundation.ProjectVersion1
import org.junit.Rule

/**
 This tests the multiproject sample with the GradleView mechanism.
 @author mhunsicker
 */
@RunWith(DistributionIntegrationTestRunner.class)
class MultiprojectProjectAndTaskListIntegrationTest {

    static final String JAVA_PROJECT_NAME = 'javaproject'
    static final String SHARED_NAME = 'shared'
    static final String API_NAME = 'api'
    static final String WEBAPP_NAME = 'webservice'
    static final String SERVICES_NAME = 'services'
    static final String WEBAPP_PATH = "$SERVICES_NAME/$WEBAPP_NAME" as String

    private File javaprojectDir
    private List projects;

    @Rule public final GradleDistribution dist = new GradleDistribution()
    private final GradleExecuter executer = dist.executer;

    @Before
    void setUp() {
        javaprojectDir = new File(dist.samplesDir, 'java/multiproject')
        projects = [SHARED_NAME, API_NAME, WEBAPP_NAME, SERVICES_NAME].collect {"JAVA_PROJECT_NAME/$it"} + JAVA_PROJECT_NAME
        deleteBuildDir(projects)
    }

    @After
    void tearDown() {
        deleteBuildDir(projects)
    }

    private def deleteBuildDir(List projects) {
        return projects.each {GFileUtils.deleteDirectory(new File(dist.samplesDir, "$it/build"))}
    }

    /*
       This tests against the multiproject sample. It expects to find not just
       the root level projects, but also the nested sub projects
       (services:webservice). This isn't really interested in the actual tasks
       themselves (I fear those may change too often to worry with keeping the
       test up to date).

       @author mhunsicker
    */

    @Test
    public void multiProjectjavaProjectSample() {
        // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('assemble').run();

        File multiProjectDirectory = new File(dist.getSamplesDir(), "java/multiproject");
        Assert.assertTrue(multiProjectDirectory.exists());

        GradlePluginLord gradlePluginLord = new GradlePluginLord();
        gradlePluginLord.setCurrentDirectory(multiProjectDirectory);
        gradlePluginLord.setGradleHomeDirectory(dist.gradleHomeDir);

        //refresh the projects and wait. This will throw an exception if it fails.
        org.gradle.foundation.TestUtility.refreshProjectsBlocking(gradlePluginLord, 20);  //we'll give this 20 seconds to complete

        //get the root project
        List<ProjectView> projects = gradlePluginLord.getProjects();
        Assert.assertNotNull(projects);

        //make sure there weren't other root projects found.
        Assert.assertEquals(1, projects.size());

        ProjectView rootProject = projects.get(0);
        Assert.assertNotNull(rootProject);
        Assert.assertEquals("multiproject", rootProject.getName());

        //now check for sub projects, api, shared, and services
        ProjectView apiProject = rootProject.getSubProject("api");
        Assert.assertNotNull(apiProject);
        Assert.assertTrue(apiProject.getSubProjects().isEmpty());  //this has no sub projects

        ProjectView sharedProject = rootProject.getSubProject("shared");
        Assert.assertNotNull(sharedProject);
        Assert.assertTrue(sharedProject.getSubProjects().isEmpty());  //this has no sub projects

        ProjectView servicesProject = rootProject.getSubProject("services");
        Assert.assertNotNull(servicesProject);

        //services has a sub project
        ProjectView webservicesProject = servicesProject.getSubProject("webservice");
        Assert.assertNotNull(webservicesProject);
        Assert.assertTrue(webservicesProject.getSubProjects().isEmpty());  //this has no sub projects

        //make sure we didn't inadvertantly find other sub projects.
        Assert.assertEquals(3, rootProject.getSubProjects().size());
    }

   /**
    This tests that the wrappers for projects and tasks are working
    */
   @Test
   public void testOpenAPIWrapperProjectAndTaskList()
   {
     // Build and test projects
        executer.inDirectory(javaprojectDir).withTasks('assemble').run();

        File multiProjectDirectory = new File(dist.getSamplesDir(), "java/multiproject");
        Assert.assertTrue(multiProjectDirectory.exists());

        GradlePluginLord gradlePluginLord = new GradlePluginLord();
        gradlePluginLord.setCurrentDirectory(multiProjectDirectory);
        gradlePluginLord.setGradleHomeDirectory(dist.gradleHomeDir);

        GradleInterfaceWrapper wrapper = new GradleInterfaceWrapper( gradlePluginLord );

        //the rest of this uses the open API mechanism to access the projects and tasks

        //refresh the projects and wait. This will throw an exception if it fails.
        org.gradle.foundation.TestUtility.refreshProjectsBlocking(gradlePluginLord, 20);  //we'll give this 20 seconds to complete

        //get the root project
        List<ProjectVersion1> projects = wrapper.getRootProjects();
        Assert.assertNotNull(projects);

        //make sure there weren't other root projects found.
        Assert.assertEquals(1, projects.size());

        ProjectVersion1 rootProject = projects.get(0);
        Assert.assertNotNull(rootProject);
        Assert.assertEquals("multiproject", rootProject.getName());

        //now check for sub projects, api, shared, and services
        ProjectVersion1 apiProject = rootProject.getSubProject("api");
        Assert.assertNotNull(apiProject);
        Assert.assertTrue(apiProject.getSubProjects().isEmpty());  //this has no sub projects

        ProjectVersion1 sharedProject = rootProject.getSubProject("shared");
        Assert.assertNotNull(sharedProject);
        Assert.assertTrue(sharedProject.getSubProjects().isEmpty());  //this has no sub projects

        ProjectVersion1 servicesProject = rootProject.getSubProject("services");
        Assert.assertNotNull(servicesProject);

        //services has a sub project
        ProjectVersion1 webservicesProject = servicesProject.getSubProject("webservice");
        Assert.assertNotNull(webservicesProject);
        Assert.assertTrue(webservicesProject.getSubProjects().isEmpty());  //this has no sub projects

        //make sure we didn't inadvertantly find other sub projects.
        Assert.assertEquals(3, rootProject.getSubProjects().size());

        //I don't want to keep the actual tasks in synch, but let's make sure there's something there.
        def tasks = apiProject.getTasks()
        Assert.assertNotNull( tasks );
        Assert.assertFalse( tasks.isEmpty() );
   }
}