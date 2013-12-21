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
package org.gradle.integtests.openapi

import org.gradle.integtests.fixtures.TestResources
import org.gradle.integtests.fixtures.executer.IntegrationTestBuildContext
import org.gradle.internal.os.OperatingSystem
import org.gradle.openapi.external.ExternalUtility
import org.gradle.openapi.external.foundation.GradleInterfaceVersion2
import org.gradle.openapi.external.foundation.ProjectVersion1
import org.gradle.openapi.external.foundation.RequestVersion1
import org.gradle.openapi.external.foundation.TaskVersion1
import org.gradle.openapi.external.foundation.favorites.FavoriteTaskVersion1
import org.gradle.openapi.external.foundation.favorites.FavoritesEditorVersion1
import org.gradle.openapi.external.ui.*
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider
import org.gradle.util.PreconditionVerifier
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.junit.Assert
import org.junit.ClassRule
import org.junit.Rule
import org.junit.Test

import javax.swing.*
import java.awt.*
import java.awt.event.HierarchyEvent
import java.awt.event.HierarchyListener
import java.util.concurrent.TimeUnit

import static org.hamcrest.Matchers.*

/**
 * This tests numerous aspects of the Open API UI. This is how the Idea plugin extracts the UI from
 * Gradle.
 */
@Requires(TestPrecondition.SWING)
class OpenApiUiTest {

    @Rule public final TestNameTestDirectoryProvider temporaryFolder = new TestNameTestDirectoryProvider()
    private IntegrationTestBuildContext buildContext = new IntegrationTestBuildContext();
    @Rule public TestResources resources = new TestResources(temporaryFolder, 'testproject')
    @Rule public OpenApiFixture openApi = new OpenApiFixture(temporaryFolder)
    @ClassRule public static PreconditionVerifier verifier = new PreconditionVerifier()

    /**
     This tests to see if we can call the UIFactory to create a single pane UI.
     This is only testing that extracting the UI returns something without giving
     errors and that it has a component. This is just a good general-case test
     to make sure the basics are working.
     */
    @Test
    void testSinglePaneBasic() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()

        //make sure we got something
        Assert.assertNotNull(singlePane)

        //tell it we're about to show it, so it'll create a component
        singlePane.aboutToShow();

