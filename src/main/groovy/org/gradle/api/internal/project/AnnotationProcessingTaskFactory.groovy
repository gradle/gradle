package org.gradle.api.internal.project

import org.gradle.api.Task
import org.gradle.api.Project
import java.lang.reflect.Method
import org.gradle.api.tasks.TaskAction
import java.lang.reflect.Modifier
import org.gradle.api.GradleException

class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final ITaskFactory taskFactory

    def AnnotationProcessingTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    Task createTask(Project project, Map args) {
        Task task = taskFactory.createTask(project, args)
        for (Class current = task.getClass(); current != null; current = current.superclass) {
            current.declaredMethods.each {Method method ->
                if (!method.getAnnotation(TaskAction)) {
                    return
                }
                if (Modifier.isStatic(method.modifiers)) {
                    throw new GradleException("Cannot use @TaskAction annotation on static method ${method.declaringClass.simpleName}.$method.name().")
                }
                if (method.parameterTypes.length > 0) {
                    throw new GradleException("Cannot use @TaskAction annotation on method ${method.declaringClass.simpleName}.$method.name() as this method takes parameters.")
                }
                task.doFirst { task."$method.name"() }
            }
        }
        task
    }
}