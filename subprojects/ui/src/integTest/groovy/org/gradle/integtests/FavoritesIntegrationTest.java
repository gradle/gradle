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

import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.foundation.BuildInformation;
import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.TestUtility;
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.gradle.test.fixtures.file.TestNameTestDirectoryProvider;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;

import javax.swing.filechooser.FileFilter;
import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Performs integration tests on favorite tasks.
 */
public class FavoritesIntegrationTest {
    @Rule
    public final TestNameTestDirectoryProvider tempDir = new TestNameTestDirectoryProvider();
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
        Project subsubProject = TestUtility.createMockProject(context, "mysubsubproject", "filepath3", 2, null, new Task[]{subsubCompileTask, subsubLibTask, subsubDocTask}, null, (Project[]) null);

        Task subCompileTask1 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask1 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask1 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject1 = TestUtility.createMockProject(context, "mysubproject1", "filepath2a", 1, new Project[]{subsubProject}, new Task[]{subCompileTask1, subLibTask1, subDocTask1}, null,
                (Project[]) null);

        Task subCompileTask2 = TestUtility.createTask(context, "compile", "compile description");
        Task subLibTask2 = TestUtility.createTask(context, "lib", "lib description");
        Task subDocTask2 = TestUtility.createTask(context, "doc", "doc description");
        Project subProject2 = TestUtility.createMockProject(context, "mysubproject2", "filepath2b", 1, null, new Task[]{subCompileTask2, subLibTask2, subDocTask2}, null, (Project[]) null);

        Project rootProject = TestUtility.createMockProject(context, "myrootproject", "filepath1", 0, new Project[]{subProject1, subProject2}, null, null, (Project[]) null);

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
        mySubSubProjectCompile = buildInformation.getTaskFromFullPath("myrootproject:mysubproject1:mysubsubproject:compile");
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