        //make sure we now have that component
        Assert.assertNotNull(singlePane.getComponent())
    }

    /**
     * This tests to see if we can call the UIFactory to create a dual pane UI.
     This is only testing that extracting the UI returns something without giving
     errors and that it has its dual components. This is just a good general-case test
     to make sure the basics are working.
     */
    @Test
    void testDualPaneBasic() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()

        //make sure we got something
        Assert.assertNotNull(dualPane)

        //tell it we're about to show it, so it'll create a component
        dualPane.aboutToShow();

        //make sure we now have the main component
        Assert.assertNotNull(dualPane.getMainComponent())

        //and the output component
        Assert.assertNotNull(dualPane.getOutputPanel())
    }

    /**
     * This verifies that favorites are working for some basics. We're going to test this with both
     * the single and dual pane UIs (they actually use the same editor then for other tests we'll
     * assume they're same).
     */
    @Test
    void testFavoritesBasic() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        checkFavoritesBasic(singlePane)

        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        checkFavoritesBasic(dualPane)
    }

    /**
     * This verifies that we favorites are basically working based on the given UI. We're going to add one, then
     * do some 'gets' to find the just-added favorite.
     */
    private void checkFavoritesBasic(BasicGradleUIVersion1 basicGradleUI) {
        FavoritesEditorVersion1 editor = basicGradleUI.getFavoritesEditor()

        //there should be no favorites as of yet
        Assert.assertTrue(editor.getFavoriteTasks().isEmpty())

        //add one (doesn't really matter what it is)
        def fullCommandLine = "-t -S"
        def displayName = "Task List With Stack trace"
        FavoriteTaskVersion1 favorite = editor.addFavorite(fullCommandLine, displayName, true)

        //make sure something was added
        Assert.assertEquals(1, editor.getFavoriteTasks().size())

        //get the newly-added favorite by command line.
        FavoriteTaskVersion1 matchingFavorite1 = editor.getFavorite(fullCommandLine)
        Assert.assertEquals(favorite, matchingFavorite1)

        //get the newly-added favorite by displayName.
        FavoriteTaskVersion1 matchingFavorite2 = editor.getFavoriteByDisplayName(displayName)
        Assert.assertEquals(favorite, matchingFavorite2)

        Assert.assertTrue(matchingFavorite2.alwaysShowOutput())
    }

    /**
     * This verifies that we can edit favorites. We're going to add a favorite then edit its
     * command line, display name, and 'show output' setting.
     */
    @Test
    void testEditingFavorites() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        FavoritesEditorVersion1 editor = singlePane.getFavoritesEditor()

        def originalFullCommandLine = "-t -S"
        def originalDisplayName = "Task List With Stack trace"
        FavoriteTaskVersion1 addedFavorite = editor.addFavorite(originalFullCommandLine, originalDisplayName, true)

        //make sure we can find the just-added favorite
        Assert.assertNotNull(editor.getFavorite(originalFullCommandLine))
        Assert.assertNotNull(editor.getFavoriteByDisplayName(originalDisplayName))

        String newFullCommandLine = "-t -S -d"
        String newDisplayName = "new task list"
        String error = editor.editFavorite(addedFavorite, newFullCommandLine, newDisplayName, false)
        Assert.assertNull(error)  //we should get no error

        //now we shouldn't be able to find the favorite using the original values. This is part of verifying the values were in fact changed.
        Assert.assertNull(editor.getFavorite(originalFullCommandLine))
        Assert.assertNull(editor.getFavoriteByDisplayName(originalDisplayName))

        //make sure we can find it using the new values. This is part of verifying the values were in fact changed.
        Assert.assertNotNull(editor.getFavorite(newFullCommandLine))
        Assert.assertNotNull(editor.getFavoriteByDisplayName(newDisplayName))

        //there should just be 1 favorite
        Assert.assertEquals(1, editor.getFavoriteTasks().size())
    }

    /**
     * This verifies that we can remove favorites. We're going to add some favorites then remove them
     * verifying that they've gone.
     */
    @Test
    void testRemovingFavorites() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        FavoritesEditorVersion1 editor = singlePane.getFavoritesEditor()

        //there should be no favorites as of yet
        Assert.assertTrue(editor.getFavoriteTasks().isEmpty())

        //add one (doesn't really matter what it is)
        String command1 = "build"
        FavoriteTaskVersion1 favorite1 = editor.addFavorite(command1, command1, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command1))
        Assert.assertEquals(1, editor.getFavoriteTasks().size())

        //add another one
        String command2 = "build -xtest"
        FavoriteTaskVersion1 favorite2 = editor.addFavorite(command2, command2, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command2))
        Assert.assertEquals(2, editor.getFavoriteTasks().size())

        String command3 = "clean"
        FavoriteTaskVersion1 favorite3 = editor.addFavorite(command3, command3, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command3))
        Assert.assertEquals(3, editor.getFavoriteTasks().size())

        String command4 = "docs"
        FavoriteTaskVersion1 favorite4 = editor.addFavorite(command4, command4, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command4))
        Assert.assertEquals(4, editor.getFavoriteTasks().size())

        //now remove one of them
        java.util.List removed1 = [favorite2]
        editor.removeFavorites(removed1)

        //make sure it was removed
        Assert.assertNull(editor.getFavorite(command2))
        Assert.assertEquals(3, editor.getFavoriteTasks().size())

        //now remove multiples
        java.util.List removed2 = [favorite1, favorite4]
        editor.removeFavorites(removed2)

        //make sure they were both removed
        Assert.assertNull(editor.getFavorite(command1))
        Assert.assertNull(editor.getFavorite(command4))
        Assert.assertEquals(1, editor.getFavoriteTasks().size())
    }

    /**
     * This tests executing multiple favorites at once. We'll add two favorites, then execute both of them
     * via GradleInterfaceVersion1.executeFavorites(). This should execute both as a single command
     * concatenating them together.
     */
    @Test
    void testExecutingFavorites() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        FavoritesEditorVersion1 editor = singlePane.getFavoritesEditor()

        //this starts the execution queue
        singlePane.aboutToShow()

        //there should be no favorites as of yet
        Assert.assertTrue(editor.getFavoriteTasks().isEmpty())

        //add one (doesn't really matter what it is)
        String command1 = "build"
        FavoriteTaskVersion1 favorite1 = editor.addFavorite(command1, command1, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command1))
        Assert.assertEquals(1, editor.getFavoriteTasks().size())

        //add another one
        String command2 = "clean"
        FavoriteTaskVersion1 favorite2 = editor.addFavorite(command2, command2, true)

        //make sure it was added
        Assert.assertNotNull(editor.getFavorite(command2))
        Assert.assertEquals(2, editor.getFavoriteTasks().size())

        //add a request observer so we can observe when the command is finished. This allows us to
        //see what was actually executed.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.EXECUTION_TYPE)
        ((GradleInterfaceVersion2) singlePane.getGradleInterfaceVersion1()).addRequestObserver(testRequestObserver)

        //now execute both favorites
        java.util.List<FavoriteTaskVersion1> favorites = [favorite1, favorite2]
        RequestVersion1 request = ((GradleInterfaceVersion2) singlePane.getGradleInterfaceVersion1()).executeFavorites(favorites)

        Assert.assertNotNull(request)

        //verify that the actual command that was executed is a concatenation of both favorites
        Assert.assertThat(request.getFullCommandLine(), startsWith("build clean"))
    }

    /**
     * This tests getting projects and tasks from gradle. It then goes through a series of tests
     * related to projects and tasks (such as making sure their description is returned, their
     * parent is returned, etc).
     */
    @Test
    void testProjectsAndTasks() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) dualPane.getGradleInterfaceVersion1()

        //make sure our samples directory exists
        Assert.assertTrue(gradleInterface.getCurrentDirectory().isDirectory())

        //add a request observer so we can observe when the command is finished. This allows us to
        //see what was actually executed.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.REFRESH_TYPE)
        gradleInterface.addRequestObserver(testRequestObserver)

        //this starts the execution queue
        dualPane.aboutToShow()

        gradleInterface.refreshTaskTree()

        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)

        Assert.assertEquals("Execution Failed: " + testRequestObserver.output, 0, testRequestObserver.result)

        java.util.List<ProjectVersion1> rootProjects = gradleInterface.getRootProjects();
        Assert.assertFalse(rootProjects.isEmpty());   //do we have any root projects?

        ProjectVersion1 rootProject = rootProjects.get(0);
        Assert.assertNotNull(rootProject);
        Assert.assertThat(rootProject.getSubProjects().size(), equalTo(3))

        //Quick check to make sure there are tasks on each of the sub projects.
        //The exact task names will change over time, so I don't want to try
        //to test for those. I'll just make sure there are several.
        Iterator<ProjectVersion1> iterator = rootProjects.get(0).getSubProjects().iterator();
        while (iterator.hasNext()) {
            ProjectVersion1 projectVersion1 = iterator.next();
            Assert.assertTrue(projectVersion1.getTasks().size() > 4);
        }

        //there should be a 'services' project
        ProjectVersion1 servicesProject = rootProjects.get(0).getSubProject("services");
        Assert.assertNotNull(servicesProject);

        //and it contains a 'webservice' sub project
        ProjectVersion1 webserviceProject = servicesProject.getSubProject("webservice");
        Assert.assertNotNull(webserviceProject);

        ProjectVersion1 apiProject = rootProjects.get(0).getSubProject("api");
        Assert.assertNotNull(apiProject);

        //verify the parent project is set correctly
        Assert.assertEquals(servicesProject, webserviceProject.getParentProject())

        //verify its full name is correct (this might should be prefixed with a colon)
        Assert.assertEquals("services:webservice", webserviceProject.getFullProjectName())

        //verify getSubProjectFromFullPath works
        ProjectVersion1 foundProject = rootProject.getSubProjectFromFullPath("services:webservice")
        Assert.assertNotNull("Failed to find services:webservice", foundProject)
        Assert.assertEquals(webserviceProject, foundProject)

        //verify that are multiple tasks here (we know their should be)
        Assert.assertTrue(webserviceProject.getTasks().size() > 4);

        //verify getTaskFromFullPath works
        TaskVersion1 apiBuildTask = rootProject.getTaskFromFullPath(":api:build")
        Assert.assertNotNull("Failed to find :api:build", apiBuildTask)

        Assert.assertEquals(apiProject, apiBuildTask.getProject())

        //then make sure it has a description
        Assert.assertNotNull(apiBuildTask.getDescription())

        //and that its not marked as the default (we need a task to be the default so we can verify it returns true)
        Assert.assertFalse(apiBuildTask.isDefault())

        //there are no default tasks here
        Assert.assertTrue(apiProject.getDefaultTasks().isEmpty())

        //this build task is a child of the api project. Should be the same task we got earlier
        TaskVersion1 buildTask = apiProject.getTask("build")
        Assert.assertNotNull("Failed to find build task", buildTask)
        Assert.assertEquals(apiBuildTask, buildTask)
    }

    /**
     * This verifies that the GradleInterfaceVersion1.refreshTaskTree that takes
     * additional arguments works. We're not really interested in what those additional
     * arguments are, just that it passes them along.
     */
    @Test
    void testRefreshWithArguments() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) dualPane.getGradleInterfaceVersion1()

        //make sure our samples directory exists
        if (!gradleInterface.getCurrentDirectory().exists()) {
            throw new AssertionError('sample project missing. Expected it at: ' + gradleInterface.getCurrentDirectory())
        }

        //this starts the execution queue
        dualPane.aboutToShow()

        //add a request observer so we can observe when the command is finished. This allows us to
        //see what was actually executed.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.REFRESH_TYPE)
        gradleInterface.addRequestObserver(testRequestObserver)

        RequestVersion1 request = gradleInterface.refreshTaskTree2("-xtest")

        //make sure that the actual request is the normal refresh request with our
        //(this line is really what we're trying to test)
        Assert.assertThat(request.getFullCommandLine(), startsWith("tasks -xtest"))

        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)

        Assert.assertEquals("Execution Failed: " + testRequestObserver.output, 0, testRequestObserver.result)

        Assert.assertEquals("Not our request", request, testRequestObserver.request);
    }

    /**
     * This verifies that you can add custom stuff to the setup tab. This is a UI test and is kinda tricky. We're going
     * to use a HierarchyListener to see if our component is made visible. This will confirm if it was added or not
     * because it must be added to be made visible. To do this, however, we'll need to actually show the UI. All we're
     * really doing here, is adding a 'custom' component to the UI, then adding the UI to a frame, then showing the frame,
     * so we can verify that our component was shown.
     */
    @Test
    void testAddingComponentToSetupTab() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;  // Can't run this test in headless mode!
        }

        JLabel label = new JLabel("Testing Testing 123")
        TestVisibilityHierarchyListener hierarchyAdapter = new TestVisibilityHierarchyListener()
        label.addHierarchyListener(hierarchyAdapter)

        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()

        //make sure we haven't been told the component was shown or hidden yet
        Assert.assertFalse(hierarchyAdapter.componentWasShown)
        Assert.assertFalse(hierarchyAdapter.componentWasHidden)

        singlePane.aboutToShow();

        singlePane.setCustomPanelToSetupTab(label)

        //this still should not show the component (at this point, we're probably more testing that our hierarchyAdapter is working)
        Assert.assertFalse(hierarchyAdapter.componentWasShown)
        Assert.assertFalse(hierarchyAdapter.componentWasHidden)

        //now create a frame, place the UI in it, then show it briefly
        JFrame frame = openApi.open(singlePane)

        //set the Setup tab as the current tab. This is required to actually show the component.
        int setupTabIndex = singlePane.getGradleTabIndex("Setup");
        Assert.assertTrue("Failed to get index of setup tab", setupTabIndex != -1)
        singlePane.setCurrentGradleTab(setupTabIndex);

        //still should not show the component (its not yet visible, but is about to be)
        Assert.assertFalse(hierarchyAdapter.componentWasShown)
        Assert.assertFalse(hierarchyAdapter.componentWasHidden)

        //This shows and hides the UI, giving it time to actually show itself and empty the event dispatch
        //queue. This is required for the setup tab to become current as well as show the custom component we added.
        openApi.flushEventQueue(frame)

        Assert.assertEquals("The setup tab was not selected", setupTabIndex, singlePane.getCurrentGradleTab())

        //now the label should have been made visible then invisible
        Assert.assertTrue(hierarchyAdapter.componentWasShown)
        Assert.assertTrue(hierarchyAdapter.componentWasHidden)
    }

    /**
     * This verifies that you can add a custom tab to the UI. This is a UI test and is kinda tricky. We're going
     * to use a HierarchyListener to see if our tab component is made visible. This will confirm if it was added or not
     * because it must be added to be made visible. To do this, however, we'll need to actually show the UI. All we're
     * really doing here, is adding a tab to the UI, then adding the UI to a frame, then showing the frame so we can
     * then verify that our tab was shown (actually using our tab's component).
     */
    @Test
    void testAddingCustomTab() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;  // Can't run this test in headless mode!
        }

        TestTab testTab = new TestTab()

        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()

        //make sure we haven't been told the component was shown or hidden yet
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasShown)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasHidden)

        //make sure things are initialized properly. These should all be false
        Assert.assertFalse(testTab.nameRetrieved);
        Assert.assertFalse(testTab.informedAboutToShow);
        Assert.assertFalse(testTab.componentCreated);

        int originalCount = singlePane.getGradleTabCount();

        singlePane.addTab(99, testTab) //I don't really care about the index. It should accept a number that is too large and handle it appropriately.

        singlePane.aboutToShow()

        //this still should not show the component (at this point, we're probably more testing that our hierarchyAdapter is working)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasShown)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasHidden)

        //now create a frame, place the UI in it, then show it briefly
        JFrame frame = openApi.open(singlePane)

        String testTabName = "Test Tab"

        //set the test tab as the current tab. This is required to actually show the component.
        int testTabIndex = singlePane.getGradleTabIndex(testTabName)
        Assert.assertTrue("Failed to get index of test tab", testTabIndex != -1)
        singlePane.setCurrentGradleTab(testTabIndex)

        //just to test getGradleTabName, make sure it returns our tab name
        Assert.assertEquals(testTabName, singlePane.getGradleTabName(testTabIndex))

        //to test getGradleTabCount, make sure the tab count went up by 1
        Assert.assertEquals(originalCount + 1, singlePane.getGradleTabCount())

        //still should not show the component (its not yet visible, but is about to be)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasShown)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasHidden)

        //This shows and hides the UI, giving it time to actually show itself and empty the event dispatch
        //queue. This is required for the test tab to become current.
        openApi.flushEventQueue(frame)

        Assert.assertEquals("The test tab was not selected", testTabIndex, singlePane.getCurrentGradleTab())

        //now the label should have been made visible then invisible
        Assert.assertTrue(testTab.hierarchyAdapter.componentWasShown)
        Assert.assertTrue(testTab.hierarchyAdapter.componentWasHidden)

        //at the end, the name should have been queried, we should have been told we were about to shown, and the component should be created
        Assert.assertTrue(testTab.nameRetrieved);
        Assert.assertTrue(testTab.informedAboutToShow);
        Assert.assertTrue(testTab.componentCreated);

        //reset the test tab (resets the listener so we can remove the tab and verify that it no longer shows up, as well as some of our test variables)
        testTab.reset()
        singlePane.removeTab(testTab)

        //I'm going to set the current tab, but this shouldn't do anything because the tab was removed
        singlePane.setCurrentGradleTab(testTabIndex);

        //part of showing the UI is telling it its about to be shown. In this case, nothing should happen
        //related to the test tab. It has been removed
        singlePane.aboutToShow()

        //This shows and hides the UI, giving it time to actually show itself and empty the event
        //dispatch queue. This is required for the test tab to become current (were it still present).
        openApi.flushEventQueue(frame)

        //try to get the test tab
        testTabIndex = singlePane.getGradleTabIndex("Test Tab");
        Assert.assertTrue("Erroneously got index of test tab. It was removed", testTabIndex == -1)

        //we've removed it, so it shouldn't have been polled about or informed of anything
        Assert.assertFalse(testTab.nameRetrieved);
        Assert.assertFalse(testTab.informedAboutToShow);
        Assert.assertFalse(testTab.componentCreated);

        //It was not shown after the reset, these should both be false
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasShown)
        Assert.assertFalse(testTab.hierarchyAdapter.componentWasHidden)
    }

    /**
     * We want to make sure the settings are working correctly here. This is the mechanism that
     * handles saving/restoring the values within the UI and can be stored in different ways
     * depending on how the UI integrated with its parent (its up to whoever implements
     * SettingsNodeVersion1). Here, to spot check that the basics are working, we'll create a
     * UI, set a value, close it, then recreate it using the same settings object. The values
     * should be saved upon close and then restored.
     */
    @Test
    void testSettings() {
        TestSettingsNodeVersion1 settingsNode = new TestSettingsNodeVersion1();

        TestSingleDualPaneUIInteractionVersion1 testSingleDualPaneUIInteractionVersion1 = new TestSingleDualPaneUIInteractionVersion1(new TestAlternateUIInteractionVersion1(), settingsNode);
        SinglePaneUIVersion1 singlePane = null;
        try {
            singlePane = UIFactory.createSinglePaneUI(getClass().getClassLoader(), buildContext.getGradleHomeDir(), testSingleDualPaneUIInteractionVersion1, false);
        } catch (Exception e) {
            throw new AssertionError("Failed to extract single pane: Caused by " + e.getMessage())
        }

        File illegalDirectory = temporaryFolder.testDirectory.file("non-existant").createDir();
        if (illegalDirectory.equals(singlePane.getCurrentDirectory())) {
            throw new AssertionError("Directory already set to 'test' directory. The test is not setup correctly.");
        }

        //this is required to get the ball rolling
        singlePane.aboutToShow();

        //set the current directory after calling aboutToShow (otherwise, it'll stomp over us when it restores its default settings)
        singlePane.setCurrentDirectory(illegalDirectory);

        //close the UI. This saves the current settings.
        singlePane.close();

        //now instantiate it again
        testSingleDualPaneUIInteractionVersion1 = new TestSingleDualPaneUIInteractionVersion1(new TestAlternateUIInteractionVersion1(), settingsNode);
        try {
            singlePane = UIFactory.createSinglePaneUI(getClass().getClassLoader(), buildContext.getGradleHomeDir(), testSingleDualPaneUIInteractionVersion1, false);
        } catch (Exception e) {
            throw new AssertionError("Failed to extract single pane (second time): Caused by " + e.getMessage())
        }

        //this should restore the previous settings
        singlePane.aboutToShow();

        Assert.assertEquals(illegalDirectory, singlePane.getCurrentDirectory());
    }

    /**
     * This tests that the command line altering mechanism works. This adds additional
     * things to the command line being executed. This is used by gradle build systems
     * that need custom, system-specific arguments passed to it that aren't known by gradle
     * proper. For example: you're working with a large project made of MANY subprojects, a
     * custom IDE plugin can track which projects you're focusing on and pass that information
     * to the build system via this mechanism, so it only builds appropriate projects.
     * This is awkward to test because its real use requires a customized build system,
     * so I'm going to pass an argument that is illegal by itself -- meaning no tasks
     * are specified. Then I'll alter the command line by adding an actual task. Then
     * wait for it to complete and verify what was executed
     */
    @Test
    void testCommandLineAlteringListener() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) dualPane.getGradleInterfaceVersion1()

        //this starts the execution queue. This also initiates a refresh that we'll ignore later.
        dualPane.aboutToShow()

        //add a request observer so we can observe when the command is finished. This allows us to
        //see what was actually executed.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.EXECUTION_TYPE)
        gradleInterface.addRequestObserver(testRequestObserver)

        //now that we know that command is illegal by itself, try it again but the listener will append 'build'
        //to the command line which makes it legal (again, we don't really care what we execute.
        TestCommandLineArgumentAlteringListenerVersion1 commandLineArgumentAlteringListener = new TestCommandLineArgumentAlteringListenerVersion1("classes")
        gradleInterface.addCommandLineArgumentAlteringListener(commandLineArgumentAlteringListener)

        //execute this before we do our test. This is not legal by itself. It should fail. That means our
        //test is setup correctly. For example: if someone adds a default task to this project, this will
        //generate NO error and thus, our test will prove nothing. If you get a test failure here, you
        //can try changing the command line to something that's illegal by itself (we don't care what).
        RequestVersion1 request = gradleInterface.executeCommand2("-s", "test command")

        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)

        Assert.assertThat(testRequestObserver.request.getFullCommandLine(), startsWith("-s "))
        Assert.assertThat(testRequestObserver.request.getFullCommandLine(), endsWith(" classes"))

        //make sure it completed execution correctly
        Assert.assertEquals("Execution failed with return code: " + testRequestObserver.result + "\nOutput:\n" + testRequestObserver.output, 0, testRequestObserver.result)

        //the request that was executed should be equal to our original command with our 'altered' command added to it
        Assert.assertNotNull("Missing 'execution completed' request", testRequestObserver.request)

        //just to be paranoid, let's make sure it was actually our request. If this fails, it probably represents something
        //fundamentally flawed with the request or request wrapper mechanism.
        Assert.assertEquals(request, testRequestObserver.request)

        gradleInterface.removeRequestObserver(testRequestObserver)
        gradleInterface.removeCommandLineArgumentAlteringListener(commandLineArgumentAlteringListener)
    }

    /**
     * This tests that getVersion returns the same thing as the jar's suffix.
     * We'll get the gradle jar, then strip off its extension and verify that
     * the jar's name ends with the version number.
     */
    @Test
    void testVersion() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        String version = ((GradleInterfaceVersion2) singlePane.getGradleInterfaceVersion1()).getVersion()

        Assert.assertNotNull("null version number", version)

        Assert.assertFalse("Empty version number", version.trim().equals(""))       //shouldn't be empty

        File gradleJar = ExternalUtility.getGradleJar(buildContext.gradleHomeDir)

        Assert.assertNotNull("Missing gradle jar", gradleJar)                         //we should have a gradle jar

        int indexOfExtension = gradleJar.getName().toLowerCase().lastIndexOf(".jar")  //get the index of its extension

        Assert.assertTrue("Has no '.jar' extension", indexOfExtension != -1)          //it had better have an extension

        String jarName = gradleJar.getName().substring(0, indexOfExtension)              //get its name minus the extension

        assert jarName.endsWith(version)  //the name (minus extension) should end with the version
    }

    /**
     * This is just a spot check that getGradleHomeDirectory works. Its based off
     * of the gradle you're running.
     */
    @Test
    void testGradleHomeDirectory() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()

        Assert.assertEquals(buildContext.gradleHomeDir, singlePane.getGradleHomeDirectory())
    }

    /**
     * This is just a spot check that we can get an instance of the OutputUILord.
     * Other tests cover its functionality more thoroughly. This is just to make sure
     * its working when accessed via the Open API.
     */
    @Test
    void testOutputUILord() {
        SinglePaneUIVersion1 singlePane = openApi.createSinglePaneUI()
        OutputUILordVersion1 outputUILord = singlePane.getOutputLord()
        Assert.assertNotNull(outputUILord)
    }

    /**
     * This tests that you can correctly obtain the number of output tabs from a
     * dual pane UI. This
     */
    @Test
    void testDualPaneOutputPaneNumber() {
        if (java.awt.GraphicsEnvironment.isHeadless()) {
            return;  // Can't run this test in headless mode!
        }

        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()

        //now create a frame, place the UI in it, then show it briefly
        JFrame frame = openApi.open(dualPane)

        //make sure we got something
        Assert.assertNotNull(dualPane)

        //tell it we're about to show it, so it'll create a component
        dualPane.aboutToShow()

        dualPane.refreshTaskTree()

        openApi.flushEventQueue(frame)

        //there should be one opened output tab for the refresh
        Assert.assertEquals(1, dualPane.getNumberOfOpenedOutputTabs())

        dualPane.executeCommand("build", "build")

        openApi.flushEventQueue(frame)

        //there should be 2 opened output tabs. One for refresh, one for build
        Assert.assertEquals(2, dualPane.getNumberOfOpenedOutputTabs())
    }

    /**
     * This tests whether or not a the UI is considered busy. Its busy if its
     * executing a command. To test this, we'll execute a command and verify
     * we're busy. When it finishes, we'll verify we're not longer busy.
     * We'll also check that canClose works properly. If we're busy, calling
     * canClose should prompt the user to confirm closing and return their
     * answer. If we're not busy, it should not prompt the user and return
     * true.
     */
    @Test
    void testBusy() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) dualPane.getGradleInterfaceVersion1()

        //this starts the execution queue. This also initiates a refresh that we'll ignore later.
        dualPane.aboutToShow()

        //add a request observer so we can observe when the command is finished.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.EXECUTION_TYPE)
        gradleInterface.addRequestObserver(testRequestObserver)

        gradleInterface.executeCommand("build", "test command")

        //now that there's a real command in the queue, we should be considered busy
        Assert.assertTrue(dualPane.isBusy())
        Assert.assertTrue(gradleInterface.isBusy())

        //we're busy, we shouldn't be able to close
        TestCloseInteraction testCloseInteraction = new TestCloseInteraction(false)
        Assert.assertFalse(dualPane.canClose(testCloseInteraction))

        //since we just asked to close and we're busy, make sure we prompted the user
        Assert.assertTrue(testCloseInteraction.wasPromptedToConfirmClose)

        testRequestObserver.waitForRequestExecutionComplete(120, TimeUnit.SECONDS)

        Assert.assertThat(testRequestObserver.request.getFullCommandLine(), startsWith("build"))

        //make sure it completed execution correctly
        Assert.assertEquals("Execution failed with return code: " + testRequestObserver.result + "\nOutput:\n" + testRequestObserver.output, 0, testRequestObserver.result)

        //make sure we're not longer considered busy
        Assert.assertFalse(dualPane.isBusy())
        Assert.assertFalse(gradleInterface.isBusy())

        //make sure we can close now
        testCloseInteraction = new TestCloseInteraction(false)
        Assert.assertTrue(dualPane.canClose(testCloseInteraction))

        //since we just asked to close and we're NOT busy, make sure we did NOT prompt the user
        Assert.assertFalse(testCloseInteraction.wasPromptedToConfirmClose)

        gradleInterface.removeRequestObserver(testRequestObserver)
    }

    /**
     * This tests that we can set a custom gradle executor.
     */
    @Test
    void testSettingCustomGradleExecutor() {
        DualPaneUIVersion1 dualPane = openApi.createDualPaneUI()
        GradleInterfaceVersion2 gradleInterface = (GradleInterfaceVersion2) dualPane.getGradleInterfaceVersion1()

        //it should be null by default
        Assert.assertNull(gradleInterface.getCustomGradleExecutable())

        //now let's set it to a custom gradle executable. Actually, we're not going to really get
        //a custom one; we'll use the normal one. Why? Because a real custom one would probably
        //become a pain for maintaining this test. Here, we're interested that the basics are working
        //from an open-api standpoint.
        File gradleExecutor = getCustomGradleExecutable()

        gradleInterface.setCustomGradleExecutable(gradleExecutor)

        //make sure it was set
        Assert.assertEquals(gradleExecutor, gradleInterface.getCustomGradleExecutable())
        Assert.assertEquals(gradleExecutor, dualPane.getCustomGradleExecutable()) //just another way to get it

        //add a request observer so we can observe when the command is finished.
        BlockingRequestObserver testRequestObserver = new BlockingRequestObserver(RequestVersion1.REFRESH_TYPE)
        gradleInterface.addRequestObserver(testRequestObserver)

        //this starts the execution queue
        dualPane.aboutToShow()

        dualPane.refreshTaskTree()

        testRequestObserver.waitForRequestExecutionComplete(80, TimeUnit.SECONDS)

        //make sure it completed execution correctly
        Assert.assertEquals("Execution failed with return code: " + testRequestObserver.result + "\nOutput:\n" + testRequestObserver.output, 0, testRequestObserver.result)

        gradleInterface.removeRequestObserver(testRequestObserver)
    }

    /**
     * This gets a gradle executable. That is, a way to launch gradle (shell script or batch file).
     */
    private File getCustomGradleExecutable() {
        //now let's set it to a custom gradle executable. We'll just point it to the regular
        //gradle file (but it'll be the custom one.
        String name = OperatingSystem.current().getScriptName("bin/gradle");

        File gradleExecutor = new File(buildContext.getGradleHomeDir(), name)

        //make sure the executable exists
        Assert.assertTrue("Missing gradle executable at: " + gradleExecutor, gradleExecutor.exists())

        return gradleExecutor
    }
}

