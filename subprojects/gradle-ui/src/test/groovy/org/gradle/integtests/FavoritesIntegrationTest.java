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
package org.gradle.integtests;

import junit.framework.AssertionFailedError;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.foundation.BuildInformation;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.TestUtility;
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import javax.swing.filechooser.FileFilter;
import java.io.File;

/**
 * Performs integration tests on favorite tasks.
 *
 * @author mhunsicker
 */
public class FavoritesIntegrationTest {
    private BuildInformation buildInformation;

    private ProjectView myRootProject;

    private ProjectView mySubProject1;
    private TaskView mySubProject1Comple;
    private TaskView mySubProject1Lib;
    private TaskView mySubProject1Doc;

    private ProjectView mySubSubProject;
    private TaskView mySubSubProjectCompile;
    private TaskView mySubSubProjectLib;
    private TaskView mySubSubProjectDoc;

    private ProjectView mySubProject2;
    private TaskView mySubProject2Lib;
    private TaskView mySubProject2doc;
    private TaskView mySubProject2Compile;
    private JUnit4Mockery context;

    @Before
    public void setUp() throws Exception {
        context = new JUnit4Mockery();

        Task subsubCompileTask = TestUtility.createTask(context, "compile", "compile description");
        Task subsubLibTask = TestUtility.createTask(context, "lib", "lib description");
        Task subsubDocTask = TestUtility.createTask(context, "doc", "doc description");
        Project subsubProject = TestUtility.createMockProject(context, "mysubsubproject", "filepath3", 2, null,
                new Task[]{subsubCompileTask, subsubLibTask, subsubDocTask}, null, (Project[]) null);

        Task subCompileTask1 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask1 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask1 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject1 = TestUtility.createMockProject(context, "mysubproject1", "filepath2a", 1,
                new Project[]{subsubProject}, new Task[]{subCompileTask1, subLibTask1, subDocTask1}, null,
                (Project[]) null);

        Task subCompileTask2 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask2 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask2 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject2 = TestUtility.createMockProject(context, "mysubproject2", "filepath2b", 1, null,
                new Task[]{subCompileTask2, subLibTask2, subDocTask2}, null, (Project[]) null);

        Project rootProject = TestUtility.createMockProject(context, "myrootproject", "filepath1", 0,
                new Project[]{subProject1, subProject2}, null, null, (Project[]) null);

        buildInformation = new BuildInformation(rootProject);

        //now get the converted objects to simplify our matching
        myRootProject = buildInformation.getProjectFromFullPath("myrootproject");
        Assert.assertNotNull(myRootProject);
        mySubProject1 = buildInformation.getProjectFromFullPath("myrootproject:mysubproject1");
        Assert.assertNotNull(mySubProject1);
        mySubProject1Comple = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:compile");
        Assert.assertNotNull(mySubProject1Comple);
        mySubProject1Lib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:lib");
        Assert.assertNotNull(mySubProject1Lib);
        mySubProject1Doc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:doc");
        Assert.assertNotNull(mySubProject1Doc);
        mySubSubProject = buildInformation.getProjectFromFullPath("myrootproject:mysubproject1:mysubsubproject");
        Assert.assertNotNull(mySubSubProject);
        mySubSubProjectCompile = buildInformation.getTaskFromFullPath(
                "myrootproject:mysubproject1:mysubsubproject:compile");
        Assert.assertNotNull(mySubSubProjectCompile);
        mySubSubProjectLib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:mysubsubproject:lib");
        Assert.assertNotNull(mySubSubProjectLib);
        mySubSubProjectDoc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:mysubsubproject:doc");
        Assert.assertNotNull(mySubSubProjectDoc);
        mySubProject2 = buildInformation.getProjectFromFullPath("myrootproject:mysubproject2");
        Assert.assertNotNull(mySubProject2);
        mySubProject2Compile = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:compile");
        Assert.assertNotNull(mySubProject2Compile);
        mySubProject2Lib = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:lib");
        Assert.assertNotNull(mySubProject2Lib);
        mySubProject2doc = buildInformation.getTaskFromFullPath("myrootproject:mysubproject2:doc");
        Assert.assertNotNull(mySubProject2doc);
    }

    /**
     * This creates favorites, saves them to a file, then reads them from that file.
     */
    @Test
    public void testSavingRestoringFavorites() {
        FavoritesEditor originalEditor = new FavoritesEditor();

        Assert.assertTrue(originalEditor.getFavoriteTasks().isEmpty());

        //add two tasks
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);
        FavoriteTask favoriteTask2 = originalEditor.addFavorite(mySubSubProjectLib, false);

        //now change the display name so its not the same as the full name, just so know each field is working.
        originalEditor.editFavorite(favoriteTask1, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = "favorite 1";
                return true;
            }

