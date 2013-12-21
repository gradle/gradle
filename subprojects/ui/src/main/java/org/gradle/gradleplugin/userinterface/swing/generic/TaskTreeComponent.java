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
package org.gradle.gradleplugin.userinterface.swing.generic;

import org.gradle.foundation.ProjectView;
import org.gradle.foundation.TaskView;
import org.gradle.foundation.visitors.TaskTreePopulationVisitor;
import org.gradle.gradleplugin.foundation.GradlePluginLord;
import org.gradle.gradleplugin.foundation.filters.ProjectAndTaskFilter;

import javax.swing.*;
import javax.swing.tree.*;
import java.awt.*;
import java.awt.event.*;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;

/**
 * This displays a tree of projects, subprojects, and tasks. You implement the Interaction to detemine how to handle right clicks and double clicking tasks. To use this, call populate and pass it a
 * filter (allows you to change exactly what is displayed). There are several functions to obtaining the selected items, plus you can get the tree directly for any advanced functionality.
 */
public class TaskTreeComponent {
    private GradlePluginLord gradlePluginLord;
    private Interaction interaction;

    private JTree tree;
    private DefaultTreeModel model;
    private TaskTreeBaseNode rootNode;

    private boolean isPopulated;

    private TaskTreeComponent.Renderer renderer;

    public interface Interaction {
        void rightClick(JTree tree, int x, int y);

        /**
         * Notification that a project was invoked (double-clicked). Do whatever you like, such as execute its default task.
         *
         * @param project the project that was invoked.
         */
        void projectInvoked(ProjectView project);

        /**
         * Notification that a task was invoked (double-clicked). Do whatever you like, such as execute it.
         *
         * @param task the task that was invoked.
         * @param isCtrlKeyDown true if the CTRL key was pressed at the time
         */
        void taskInvoked(TaskView task, boolean isCtrlKeyDown);
    }

    public TaskTreeComponent(GradlePluginLord gradlePluginLord, Interaction interaction) {
        this.gradlePluginLord = gradlePluginLord;
        this.interaction = interaction;

        createTreePanel();
    }