/**
 * Inner class for tracking a component's visiblity has changed.
 * A HierarchyListener is how Swing notifies you that a component's visibility has changed.
 * We'll use it to track if the component was shown and then hidden.
 */
private class TestVisibilityHierarchyListener implements HierarchyListener {
    private boolean componentWasShown = false;
    private boolean componentWasHidden = false;

    void hierarchyChanged(HierarchyEvent e) {
        if ((e.getChangeFlags() & HierarchyEvent.SHOWING_CHANGED) != 0) {
            if (e.getComponent().isShowing()) {
                componentWasShown = true;
            }
            else {
                componentWasHidden = true;
            }
        }
    }
}

/**
 * A class that manages a dummy gradle tab. It just consists of a label,
 *  but tracks that certain fields were called.
 */
class TestTab implements GradleTabVersion1 {
    private JLabel label = new JLabel("Testing Testing 123")
    private TestVisibilityHierarchyListener hierarchyAdapter = new TestVisibilityHierarchyListener()
    private boolean nameRetrieved
    private boolean informedAboutToShow
    private boolean componentCreated

    def TestTab() {
        label.addHierarchyListener(hierarchyAdapter)
    }

    private void reset() {
        label.removeHierarchyListener(hierarchyAdapter)         //remove the existing listener
        hierarchyAdapter = new TestVisibilityHierarchyListener()  //create a new one
        label.addHierarchyListener(hierarchyAdapter)            //add it

        nameRetrieved = false
        informedAboutToShow = false
        componentCreated = false
    }

