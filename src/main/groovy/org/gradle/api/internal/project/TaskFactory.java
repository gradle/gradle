/*
 * Copyright 2007-2008 the original author or authors.
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
package org.gradle.api.internal.project;

import org.gradle.api.*;
import org.gradle.api.DefaultTask;
import org.gradle.api.internal.ConventionTask;
import org.gradle.util.GUtil;
import org.codehaus.groovy.control.CompilationFailedException;

import java.lang.reflect.Constructor;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.*;

import groovy.lang.*;

/**
 * @author Hans Dockter
 */
public class TaskFactory implements ITaskFactory {
    public static final String GENERATE_CONVENTION_GETTERS = "generateGetters";
    private final Map<Class, Class> generatedClasses = new HashMap<Class, Class>();

    public Task createTask(Project project, Map args) {
        checkTaskArgsAndCreateDefaultValues(args);

        String name = args.get(Task.TASK_NAME).toString();
        if (!GUtil.isTrue(name)) {
            throw new InvalidUserDataException("The task name must be provided.");
        }

        Class type = (Class) args.get(Task.TASK_TYPE);
        Boolean generateGetters = Boolean.valueOf(args.get(GENERATE_CONVENTION_GETTERS).toString());
        Task task = createTaskObject(project, type, name, generateGetters);

        Object dependsOnTasks = args.get(Task.TASK_DEPENDS_ON);
        task.dependsOn(dependsOnTasks);
        Object description = args.get(Task.TASK_DESCRIPTION);
        if (description != null) {
            task.setDescription(description.toString());
        }
        Object action = args.get(Task.TASK_ACTION);
        if (action instanceof TaskAction) {
            TaskAction taskAction = (TaskAction) action;
            task.doFirst(taskAction);
        } else if (action != null) {
            Closure closure = (Closure) action;
            task.doFirst(closure);
        }

        return task;
    }

    private Task createTaskObject(Project project, Class type, String name, boolean generateGetters) {
        if (!Task.class.isAssignableFrom(type)) {
            throw new GradleException(String.format(
                    "Cannot create task of type '%s' as it does not implement the Task interface.",
                    type.getSimpleName()));
        }

        if (generateGetters && ConventionTask.class.isAssignableFrom(type)) {
            type = generateSubclass(type);
        }

        Constructor constructor;
        try {
            constructor = type.getDeclaredConstructor(Project.class, String.class);
        } catch (NoSuchMethodException e) {
            throw new GradleException(String.format(
                    "Cannot create task of type '%s' as it does not have an appropriate public constructor.",
                    type.getSimpleName()));
        }

        try {
            return (Task) constructor.newInstance(project, name);
        } catch (InvocationTargetException e) {
            throw new GradleException(String.format("Could not create task of type '%s'.", type.getSimpleName()),
                    e.getCause());
        } catch (Exception e) {
            throw new GradleException(String.format("Could not create task of type '%s'.", type.getSimpleName()), e);
        }
    }

    private Class generateSubclass(Class type) {
        Class generatedClass = generatedClasses.get(type);
        if (generatedClass != null) {
            return generatedClass;
        }

        String className = type.getSimpleName() + "_WithConventionMapping";

        Formatter src = new Formatter();
        src.format("package %s;%n", type.getPackage().getName());
        src.format("public class %s extends %s {%n", className, type.getName().replaceAll("\\$", "."));
        src.format("public %s(org.gradle.api.Project project, String name) { super(project, name); }%n", className);
        MetaClass metaClass = GroovySystem.getMetaClassRegistry().getMetaClass(type);
        List<MetaProperty> properties = metaClass.getProperties();
        for (MetaProperty property : properties) {
            if (property.getName().equals("metaClass")) {
                continue;
            }
            if (property instanceof MetaBeanProperty) {
                MetaBeanProperty metaBeanProperty = (MetaBeanProperty) property;
                MetaMethod getter = metaBeanProperty.getGetter();
                if (getter != null && !Modifier.isFinal(getter.getModifiers()) && ConventionTask.class.isAssignableFrom(getter.getDeclaringClass().getCachedClass())) {
                    String returnTypeName = getter.getReturnType().getCanonicalName();
                    src.format("public %s %s() { return conv(super.%s(), '%s'); }%n", returnTypeName,
                            getter.getName(), getter.getName(), property.getName());
                }
            }
        }
        src.format("}");

        GroovyClassLoader classLoader = new GroovyClassLoader(type.getClassLoader());
        try {
            generatedClass = classLoader.parseClass(src.toString());
        } catch (CompilationFailedException e) {
            throw new GradleException(String.format("Could not generate a proxy class for task class %s.",
                    type.getName()), e);
        }
        generatedClasses.put(type, generatedClass);
        return generatedClass;
    }

    private void checkTaskArgsAndCreateDefaultValues(Map args) {
        setIfNull(args, Task.TASK_NAME, "");
        setIfNull(args, Task.TASK_TYPE, DefaultTask.class);
        setIfNull(args, Task.TASK_DEPENDS_ON, new ArrayList());
        setIfNull(args, GENERATE_CONVENTION_GETTERS, "true");
    }

    private void setIfNull(Map map, String key, Object defaultValue) {
        if (map.get(key) == null) {
            map.put(key, defaultValue);
        }
    }
}