            public void reportError(String error) {
                throw new AssertionFailedError("Unexpected error");
            }
        });

        //make sure they were added properly
        FavoriteTask originalFavoriteTask1 = originalEditor.getFavoriteTasks().get(0);
        assertFavorite(originalFavoriteTask1, "mysubproject1:compile", "favorite 1", true);

        FavoriteTask originalFavoriteTask2 = originalEditor.getFavoriteTasks().get(1);
        assertFavorite(originalFavoriteTask2, "mysubproject1:mysubsubproject:lib", "mysubproject1:mysubsubproject:lib",
                false);

        File file = TestUtility.createTemporaryFile("fred", ".favorite-tasks");
        file.deleteOnExit();
        originalEditor.exportToFile(new TestUtility.TestExportInteraction(file,
                true)); //confirm overwrite because the above function actually creates the file.

        FavoritesEditor newEditor = new FavoritesEditor();
        newEditor.importFromFile(new TestUtility.TestImportInteraction(file));

        //make sure they're the same
        FavoriteTask readInFavoriteTask1 = originalEditor.getFavoriteTasks().get(0);
        assertFavorite(readInFavoriteTask1, originalFavoriteTask1);

        FavoriteTask readInFavoriteTask2 = originalEditor.getFavoriteTasks().get(1);
        assertFavorite(readInFavoriteTask2, originalFavoriteTask2);
    }

    /**
     * This verifies that the serialization mechnanism corrects the extension so that it is correct. We'll save a file
     * with the wrong extension. The save mechanism should save it with the correct extension appended to the end
     * (leaving the wrong extension in tact, just not at the end).
     */
    @Test
    public void testEnsureFileHasCorrectExtension() {
        FavoritesEditor originalEditor = new FavoritesEditor();

        Assert.assertTrue(originalEditor.getFavoriteTasks().isEmpty());

        //add a favorite
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);

        File incorrectFile = TestUtility.createTemporaryFile("fred",
                ".wrong");  //specify a wrong extension. It should actually end in ".favorite-tasks"
        incorrectFile.deleteOnExit();
        File correctFile = new File(incorrectFile.getParentFile(), incorrectFile.getName() + ".favorite-tasks");

        //Make sure the correct file doesn't already exist before we've even done our test. This is highly unlikely, but it might happen.
        //Technically, I should place these in a new temporary directory, but I didn't want the hassle of cleanup.
        if (correctFile.exists()) {
            throw new AssertionFailedError(
                    "'correct' file already exists. This means this test WILL succeed but perhaps not for the correct reasons.");
        }

        correctFile.deleteOnExit();

        //do the export
        originalEditor.exportToFile(new TestUtility.TestExportInteraction(incorrectFile,
                true)); //confirm overwrite because the above function actually creates the file.

        //it should have been saved to the correct file
        if (!correctFile.exists()) {
            throw new AssertionFailedError(
                    "failed to correct the file name. Expected it to be saved to '" + correctFile.getAbsolutePath()
                            + "'");
        }

        //now read in the file to verify it actually worked.
        FavoritesEditor newEditor = new FavoritesEditor();
        newEditor.importFromFile(new TestUtility.TestImportInteraction(correctFile));

        FavoriteTask readInFavoriteTask = newEditor.getFavoriteTasks().get(0);
        assertFavorite(readInFavoriteTask, favoriteTask1);
    }

    private void assertFavorite(FavoriteTask favoriteTaskToTest, String expectedFullTaskName,
                                String expectedDisplayName, boolean expectedAlwaysShowOutput) {
        Assert.assertEquals(expectedFullTaskName, favoriteTaskToTest.getFullCommandLine());
        Assert.assertEquals(expectedDisplayName, favoriteTaskToTest.getDisplayName());
        Assert.assertEquals(expectedAlwaysShowOutput, favoriteTaskToTest.alwaysShowOutput());
    }

    private void assertFavorite(FavoriteTask favoriteTaskToTest, FavoriteTask expectedFavoriteTask) {
        assertFavorite(favoriteTaskToTest, expectedFavoriteTask.getFullCommandLine(),
                expectedFavoriteTask.getDisplayName(), expectedFavoriteTask.alwaysShowOutput());
    }

    /**
     * This confirms that overwriting a file requires confirmation. We'll create a file (just by creating a temporary
     * file), then try to save to it.
     */
    @Test
    public void testConfirmOverwrite() {  //we should be prompted to confirm overwriting an existing file.

        FavoritesEditor originalEditor = new FavoritesEditor();

        Assert.assertTrue(originalEditor.getFavoriteTasks().isEmpty());

        //add a favorite
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);

        File file = TestUtility.createTemporaryFile("confirm-overwrite", ".favorite-tasks");
        file.deleteOnExit();

        //make sure the file exists, so we know our save will be overwritting something.
        Assert.assertTrue(file.exists());

        long originalSize = file.length();

        TestOverwriteConfirmExportInteraction exportInteraction = new TestOverwriteConfirmExportInteraction(file,
                false);

        //do the export
        originalEditor.exportToFile(exportInteraction);

        //make sure we were prompted to confirm overwriting
        Assert.assertTrue(exportInteraction.wasConfirmed);

        //make sure the size didn't change. This means we didn't write to it.
        Assert.assertEquals(originalSize, file.length());
    }

    /**
     * This exists soley so we can track if confirmOverwritingExisingFile was called.
     */
    private class TestOverwriteConfirmExportInteraction extends TestUtility.TestExportInteraction {
        public boolean wasConfirmed;

        private TestOverwriteConfirmExportInteraction(File file, boolean confirmOverwrite) {
            super(file, confirmOverwrite);
        }

        public File promptForFile(FileFilter fileFilters) {
            if (wasConfirmed)   //once we confirm it, just return null.
            {
                return null;
            }

            return super.promptForFile(fileFilters);
        }

        /**
         * The file already exists. Confirm whether or not you want to overwrite it.
         *
         * @param file the file in question
         * @return true to overwrite it, false not to.
         */
        @Override
        public boolean confirmOverwritingExisingFile(File file) {
            wasConfirmed = true;
            return false;
        }
    }
}