    private void createTreePanel() {
        rootNode = new TaskTreeBaseNode();

        model = new DefaultTreeModel(rootNode);
        tree = new JTree(model);

        tree.setRootVisible(false);
        tree.setShowsRootHandles(true);

        renderer = new Renderer();
        tree.setCellRenderer(renderer);

        ToolTipManager.sharedInstance().registerComponent(tree);

        tree.setToggleClickCount(99);  //prevents double clicks from expanding/collapsing the tree. We want to treat them as double-clicks

        tree.addMouseListener(new MyMouseListener());

        //make hitting Enter and CTRL Enter on a node equal executing it (the first node at least)
        tree.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                executeFirstSelectedNode(false);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, 0), JComponent.WHEN_IN_FOCUSED_WINDOW);

        tree.registerKeyboardAction(new ActionListener() {
            public void actionPerformed(ActionEvent e) {
                executeFirstSelectedNode(true);
            }
        }, KeyStroke.getKeyStroke(KeyEvent.VK_ENTER, KeyEvent.CTRL_MASK), JComponent.WHEN_IN_FOCUSED_WINDOW);
    }

    public JTree getTree() {
        return tree;
    }

    public void setTreeCellRenderer(TreeCellRenderer renderer) {
        tree.setCellRenderer(renderer);
    }

    /**
     * This renders our projects and tasks. This removes the icon and optionally shows the description in a different color. Since there's quite a bit of code for handling rendering tree cells, I'm
     * just going to mooch off of the DefaultTreeCellRenderer. I'll just modify its behavior a little (I probably don't need that or the description since it's not going to draw a selection or
     * highlight).
     */
    private class Renderer implements TreeCellRenderer {
        private JPanel panel;
        private DefaultTreeCellRenderer nameRenderer;
        private DefaultTreeCellRenderer descriptionRenderer;
        private Color descriptionColor;
        private boolean showDescription = true;
        private Component seperator;
        private Font normalFont;
        private Font boldFont;

        private Renderer() {
            setupRendererUI();
            setShowDescription(true);

            descriptionColor = Color.blue;
        }

        private void setupRendererUI() {
            panel = new JPanel();
            panel.setLayout(new BoxLayout(panel, BoxLayout.X_AXIS));

            nameRenderer = new DefaultTreeCellRenderer();
            descriptionRenderer = new DefaultTreeCellRenderer();

            panel.add(nameRenderer);
            seperator = Box.createHorizontalStrut(10);
            panel.add(seperator);
            panel.add(descriptionRenderer);

            panel.setOpaque(false);

            setupFonts();
        }

        /**
         * Setup the fonts. On some platforms, bold is the typical version. We explicitly don't want that. So we'll make the fonts plain and use the bold for our own purposes (indicating default
         * tasks).
         */
        private void setupFonts() {
            normalFont = nameRenderer.getFont().deriveFont(Font.PLAIN);
            boldFont = normalFont.deriveFont(Font.BOLD);

            nameRenderer.setFont(normalFont);
            descriptionRenderer.setFont(normalFont);
        }

        public boolean showDescription() {
            return showDescription;
        }

        //the easiest thing to do is just hide the description and its separator

        public void setShowDescription(boolean showDescription) {
            this.showDescription = showDescription;
            seperator.setVisible(showDescription);
            descriptionRenderer.setVisible(showDescription);
            seperator.invalidate();
            nameRenderer.invalidate();
            descriptionRenderer.invalidate();
            panel.invalidate();

            //have to tell the tree each node changed. This is so it will recalculate its size. Without this, if the description is
            //initially disabled, the tree is populated and expanded, then description is enabled, nothing shows up because the tree
            //caches the node's size for some dumb reason.
            Enumeration enumeration = rootNode.breadthFirstEnumeration();
            while (enumeration.hasMoreElements()) {
                TaskTreeBaseNode treeNode = (TaskTreeBaseNode) enumeration.nextElement();
                model.nodeChanged(treeNode);
            }

            tree.repaint();
        }

        public Component getTreeCellRendererComponent(JTree tree, Object value, boolean isSelected, boolean expanded, boolean leaf, int row, boolean hasFocus) {
            TaskTreeBaseNode node = (TaskTreeBaseNode) value;

            String description = node.getDescription();

            //we've already added these components to our panel. We know they're just labels. Calling getTreeCell... just sets their text and colors correctly.
            this.nameRenderer.getTreeCellRendererComponent(tree, node.toString(), isSelected, expanded, leaf, row, hasFocus);
            this.descriptionRenderer.getTreeCellRendererComponent(tree, description, isSelected, expanded, leaf, row, false);

            //set the tooltip. This must be on the component we return not our sub renderers
            panel.setToolTipText(description);

            //just remove the icon entirely
            nameRenderer.setIcon(null);
            this.descriptionRenderer.setIcon(null);

            if (node.isBold()) {
                nameRenderer.setFont(boldFont);
            } else {
                nameRenderer.setFont(normalFont);
            }

            //set the description color. If its selected, make it the name renderer's color
            //so we know the colors won't conflict (they do on Windows XP).
            if (!isSelected) {
                this.descriptionRenderer.setForeground(descriptionColor);
            } else {
                this.descriptionRenderer.setForeground(nameRenderer.getForeground());
            }

            nameRenderer.invalidate();
            descriptionRenderer.invalidate();
            seperator.invalidate();
            panel.invalidate();
            panel.validate();

            return panel;
        }
    }

    private class MyMouseListener extends MouseAdapter {
        public void mousePressed(MouseEvent e) {
            if (e.getButton() == MouseEvent.BUTTON3) {  //This is here just to select a node that we right click on.
                //The tree really needs to handle this for us.
                Point point = e.getPoint();
                int row = tree.getRowForLocation(point.x, point.y);
                if (row != -1) {
                    if (tree.isRowSelected(row)) {
                        return;
                    }  //if its already selected, just leave it alone. This prevents us from changing selecting when a user right-clicks on one of many selected items.

                    //we need to determine if we move the selection, or just add it to the existing selection.
                    if (isAddToSelectionKey(e)) {
                        tree.addSelectionRow(row);
                    } else {
                        tree.setSelectionRow(row);
                    }
                }
            }
        }

        private boolean isAddToSelectionKey(MouseEvent e) {  //this is actually OS-specific, but for now, I'll just use CTRL.
            return (e.getModifiers() & MouseEvent.CTRL_MASK) != 0;
        }

        public void mouseClicked(MouseEvent e) {
            if (e.getClickCount() == 1) {
                if (e.getButton() == MouseEvent.BUTTON3) {
                    Point point = e.getPoint();
                    interaction.rightClick(tree, point.x, point.y);
                }
            } else if (e.getClickCount() == 2) {
                TaskTreeBaseNode node = getNodeAtPoint(e.getPoint());
                if (node != null) {
                    boolean isCtrlKeyDown = (e.getModifiers() & MouseEvent.CTRL_MASK) != 0;
                    node.executeTask(isCtrlKeyDown);
                }
            }
        }
    }

    /**
     * This populates (and repopulates) the tree. This is surprisingly tedious in an effort to make the tree collapse as little as possible.
     */
    public void populate(ProjectAndTaskFilter filter) {
        TaskTreePopulationVisitor.visitProjectAndTasks(gradlePluginLord.getProjects(), new PopulateTreeVisitor(), filter, rootNode);

        model.reload();
        SwingUtilities.invokeLater(new Runnable() {
            public void run() {  //this expands the 'root' project
                tree.expandRow(0);
            }
        });

        isPopulated = true;
    }

    public boolean isPopulated() {
        return isPopulated;
    }

    /**
     * This visitor populates the tree as we walk projects and tasks. This jumpts through quite a bit of hoops in an effort to keep the tree from collapsing. It still does, but not completely. In
     * order to keep it from collapsing, you must track some additional information that frankly the tree could and should do for you.
     */
    private class PopulateTreeVisitor implements TaskTreePopulationVisitor.Visitor<TaskTreeBaseNode, TaskTreeNode> {
        /**
         * This is called for each project.
         *
         * @param project the project
         * @param parentProjectObject whatever you handed back from a prior call to visitProject if this is a sub project. Otherwise, it'll be whatever was passed into the visitPojectsAndTasks
         * function.
         * @return an object that will be handed back to you for each of this project's tasks.
         */
        public TaskTreeBaseNode visitProject(ProjectView project, int indexOfProject, TaskTreeBaseNode parentProjectObject) {
            ProjectTreeNode projectTreeNode = findProjectChild(parentProjectObject, project.getName());
            if (projectTreeNode == null) {
                projectTreeNode = new ProjectTreeNode(project);
            }

            int actualIndex = parentProjectObject.getIndex(projectTreeNode);
            if (actualIndex != indexOfProject) //this will be -1 for a new node
            {
                if (actualIndex != -1) //only try to remove it if its already there. Swing doesn't like this otherwise.
                {
                    model.removeNodeFromParent(projectTreeNode);
                }

                insertChildNode(parentProjectObject, projectTreeNode, indexOfProject);
            }

            return projectTreeNode;
        }

        private ProjectTreeNode findProjectChild(TaskTreeBaseNode parentNode, String projectName) {
            for (int index = 0; index < parentNode.getChildCount(); index++) {
                TreeNode child = parentNode.getChildAt(index);
                if (child instanceof ProjectTreeNode) {
                    if (((ProjectTreeNode) child).getProject().getName().equals(projectName)) {
                        return (ProjectTreeNode) child;
                    }
                }
            }
            return null;
        }

        /**
         * This is called for each task.
         *
         * @param task the task
         * @param indexOfTask index
         * @param tasksProject the project for this task
         */
        public TaskTreeNode visitTask(TaskView task, int indexOfTask, ProjectView tasksProject, TaskTreeBaseNode parentTreeNode) {
            TaskTreeNode taskTreeNode = findTaskChild((ProjectTreeNode) parentTreeNode, task.getName());

            if (taskTreeNode == null) {
                taskTreeNode = new TaskTreeNode(task);
            }

            int actualIndex = parentTreeNode.getIndex(taskTreeNode);
            if (actualIndex != indexOfTask) {
                //this will be -1 for a new node
                if (actualIndex != -1) {
                    //only try to remove it if its already there. Swing doesn't like this otherwise.
                    model.removeNodeFromParent(taskTreeNode);
                }

                insertChildNode(parentTreeNode, taskTreeNode, indexOfTask);
            }

            return taskTreeNode;
        }

        //This only exists so we can call insert or add appropriately.
        //The stupid tree isn't smart enough to do this for you.

        private void insertChildNode(DefaultMutableTreeNode parent, DefaultMutableTreeNode child, int index) {
            if (parent.getChildCount() < index) {
                parent.add(child);
                model.nodesWereInserted(parent, new int[]{parent.getChildCount() - 1});
            } else {
                parent.insert(child, index);
                model.nodesWereInserted(parent, new int[]{index});
            }
        }

        private TaskTreeNode findTaskChild(ProjectTreeNode parentNode, String taskName) {
            for (int index = 0; index < parentNode.getChildCount(); index++) {
                TreeNode child = parentNode.getChildAt(index);
                if (child instanceof TaskTreeNode) {
                    if (((TaskTreeNode) child).getTask().getName().equals(taskName)) {
                        return (TaskTreeNode) child;
                    }
                }
            }
            return null;
        }

        /**
         * This is called when a project has been visited completely and is just a notification giving you an opportunity to do whatever you like.
         *
         * Here, we're going to remove any nodes that aren't in either of the lists. This is when a task or project is hidden or when things simply change.
         *
         * @param parentProjectObject the object that represents the parent of the project and task objects below
         * @param projectObjects a list of whatever you returned from visitProject
         * @param taskObjects a list of whatever you returned from visitTask
         */
        public void completedVisitingProject(TaskTreeBaseNode parentProjectObject, List<TaskTreeBaseNode> projectObjects, List<TaskTreeNode> taskObjects) {
            int index = 0;
            while (index < parentProjectObject.getChildCount()) {
                TaskTreeBaseNode child = (TaskTreeBaseNode) parentProjectObject.getChildAt(index);
                if (!projectObjects.contains(child) && !taskObjects.contains(child)) {
                    model.removeNodeFromParent(child);
                } else {
                    index++;
                }
            }
        }
    }

    private void expandNode(TreeNode node) {
        tree.expandPath(new TreePath(node));
    }

    /**
     * This is a basic tree node. All nodes in this tree must extend this. This is so we don't have to deal with all the differing types of things that may be in this tree.
     */
    public class TaskTreeBaseNode extends DefaultMutableTreeNode {
        public void executeTask(boolean isCtrlKeyDown) {
        }  //do nothing by default.

        public String toString() {
            return "hidden-root";
        }  //by default, its the root.

        public String getDescription() {
            return null;
        }

        public boolean isBold() {
            return false;
        }
    }

    /**
     * This represents a project.
     */
    public class ProjectTreeNode extends TaskTreeBaseNode {
        private ProjectView project;

        private ProjectTreeNode(ProjectView project) {
            this.project = project;
        }

        public String toString() {
            return project.getName();
        }

        @Override
        public void executeTask(boolean isCtrlKeyDown) {
            interaction.projectInvoked(project);
        }

        @Override
        public String getDescription() {
            return project.getDescription();
        }

        public ProjectView getProject() {
            return project;
        }
    }

    /**
     * This represents a single task.
     */
    public class TaskTreeNode extends TaskTreeBaseNode {
        private TaskView task;

        private TaskTreeNode(TaskView task) {
            this.task = task;
        }

        public String toString() {
            return task.getName();
        }

        @Override
        public void executeTask(boolean isCtrlKeyDown) {
            interaction.taskInvoked(task, isCtrlKeyDown);
        }

        public TaskView getTask() {
            return task;
        }

        @Override
        public String getDescription() {
            return task.getDescription();
        }

        @Override
        public boolean isBold() {
            return task.isDefault();
        }
    }

    /**
     * Returns the node at the specified point.
     */
    public TaskTreeBaseNode getNodeAtPoint(Point point) {
        int row = tree.getRowForLocation(point.x, point.y);
        if (row == -1) {
            return null;
        }

        TreePath path = tree.getPathForLocation(point.x, point.y);
        if (path == null) {
            return null;
        }
        return (TaskTreeBaseNode) path.getLastPathComponent();
    }

    /**
     * @return the first selected node or null if nothing is selected.
     */
    public TaskTreeBaseNode getFirstSelectedNode() {
        TreePath path = tree.getSelectionPath();
        if (path == null) {
            return null;
        }

        return (TaskTreeBaseNode) path.getLastPathComponent();
    }

    /**
     * @return a list of TaskTabTreeNode based on what is selected in the tree or an empty list if nothing is selected.
     */
    public List<TaskTreeBaseNode> getSelectedNodes() {
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths == null) {
            return Collections.emptyList();
        }

        List<TaskTreeBaseNode> nodes = new ArrayList<TaskTreeBaseNode>();

        for (int index = 0; index < treePaths.length; index++) {
            TreePath treePath = treePaths[index];
            nodes.add((TaskTreeBaseNode) treePath.getLastPathComponent());
        }

        return nodes;
    }

    /**
     * Determines if we have any projects selected. This ignores selected tasks.
     *
     * @return true if we have projects selected, false otherwise.
     */
    public boolean hasProjectsSelected() {
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths == null) {
            return false;
        }

        for (int index = 0; index < treePaths.length; index++) {
            TreePath treePath = treePaths[index];

            Object o = treePath.getLastPathComponent();
            if (o instanceof ProjectTreeNode) {
                return true;
            }
        }

        return false;
    }

    /**
     * Determines if we have any tasks selected. This ignores selected projects.
     *
     * @return true if we have tasks selected, false otherwise.
     */
    public boolean hasTasksSelected() {
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths == null) {
            return false;
        }

        for (int index = 0; index < treePaths.length; index++) {
            TreePath treePath = treePaths[index];

            Object o = treePath.getLastPathComponent();
            if (o instanceof TaskTreeNode) {
                return true;
            }
        }

        return false;
    }

    /**
     * This returns a list of selected tasks. This ignores selected projects. Note: the tasks are ordered according to the order in which they were selected.
     *
     * @return the selected tasks. Will return an empty list if no tasks are selected.
     */
    public List<TaskView> getSelectedTasks() {
        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths == null) {
            return Collections.emptyList();
        }

        List<TaskView> tasks = new ArrayList<TaskView>();

        for (int index = 0; index < treePaths.length; index++) {
            TreePath treePath = treePaths[index];

            Object o = treePath.getLastPathComponent();
            if (o instanceof TaskTreeNode) {
                tasks.add(((TaskTreeNode) o).task);
            }
        }

        return tasks;
    }

    /**
     * Object to hold onto mutliple selections, but not just multiples of the same type of node. This separates the selected nodes by type. You can have multiple projects and tasks selected.
     */
    public class MultipleSelection {
        public List<ProjectView> projects = new ArrayList<ProjectView>();
        public List<TaskView> tasks = new ArrayList<TaskView>();
    }

    /**
     * This returns the current selection broken up into projects and tasks.
     *
     * @return the selected projects and tasks. This never returns null and the contained lists are never null.
     */
    public MultipleSelection getSelectedProjectsAndTasks() {
        MultipleSelection multipleSelection = new MultipleSelection();

        TreePath[] treePaths = tree.getSelectionPaths();
        if (treePaths == null) {
            return multipleSelection;
        }

        for (int index = 0; index < treePaths.length; index++) {
            TreePath treePath = treePaths[index];

            Object o = treePath.getLastPathComponent();
            if (o instanceof TaskTreeNode) {
                multipleSelection.tasks.add(((TaskTreeNode) o).getTask());
            } else if (o instanceof ProjectTreeNode) {
                multipleSelection.projects.add(((ProjectTreeNode) o).getProject());
            }
        }

        return multipleSelection;
    }

    public boolean showDescription() {
        return renderer.showDescription();
    }

    public void setShowDescription(boolean showDescription) {
        this.renderer.setShowDescription(showDescription);
    }

    private void executeFirstSelectedNode(boolean isCtrlKeyDown) {
        TaskTreeComponent.TaskTreeBaseNode node = getFirstSelectedNode();
        if (node != null) {
            node.executeTask(isCtrlKeyDown);
        }
    }
}
