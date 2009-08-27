package org.gradle.api.internal.project

import java.lang.reflect.Method
import java.lang.reflect.Modifier
import org.apache.commons.lang.StringUtils
import org.gradle.api.GradleException
import org.gradle.api.InvalidUserDataException
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.tasks.*
import java.lang.annotation.Annotation
import org.gradle.api.file.FileCollection

class AnnotationProcessingTaskFactory implements ITaskFactory {
    private final ITaskFactory taskFactory

    def AnnotationProcessingTaskFactory(ITaskFactory taskFactory) {
        this.taskFactory = taskFactory;
    }

    Task createTask(Project project, Map args) {
        Task task = taskFactory.createTask(project, args)
        List<Closure> notNullActions = []
        List<Closure> skipActions = []
        List<Closure> validationActions = []
        for (Class current = task.getClass(); current != null; current = current.superclass) {
            current.declaredMethods.each {Method method ->
                attachTaskAction(method, task)
                Map methodInfo = [method: method, task: task, skipActions: skipActions, validationActions: validationActions, notNullActions: notNullActions]
                attachInputFileValidation(methodInfo)
                attachInputFilesValidation(methodInfo)
                attachInputDirValidation(methodInfo)
                attachOutputFileValidation(methodInfo)
                attachOutputDirValidation(methodInfo)
            }
        }

        validationActions.each {Closure cl -> task.doFirst(cl) }
        skipActions.each {Closure cl -> task.doFirst(cl) }
        notNullActions.each {Closure cl -> task.doFirst(cl) }

        task
    }

    def attachTaskAction(Method method, Task task) {
        if (!method.getAnnotation(TaskAction)) {
            return
        }
        if (Modifier.isStatic(method.modifiers)) {
            throw new GradleException("Cannot use @TaskAction annotation on static method ${method.declaringClass.simpleName}.$method.name().")
        }
        if (method.parameterTypes.length > 0) {
            throw new GradleException("Cannot use @TaskAction annotation on method ${method.declaringClass.simpleName}.$method.name() as this method takes parameters.")
        }
        task.doLast { task."$method.name"() }
    }

    def attachInputFileValidation(Map methodInfo) {
        attachValidationAction(methodInfo, InputFile) {String propertyName, value ->
            File fileValue = value
            if (!fileValue.exists()) {
                throw new InvalidUserDataException("File '$fileValue' specified for property '$propertyName' does not exist.")
            }
            if (!fileValue.isFile()) {
                throw new InvalidUserDataException("File '$fileValue' specified for property '$propertyName' is not a file.")
            }
        }
    }

    def attachInputFilesValidation(Map methodInfo) {
        attachValidationAction(methodInfo, InputFiles, null) {String propertyName, value ->
            if (methodInfo.method.getAnnotation(SkipWhenEmpty) && value instanceof FileCollection) {
                value.stopExecutionIfEmpty()
            }
        }
    }

    def attachInputDirValidation(Map methodInfo) {
        attachValidationAction(methodInfo, InputDirectory) {String propertyName, value ->
            File fileValue = value
            if (!fileValue.exists()) {
                throw new InvalidUserDataException("Directory '$fileValue' specified for property '$propertyName' does not exist.")
            }
            if (!fileValue.isDirectory()) {
                throw new InvalidUserDataException("Directory '$fileValue' specified for property '$propertyName' is not a directory.")
            }
        }
    }

    def attachOutputFileValidation(Map methodInfo) {
        attachValidationAction(methodInfo, OutputFile) {String propertyName, value ->
            File fileValue = value
            if (fileValue.exists() && !fileValue.isFile()) {
                throw new InvalidUserDataException("Cannot write to file '$fileValue' specified for property '$propertyName' as it is a directory.")
            }
            if (!fileValue.parentFile.isDirectory() && !fileValue.parentFile.mkdirs()) {
                throw new InvalidUserDataException("Cannot create parent directory '$fileValue.parentFile' of file specified for property '$propertyName'.")
            }
        }
    }

    def attachOutputDirValidation(Map methodInfo) {
        attachValidationAction(methodInfo, OutputDirectory) {String propertyName, value ->
            File fileValue = value
            if (!fileValue.isDirectory() && !fileValue.mkdirs()) {
                throw new InvalidUserDataException("Cannot create directory '$fileValue' specified for property '$propertyName'.")
            }
        }
    }

    def attachValidationAction(Map methodInfo, Class annotationType, Closure validationAction, Closure skipAction = null) {
        Method method = methodInfo.method
        Task task = methodInfo.task
        if (!method.getAnnotation(annotationType)) {
            return
        }
        if (!isGetter(method)) {
            throw new GradleException("Cannot attach @$annotationType.simpleName to non-getter method $method.name().")
        }

        String propertyName = StringUtils.uncapitalize(method.name.substring(3))
        Annotation optional = method.getAnnotation(Optional)
        if (!optional) {
            methodInfo.notNullActions << {
                def value = task."$method.name"()
                if (value == null) {
                    throw new InvalidUserDataException("No value has been specified for property '$propertyName'.")
                }
            }
        }

        if (skipAction) {
            methodInfo.skipActions << {
                def value = task."$method.name"()
                skipAction(propertyName, value)
            }
        }

        if (validationAction) {
            methodInfo.validationActions << {
                def value = task."$method.name"()
                if (value != null) {
                    validationAction(propertyName, value)
                }
            }
        }
    }

    private boolean isGetter(Method method) {
        return method.name.startsWith("get") && method.returnType != Void.TYPE &&
                method.parameterTypes.length == 0 && !Modifier.isStatic(method.modifiers)
    }
}