        //add some tasks
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);
        FavoriteTask favoriteTask2 = originalEditor.addFavorite(mySubSubProjectLib, false);

        //now change the display name so its not the same as the full name, just so know each field is working.
        originalEditor.editFavorite(favoriteTask1, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = "favorite 1";
                return true;
            }

            public void reportError(String error) {
                throw new AssertionError("Unexpected error");
            }
        });

        //make sure they were added properly
        FavoriteTask originalFavoriteTask1 = originalEditor.getFavoriteTasks().get(0);
        assertFavorite(originalFavoriteTask1, "mysubproject1:compile", "favorite 1", true);

        FavoriteTask originalFavoriteTask2 = originalEditor.getFavoriteTasks().get(1);
        assertFavorite(originalFavoriteTask2, "mysubproject1:mysubsubproject:lib", "mysubproject1:mysubsubproject:lib", false);

        File file = tempDir.createFile("fred.favorite-tasks");
        originalEditor.exportToFile(new TestUtility.TestExportInteraction(file, true)); //confirm overwrite because the above function actually creates the file.

        FavoritesEditor newEditor = new FavoritesEditor();
        newEditor.importFromFile(new TestUtility.TestImportInteraction(file));

        //make sure they're the same
        FavoriteTask readInFavoriteTask1 = originalEditor.getFavoriteTasks().get(0);
        assertFavorite(readInFavoriteTask1, originalFavoriteTask1);

        FavoriteTask readInFavoriteTask2 = originalEditor.getFavoriteTasks().get(1);
        assertFavorite(readInFavoriteTask2, originalFavoriteTask2);
    }

    /**
     * This verifies that the serialization mechanism corrects the extension so that it is correct. We'll save a file with the wrong extension. The save mechanism should save it with the correct
     * extension appended to the end (leaving the wrong extension in tact, just not at the end).
     */
    @Test
    public void testEnsureFileHasCorrectExtension() {
        FavoritesEditor originalEditor = new FavoritesEditor();

        Assert.assertTrue(originalEditor.getFavoriteTasks().isEmpty());

        //add a favorite
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);

        //specify a wrong extension. It should actually end in ".favorite-tasks"
        File incorrectFile = tempDir.createFile("fred.wrong");
        File correctFile = new File(incorrectFile.getParentFile(), incorrectFile.getName() + ".favorite-tasks");

        //Make sure the correct file doesn't already exist before we've even done our test. This is highly unlikely, but it might happen.
        //Technically, I should place these in a new temporary directory, but I didn't want the hassle of cleanup.
        if (correctFile.exists()) {
            throw new AssertionError("'correct' file already exists. This means this test WILL succeed but perhaps not for the correct reasons.");
        }

        //do the export
        originalEditor.exportToFile(new TestUtility.TestExportInteraction(incorrectFile, true)); //confirm overwrite because the above function actually creates the file.

        //it should have been saved to the correct file
        if (!correctFile.exists()) {
            throw new AssertionError("failed to correct the file name. Expected it to be saved to '" + correctFile.getAbsolutePath() + "'");
        }

        //now read in the file to verify it actually worked.
        FavoritesEditor newEditor = new FavoritesEditor();
        newEditor.importFromFile(new TestUtility.TestImportInteraction(correctFile));

        FavoriteTask readInFavoriteTask = newEditor.getFavoriteTasks().get(0);
        assertFavorite(readInFavoriteTask, favoriteTask1);
    }

    private void assertFavorite(FavoriteTask favoriteTaskToTest, String expectedFullTaskName, String expectedDisplayName, boolean expectedAlwaysShowOutput) {
        Assert.assertEquals(expectedFullTaskName, favoriteTaskToTest.getFullCommandLine());
        Assert.assertEquals(expectedDisplayName, favoriteTaskToTest.getDisplayName());
        Assert.assertEquals(expectedAlwaysShowOutput, favoriteTaskToTest.alwaysShowOutput());
    }

    private void assertFavorite(FavoriteTask favoriteTaskToTest, FavoriteTask expectedFavoriteTask) {
        assertFavorite(favoriteTaskToTest, expectedFavoriteTask.getFullCommandLine(), expectedFavoriteTask.getDisplayName(), expectedFavoriteTask.alwaysShowOutput());
    }

    /**
     * This confirms that overwriting a file requires confirmation. We'll create a file (just by creating a temporary file), then try to save to it.
     */
    @Test
    public void testConfirmOverwrite() {  //we should be prompted to confirm overwriting an existing file.

        FavoritesEditor originalEditor = new FavoritesEditor();

        Assert.assertTrue(originalEditor.getFavoriteTasks().isEmpty());

        //add a favorite
        FavoriteTask favoriteTask1 = originalEditor.addFavorite(mySubProject1Comple, true);

        File file = tempDir.createFile("test.favorite-tasks");

        //make sure the file exists, so we know our save will be overwritting something.
        Assert.assertTrue(file.exists());

        long originalSize = file.length();

        TestOverwriteConfirmExportInteraction exportInteraction = new TestOverwriteConfirmExportInteraction(file, false);

        //do the export
        originalEditor.exportToFile(exportInteraction);

        //make sure we were prompted to confirm overwriting
        Assert.assertTrue(exportInteraction.wasConfirmed);

        //make sure the size didn't change. This means we didn't write to it.
        Assert.assertEquals(originalSize, file.length());
    }

    /**
     * This exists solely so we can track if confirmOverwritingExistingFile was called.
     */
    private class TestOverwriteConfirmExportInteraction extends TestUtility.TestExportInteraction {
        public boolean wasConfirmed;

        private TestOverwriteConfirmExportInteraction(File file, boolean confirmOverwrite) {
            super(file, confirmOverwrite);
        }

        public File promptForFile(FileFilter fileFilters) {
            if (wasConfirmed) {
                //once we confirm it, just return null.
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
        public boolean confirmOverwritingExistingFile(File file) {
            wasConfirmed = true;
            return false;
        }
    }

    /**
     * This tests duplicating a single favorite. First, we'll create some, then duplicate one.
     */
    @Test
    public void testDuplicateSingleFavorite() {
        FavoritesEditor editor = new FavoritesEditor();

        //add some tasks
        FavoriteTask favoriteTask1 = editor.addFavorite(mySubProject1Comple, true);
        FavoriteTask favoriteTask2 = editor.addFavorite(mySubSubProjectLib, false);
        FavoriteTask favoriteTask3 = editor.addFavorite(mySubSubProjectDoc, false);

        //now change the display names and the alwaysShowOutput field, just so we can verify that all fields are copied.
        editFavorite(editor, favoriteTask1, "name1", false);
        editFavorite(editor, favoriteTask2, "name2", true);
        editFavorite(editor, favoriteTask3, "name3", false);

        //duplicate a single task
        FavoriteTask favoriteTask4 = editor.duplicateFavorite(favoriteTask1, new TestEditFavoriteInteraction("name4", "command4"));
        Assert.assertNotNull(favoriteTask4);
        Assert.assertEquals("command4", favoriteTask4.getFullCommandLine());
        Assert.assertEquals("name4", favoriteTask4.getDisplayName());
        Assert.assertEquals(favoriteTask1.alwaysShowOutput(), favoriteTask4.alwaysShowOutput());

        //there should be 4 tasks now
        Assert.assertEquals(4, editor.getFavoriteTasks().size());

        //now duplicate another one
        FavoriteTask favoriteTask5 = editor.duplicateFavorite(favoriteTask2, new TestEditFavoriteInteraction("name5", "command5"));
        Assert.assertNotNull(favoriteTask5);
        Assert.assertEquals("command5", favoriteTask5.getFullCommandLine());
        Assert.assertEquals("name5", favoriteTask5.getDisplayName());
        Assert.assertEquals(favoriteTask2.alwaysShowOutput(), favoriteTask5.alwaysShowOutput());

        //there should be 5 tasks now
        Assert.assertEquals(5, editor.getFavoriteTasks().size());
    }

    /**
     * This tests duplicating multiple favorites at once. First, we'll create some, then duplicate them.
     */
    @Test
    public void testDuplicatingMultipleFavorites() {
        FavoritesEditor editor = new FavoritesEditor();

        //add some tasks
        FavoriteTask favoriteTask1 = editor.addFavorite(mySubProject1Comple, true);
        FavoriteTask favoriteTask2 = editor.addFavorite(mySubSubProjectLib, false);
        FavoriteTask favoriteTask3 = editor.addFavorite(mySubSubProjectDoc, false);

        //now change the display names and the alwaysShowOutput field, just so we can verify that all fields are copied.
        editFavorite(editor, favoriteTask1, "name1", false);
        editFavorite(editor, favoriteTask2, "name2", true);
        editFavorite(editor, favoriteTask3, "name3", false);

        //get the ones to dupicate in a list
        List<FavoriteTask> tasksToCopy = new ArrayList<FavoriteTask>();
        tasksToCopy.add(favoriteTask1);
        tasksToCopy.add(favoriteTask2);

        //now perform the duplication
        editor.duplicateFavorites(tasksToCopy, new TestEditFavoriteInteraction(new NameAndCommand("newname1", "newcommand1"),
                new NameAndCommand("newname2", "newcommand2")));

        //there should be 5 tasks now
        Assert.assertEquals(5, editor.getFavoriteTasks().size());

        //the 4th one (3 from index 0) should be the same as the first one
        FavoriteTask favoriteTask4 = editor.getFavoriteTasks().get(3);

        Assert.assertNotNull(favoriteTask4);
        Assert.assertEquals("newcommand1", favoriteTask4.getFullCommandLine());
        Assert.assertEquals("newname1", favoriteTask4.getDisplayName());
        Assert.assertEquals(favoriteTask1.alwaysShowOutput(), favoriteTask4.alwaysShowOutput());

        //the 5th one (4 from index 0) should be the same as the second one
        FavoriteTask favoriteTask5 = editor.getFavoriteTasks().get(4);
        Assert.assertNotNull(favoriteTask5);
        Assert.assertEquals("newcommand2", favoriteTask5.getFullCommandLine());
        Assert.assertEquals("newname2", favoriteTask5.getDisplayName());
        Assert.assertEquals(favoriteTask2.alwaysShowOutput(), favoriteTask5.alwaysShowOutput());
    }

    /**
     * This tests duplicating multiple favorites at once, but we cancel out after duplicating one. We want to make sure that it doesn't continue to create the others. First, we'll create some, then
     * duplicate them.
     */
    @Test
    public void testDuplicatingMultipleFavoritesAndCanceling() {

        FavoritesEditor editor = new FavoritesEditor();

        //add some tasks
        FavoriteTask favoriteTask1 = editor.addFavorite(mySubProject1Comple, true);
        FavoriteTask favoriteTask2 = editor.addFavorite(mySubSubProjectLib, false);
        FavoriteTask favoriteTask3 = editor.addFavorite(mySubSubProjectDoc, false);

        //now change the display names and the alwaysShowOutput field, just so we can verify that all fields are copied.
        editFavorite(editor, favoriteTask1, "name1", false);
        editFavorite(editor, favoriteTask2, "name2", true);
        editFavorite(editor, favoriteTask3, "name3", false);

        //get the ones to duplicate in a list
        List<FavoriteTask> tasksToCopy = new ArrayList<FavoriteTask>();
        tasksToCopy.add(favoriteTask1);
        tasksToCopy.add(favoriteTask2);

        //now perform the duplication, we only pass in one NameAndCommand but we're editing 2. This makes it cancel the second one.
        editor.duplicateFavorites(tasksToCopy, new TestEditFavoriteInteraction(new NameAndCommand("newname1", "newcommand1")));

        //there should be 4 tasks now
        Assert.assertNotSame("Failed to cancel", 5, editor.getFavoriteTasks().size()); //this just provides a better error if this fails to cancel
        Assert.assertEquals(4, editor.getFavoriteTasks().size());

        //the 4th one (3 from index 0) should be the same as the first one
        FavoriteTask favoriteTask4 = editor.getFavoriteTasks().get(3);

        Assert.assertNotNull(favoriteTask4);
        Assert.assertEquals("newcommand1", favoriteTask4.getFullCommandLine());
        Assert.assertEquals("newname1", favoriteTask4.getDisplayName());
        Assert.assertEquals(favoriteTask1.alwaysShowOutput(), favoriteTask4.alwaysShowOutput());
    }

    private class TestEditFavoriteInteraction implements FavoritesEditor.EditFavoriteInteraction {
        private List<NameAndCommand> values = new ArrayList<NameAndCommand>();

        private TestEditFavoriteInteraction(NameAndCommand... values) {
            if (values != null) {
                this.values = new ArrayList<NameAndCommand>(Arrays.asList(values));   //making a new ArrayList because Arrays.asList makes it unmodifiable.
            }
        }

        private TestEditFavoriteInteraction(String displayName, String fullCommandLine) {
            values.add(new NameAndCommand(displayName, fullCommandLine));
        }

        public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
            if (values.isEmpty()) {
                return false;   //if we have no more choices, that simulates the user canceling
            }

            NameAndCommand nameAndCommand = values.remove(0);

            favoriteTask.displayName = nameAndCommand.displayName;
            favoriteTask.fullCommandLine = nameAndCommand.fullCommandLine;

            return true;
        }

        public void reportError(String error) {
            throw new AssertionError("Unexpected error; " + error);
        }
    }

    /**
     * wrapper class to hold a display name and a full command line
     */
    private class NameAndCommand {
        String displayName;
        String fullCommandLine;

        private NameAndCommand(String displayName, String fullCommandLine) {
            this.displayName = displayName;
            this.fullCommandLine = fullCommandLine;
        }
    }

    /**
     * This sets the display name of the favorite task to the specified new name.
     */
    private void editFavorite(FavoritesEditor editor, FavoriteTask favoriteTask, final String newDisplayName, final boolean newAlwaysShowOutput) {
        editor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = newDisplayName;
                favoriteTask.alwaysShowOutput = newAlwaysShowOutput;
                return true;
            }

            public void reportError(String error) {
                throw new AssertionError("Unexpected error");
            }
        });

        Assert.assertEquals(newDisplayName, favoriteTask.getDisplayName());
    }
}