    String getName() {
        nameRetrieved = true;
        return "Test Tab";
    }

    Component createComponent() {
        componentCreated = true;
        return label;
    }

    void aboutToShow() {
        informedAboutToShow = true;
    }
}

/**
 * Class that tracks whether we were prompted to confirm close. It also returns a specific
 * value to that prompt.
 */
private class TestCloseInteraction implements BasicGradleUIVersion1.CloseInteraction {
    boolean wasPromptedToConfirmClose
    boolean promptResult



    def TestCloseInteraction(promptResult) {
        this.promptResult = promptResult;
    }

    boolean promptUserToConfirmClosingWhileBusy() {
        wasPromptedToConfirmClose = true
        return promptResult
    }
}

/**
 * This appends a specified string to the command line when executing a command.
 */
private class TestCommandLineArgumentAlteringListenerVersion1 implements CommandLineArgumentAlteringListenerVersion1 {
    private final String additionalArguments;

    def TestCommandLineArgumentAlteringListenerVersion1(additionalArguments) {
        this.additionalArguments = additionalArguments;
    }

    String getAdditionalCommandLineArguments(String commandLineArguments) {
        if (commandLineArguments.startsWith("-s ")) {  //we're only interested in altering this one command
            return additionalArguments;
        }

        return null;
    }
}
