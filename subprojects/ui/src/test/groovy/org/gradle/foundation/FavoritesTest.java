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
package org.gradle.foundation;

import junit.framework.TestCase;
import org.gradle.api.Project;
import org.gradle.api.Task;
import org.gradle.gradleplugin.foundation.favorites.FavoriteTask;
import org.gradle.gradleplugin.foundation.favorites.FavoritesEditor;
import org.jmock.Expectations;
import org.jmock.integration.junit4.JUnit4Mockery;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

/**
 * Tests aspects of favorite tasks and the favorites editor.
 */
public class FavoritesTest extends TestCase {
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
     * This tests adding a favorite task. We'll verify that we get notified and that the task is added properly.
     */
    @Test
    public void testAddingFavorites() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        editor.addFavoriteTasksObserver(observer, false);

        editor.addFavorite(mySubProject1Comple, true);

        context.assertIsSatisfied();

        //make sure it was added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());

        //now add another one, this time set alwaysShowOutput to false
        context.checking(new Expectations() {{
            one(observer)
                    .favoritesChanged(); //reset our favorites changed notification so we know we're getting another one (I don't want to just verify that we got 2 messages. I want to make sure they arrived at the correct time.
        }});

        editor.addFavorite(mySubSubProjectDoc, false);

        context.assertIsSatisfied();

        //make sure it was added properly
        favoriteTask = editor.getFavoriteTasks().get(1);
        Assert.assertEquals("mysubproject1:mysubsubproject:doc", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:mysubsubproject:doc", favoriteTask.getFullCommandLine());
        Assert.assertFalse(favoriteTask.alwaysShowOutput());
    }

    /**
     * Tests removing a favorite. We add one, make sure its right, then remove it and make sure that it goes away as
     * well as that we're notified.
     */
    @Test
    public void testRemovingFavorites() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        editor.addFavorite(mySubProject1Comple, true);

        //make sure it was added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());

        //create an observer so we can make sure we're notified of the deletion.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        editor.addFavoriteTasksObserver(observer, false);

        //now remove the task
        List<FavoriteTask> tasks = new ArrayList<FavoriteTask>();
        tasks.add(favoriteTask);
        editor.removeFavorites(tasks);

        //make sure we were notified
        context.assertIsSatisfied();

        //there shouldn't be any more favorites.
        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());
    }

    /**
     * This tests removing a favorite that isn't in our favorites editor. We just want to make sure we're not notified
     * of a change and that this doesn't blow up.
     */
    public void testRemovingNonExistantFavorite() {
        //remove a task that doesn't exist. We should NOT be notified and it should not blow up

        FavoritesEditor otherEditor = new FavoritesEditor();

        Assert.assertTrue(otherEditor.getFavoriteTasks().isEmpty());

        otherEditor.addFavorite(mySubProject1Comple, true);

        FavoriteTask favoriteTask = otherEditor.getFavoriteTasks().get(0);

        //create another editor. This is the one we're really interested in.
        FavoritesEditor interestedEditor = new FavoritesEditor();

        //create an observer so we can make sure we're NOT notified of the deletion. We won't assign it any expectations.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);

        interestedEditor.addFavoriteTasksObserver(observer, false);

        //now remove the task
        List<FavoriteTask> tasks = new ArrayList<FavoriteTask>();
        tasks.add(favoriteTask);
        interestedEditor.removeFavorites(tasks);

        //it should still exist in the original
        Assert.assertEquals(1, otherEditor.getFavoriteTasks().size());

        //nothing exists in the new one.
        Assert.assertTrue(interestedEditor.getFavoriteTasks().isEmpty());
    }

    /**
     * Here we edit a favorite. We want to make sure that we get notified of the edit and that we the edited values are
     * stored properly. We'll add a favorite, then we perform an edit. Lastly, we verify our values. Notice that we're
     * going to change the task's full name. This should update the task inside the favorite.
     */
    @Test
    public void testEditingFavorite() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        editor.addFavorite(mySubProject1Comple, true);

        //make sure it was added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());

        //create an observer so we can make sure we're notified of the edit.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        editor.addFavoriteTasksObserver(observer, false);

        //now perform the edit.
        editor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.alwaysShowOutput = false;
                favoriteTask.displayName = "newname";
                favoriteTask.fullCommandLine
                        = "myrootproject:mysubproject1:mysubsubproject:lib";   //change the task's full name
                return true;
            }

            public void reportError(String error) {
                throw new AssertionError("unexpected error: " + error);
            }
        });

        //make sure we were notified
        context.assertIsSatisfied();

        //make sure the settings were changed
        favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("newname", favoriteTask.getDisplayName());
        Assert.assertEquals("myrootproject:mysubproject1:mysubsubproject:lib", favoriteTask.getFullCommandLine());
        Assert.assertTrue(!favoriteTask.alwaysShowOutput());
    }

    /**
     * This edits a favorite, but specifically, we change the task's full name to something that doesn't exist. We don't
     * want this to be an error. Maybe the task is temporarily unavailable because of a compile error. We shouldn't stop
     * everything or throw it away just because the task isn't present. The UI should provide some indication of this
     * however.
     */
    @Test
    public void testChangingFullNameToNonExistantTask() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        editor.addFavorite(mySubProject1Comple, true);

        //make sure it was added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());

        //create an observer so we can make sure we're notified of the edit.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        editor.addFavoriteTasksObserver(observer, false);

        //now perform the edit.
        editor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = "newname";
                favoriteTask.fullCommandLine = "nonexistanttask";   //change the task's full name
                return true;
            }

            public void reportError(String error) {
                throw new AssertionError("unexpected error: " + error);
            }
        });

        //make sure we were notified
        context.assertIsSatisfied();

        //make sure the settings were changed
        favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("newname", favoriteTask.getDisplayName());
        Assert.assertEquals("nonexistanttask", favoriteTask.getFullCommandLine());
        Assert.assertFalse(!favoriteTask.alwaysShowOutput());

        //now change the full name back. Make sure the task is changed back.

        //reset our expectations. We'll get notified again.
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        //now perform the edit.
        editor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = "newname";
                favoriteTask.fullCommandLine = "mysubproject1:compile";   //change the task's full name
                return true;
            }

            public void reportError(String error) {
                throw new AssertionError("unexpected error: " + error);
            }
        });

        //make sure we were notified
        context.assertIsSatisfied();

        //make sure the settings were changed
        favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("newname", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertFalse(!favoriteTask.alwaysShowOutput());
    }

    /**
     * This edits a favorite and cancels. We want to make sure that none of our changes during the editing are saved.
     */
    @Test
    public void testCancelingEditingFavorite() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        editor.addFavorite(mySubProject1Comple, true);

        //make sure it was added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());

        //create an observer so we can make sure we're NOT notified of the edit. We'll provide no expectations for this mock object.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);

        editor.addFavoriteTasksObserver(observer, false);

        //now perform the edit, but cancel out.
        editor.editFavorite(favoriteTask, new FavoritesEditor.EditFavoriteInteraction() {
            public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
                favoriteTask.displayName = "newname";
                favoriteTask.fullCommandLine = "nonexistanttask";   //change the task's full name
                favoriteTask.alwaysShowOutput = !favoriteTask.alwaysShowOutput;
                return false;  //return false to cancel!
            }

            public void reportError(String error) {
                throw new AssertionError("unexpected error: " + error);
            }
        });

        //make sure nothing was changed
        favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getDisplayName());
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());
        Assert.assertTrue(favoriteTask.alwaysShowOutput());
    }

    /**
     * This edits a favorite so the task is the same as an existing favorite. This doesn't make any sense to have two of
     * these. We're expecting an error from this.
     */
    @Test
    public void testEditingFavoriteFullNameAlreadyExists() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        //add two tasks
        editor.addFavorite(mySubProject1Comple, true);
        editor.addFavorite(mySubSubProjectLib, true);

        //make sure they were added properly
        FavoriteTask favoriteTask1 = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask1.getFullCommandLine());

        FavoriteTask favoriteTask2 = editor.getFavoriteTasks().get(1);
        Assert.assertEquals("mysubproject1:mysubsubproject:lib", favoriteTask2.getFullCommandLine());

        //now perform the actual edit.
        editExpectingNoError(editor, favoriteTask1, "new name", favoriteTask2.getFullCommandLine());
    }

    /**
     * This edits the favorite and expects NO error. It makes sure the error was received and that the original task was
     * not altered.
     */
    private void editExpectingNoError(FavoritesEditor editor, FavoriteTask favoriteTaskToEdit, String newName,
                                    String newFullName) {
        String originalFullName = favoriteTaskToEdit.getFullCommandLine();

        //create an observer so we can make sure we're notified of the edit.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});

        editor.addFavoriteTasksObserver(observer, false);

        //now perform the edit.
        ValidationErrorTestEditFavoriteInteraction interaction = new ValidationErrorTestEditFavoriteInteraction(newName,
                newFullName);
        editor.editFavorite(favoriteTaskToEdit, interaction);

        //make sure we did get an error message.
        Assert.assertFalse(interaction.receivedErrorMessage);

        //make sure the settings were changed. We'll go by what the editor has, not just our local favoriteTaskToEdit.
        favoriteTaskToEdit = editor.getFavorite(originalFullName);
        Assert.assertNull(favoriteTaskToEdit);   //the original name should no longer be present

        favoriteTaskToEdit = editor.getFavorite(newFullName);
        Assert.assertNotNull(favoriteTaskToEdit);   //the new name should be present

        Assert.assertEquals(newName, favoriteTaskToEdit.getDisplayName());
        Assert.assertEquals(newFullName, favoriteTaskToEdit.getFullCommandLine());
    }


    /**
     * This edits the favorite and expects an error. It makes sure the error was recieved and that the original task was
     * not altered.
     */
    private void editExpectingError(FavoritesEditor editor, FavoriteTask favoriteTaskToEdit, String newName,
                                    String newFullName) {
        String originalDisplayName = favoriteTaskToEdit.getDisplayName();
        String originalFullName = favoriteTaskToEdit.getFullCommandLine();

        //create an observer so we can make sure we're NOT notified of the edit. It's going to generate an error and we'll cancel.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);

        editor.addFavoriteTasksObserver(observer, false);

        //now perform the edit.
        ValidationErrorTestEditFavoriteInteraction interaction = new ValidationErrorTestEditFavoriteInteraction(newName,
                newFullName);
        editor.editFavorite(favoriteTaskToEdit, interaction);

        //make sure we did get an error message.
        Assert.assertTrue(interaction.receivedErrorMessage);

        //make sure the settings were NOT changed. We'll go by what the editor has, not just our local favoriteTaskToEdit.
        favoriteTaskToEdit = editor.getFavorite(originalFullName);
        Assert.assertNotNull(favoriteTaskToEdit);

        Assert.assertEquals(originalDisplayName, favoriteTaskToEdit.getDisplayName());
        Assert.assertEquals(originalFullName, favoriteTaskToEdit.getFullCommandLine());
        Assert.assertTrue(favoriteTaskToEdit.alwaysShowOutput());
    }

    /**
     * This implementation is very specific. It expects to get an error after it returns from editFavorite. It will then
     * cancel on the second call to editFavorite.
     */
    private class ValidationErrorTestEditFavoriteInteraction implements FavoritesEditor.EditFavoriteInteraction {
        boolean receivedErrorMessage;

        private String newDisplayName;
        private String newFullCommandLine;

        private ValidationErrorTestEditFavoriteInteraction(String newDisplayName, String newFullCommandLine) {
            this.newDisplayName = newDisplayName;
            this.newFullCommandLine = newFullCommandLine;
        }

        public boolean editFavorite(FavoritesEditor.EditibleFavoriteTask favoriteTask) {
            if (receivedErrorMessage) {
                return false;
            }  //cancel once we've received an error.

            favoriteTask.alwaysShowOutput = false;
            favoriteTask.displayName = newDisplayName;
            favoriteTask.fullCommandLine = newFullCommandLine;
            return true;
        }

        public void reportError(String error) {
            receivedErrorMessage = true;
        }
    }

    /**
     * This edits a favorite so the display name is the same as an existing favorite. This should not be allowed. We're
     * expecting an error from this.
     */
    @Test
    public void testEditingFavoriteDisplayNameAlreadyExists() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        //add two tasks
        editor.addFavorite(mySubProject1Comple, true);
        editor.addFavorite(mySubSubProjectLib, true);

        //make sure they were added properly
        FavoriteTask favoriteTask1 = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask1.getFullCommandLine());

        FavoriteTask favoriteTask2 = editor.getFavoriteTasks().get(1);
        Assert.assertEquals("mysubproject1:mysubsubproject:lib", favoriteTask2.getFullCommandLine());


        //create an observer so we can make sure we're notified of the edit.
        final FavoritesEditor.FavoriteTasksObserver observer = context.mock(
                FavoritesEditor.FavoriteTasksObserver.class);
        context.checking(new Expectations() {{
            one(observer).favoritesChanged();
        }});
        editor.addFavoriteTasksObserver(observer, false);



        //we're about to perform the actual edit. Leave the full name alone, but use the other favorite's name
        FavoriteTask favoriteTaskToEdit = favoriteTask1;
        String newName = favoriteTask2.getDisplayName();
        String newFullName = favoriteTask1.getFullCommandLine();
        String originalFullName = favoriteTaskToEdit.getFullCommandLine();

        ValidationErrorTestEditFavoriteInteraction interaction = new ValidationErrorTestEditFavoriteInteraction(newName,
                newFullName);
        //now perform the edit.
        editor.editFavorite(favoriteTaskToEdit, interaction);

        //make sure we did get an error message.
        Assert.assertFalse(interaction.receivedErrorMessage);

        //make sure the settings were changed. We'll go by what the editor has, not just our local favoriteTaskToEdit.
        favoriteTaskToEdit = editor.getFavorite(originalFullName);
        Assert.assertNotNull(favoriteTaskToEdit);   //the original name should no longer be present

        favoriteTaskToEdit = editor.getFavorite(newFullName);
        Assert.assertNotNull(favoriteTaskToEdit);   //the new name should be present

        Assert.assertEquals(newName, favoriteTaskToEdit.getDisplayName());
        Assert.assertEquals(newFullName, favoriteTaskToEdit.getFullCommandLine());
    }

    /**
     * Edits a favorite and makes the full name blank. This is not allowed. We're expecting an error.
     */
    @Test
    public void testEditingFavoriteBlankFullName() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        //add a task
        editor.addFavorite(mySubProject1Comple, true);

        //make sure they were added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());

        //now perform the actual edit. Leave the display name alone, but use a blank full name
        editExpectingError(editor, favoriteTask, favoriteTask.getDisplayName(), "");
    }

    /**
     * Edits a favorite and makes the display name blank. This is not allowed. We're expecting an error.
     */
    @Test
    public void testEditingFavoriteBlankDisplayName() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        //add a task
        editor.addFavorite(mySubProject1Comple, true);

        //make sure they were added properly
        FavoriteTask favoriteTask = editor.getFavoriteTasks().get(0);
        Assert.assertEquals("mysubproject1:compile", favoriteTask.getFullCommandLine());

        //now perform the actual edit. Leave the full name alone, but use a blank full name
        editExpectingError(editor, favoriteTask, "", favoriteTask.getFullCommandLine());
    }

    /**
     * Tests moving favorites up. This mechansim is more advanced than just move the item up one. If you select multiple
     * things with non-selected items between them and then repeatedly move up, this will 'bunch up' the items at the
     * top while keeping the items in relative order between themselves. This seems like most users would actually want
     * to happen (but I'm sure someone won't like it). This moves every other item and keeps moving them until they all
     * wind up at the top.
     */
    @Test
    public void testMoveUp() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        FavoriteTask mySubProject1CompleFavorite = editor.addFavorite(mySubProject1Comple, false);
        FavoriteTask mySubProject1LibFavorite = editor.addFavorite(mySubProject1Lib, false);
        FavoriteTask mySubProject1DocFavorite = editor.addFavorite(mySubProject1Doc, false);
        FavoriteTask mySubSubProjectCompileFavorite = editor.addFavorite(mySubSubProjectCompile, false);
        FavoriteTask mySubSubProjectLibFavorite = editor.addFavorite(mySubSubProjectLib, false);
        FavoriteTask mySubSubProjectDocFavorite = editor.addFavorite(mySubSubProjectDoc, false);

        List<FavoriteTask> favoritesToMove = new ArrayList<FavoriteTask>();

        favoritesToMove.add(mySubProject1LibFavorite);
        favoritesToMove.add(mySubSubProjectCompileFavorite);
        favoritesToMove.add(mySubSubProjectDocFavorite);

        //our observer will make sure the order is correct.
        TestOrderFavoritesObserver observer = new TestOrderFavoritesObserver(editor, mySubProject1LibFavorite,
                mySubProject1CompleFavorite, mySubSubProjectCompileFavorite, mySubProject1DocFavorite,
                mySubSubProjectDocFavorite, mySubSubProjectLibFavorite);
        editor.addFavoriteTasksObserver(observer, false);

        editor.moveFavoritesBefore(favoritesToMove);

        //we're going to move them again, set the new expected order.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubProject1CompleFavorite,
                mySubSubProjectDocFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesBefore(favoritesToMove);

        //move again. Set the new order. Notice that both mySubProject1LibFavorite mySubSubProjectCompileFavorite has stopped moving.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubSubProjectDocFavorite,
                mySubProject1CompleFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesBefore(favoritesToMove);

        //one last time. Set the new order. Notice that the items have stopped moving. They're all at the top.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubSubProjectDocFavorite,
                mySubProject1CompleFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesBefore(favoritesToMove);
    }

    //

    /**
     * Observer that listens for favoritesReordered messages. When it gets them, it compares the new order with an
     * expected order.
     */
    private class TestOrderFavoritesObserver implements FavoritesEditor.FavoriteTasksObserver {
        private FavoritesEditor editor;
        private FavoriteTask[] expectedOrder;

        private TestOrderFavoritesObserver(FavoritesEditor editor, FavoriteTask... expectedOrder) {
            this.editor = editor;
            this.expectedOrder = expectedOrder;
        }

        public void setExpectedOrder(FavoriteTask... expectedOrder) {
            this.expectedOrder = expectedOrder;
        }

        public void favoritesChanged() {
            throw new AssertionError("Did not expect to get a favoritesChanged notification!");
        }

        public void favoritesReordered(List<FavoriteTask> favoritesReordered) {
            Object[] objects = editor.getFavoriteTasks().toArray();
            Assert.assertArrayEquals(expectedOrder, objects);
        }
    }

    /**
     * Same as testMoveUp, but moving down. See it for more information.
     */
    @Test
    public void testMoveDown() {
        FavoritesEditor editor = new FavoritesEditor();

        Assert.assertTrue(editor.getFavoriteTasks().isEmpty());

        FavoriteTask mySubProject1CompleFavorite = editor.addFavorite(mySubProject1Comple, false);
        FavoriteTask mySubProject1LibFavorite = editor.addFavorite(mySubProject1Lib, false);
        FavoriteTask mySubProject1DocFavorite = editor.addFavorite(mySubProject1Doc, false);
        FavoriteTask mySubSubProjectCompileFavorite = editor.addFavorite(mySubSubProjectCompile, false);
        FavoriteTask mySubSubProjectLibFavorite = editor.addFavorite(mySubSubProjectLib, false);
        FavoriteTask mySubSubProjectDocFavorite = editor.addFavorite(mySubSubProjectDoc, false);

        List<FavoriteTask> favoritesToMove = new ArrayList<FavoriteTask>();

        favoritesToMove.add(mySubProject1CompleFavorite);
        favoritesToMove.add(mySubProject1DocFavorite);
        favoritesToMove.add(mySubSubProjectLibFavorite);

        //our observer will make sure the order is correct.
        TestOrderFavoritesObserver observer = new TestOrderFavoritesObserver(editor, mySubProject1LibFavorite,
                mySubProject1CompleFavorite, mySubSubProjectCompileFavorite, mySubProject1DocFavorite,
                mySubSubProjectDocFavorite, mySubSubProjectLibFavorite);
        editor.addFavoriteTasksObserver(observer, false);

        editor.moveFavoritesAfter(favoritesToMove);

        //we're going to move them again, set the new expected order.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubProject1CompleFavorite,
                mySubSubProjectDocFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesAfter(favoritesToMove);

        //move again. Set the new order. Notice that both mySubProject1DocFavorite and mySubSubProjectLibFavorite has stopped moving.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubSubProjectDocFavorite,
                mySubProject1CompleFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesAfter(favoritesToMove);

        //one last time. Set the new order. Notice that the items have stopped moving. They're all at the bottom.
        observer.setExpectedOrder(mySubProject1LibFavorite, mySubSubProjectCompileFavorite, mySubSubProjectDocFavorite,
                mySubProject1CompleFavorite, mySubProject1DocFavorite, mySubSubProjectLibFavorite);

        editor.moveFavoritesAfter(favoritesToMove);
    }
}
