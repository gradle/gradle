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
package org.gradle.build.docs.dsl

import org.codehaus.groovy.groovydoc.GroovyFieldDoc
import org.codehaus.groovy.groovydoc.GroovyMethodDoc
import org.codehaus.groovy.groovydoc.GroovyRootDoc
import org.codehaus.groovy.tools.groovydoc.GroovyDocTool
import org.codehaus.groovy.tools.groovydoc.SimpleGroovyClassDoc
import org.gradle.api.file.FileVisitDetails
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.build.docs.dsl.model.ClassMetaData

/**
 * Extracts meta-data from the Groovy and Java source files which make up the Gradle DSL. Persists the meta-data to a file
 * for later use in generating the docbook source for the DSL.
 */
class ExtractDslMetaDataTask extends SourceTask {
    @OutputFile
    def File destFile

    @TaskAction
    def extract() {
        project.delete(temporaryDir)
        project.copy { from source; into temporaryDir }
        GroovyDocTool groovyDoc = new GroovyDocTool([temporaryDir.absolutePath] as String[])
        List<String> files = []
        project.fileTree(temporaryDir).visit { FileVisitDetails fvd ->
            if (!fvd.isDirectory()) {
                files << fvd.path
            }
        }
        groovyDoc.add(files)

        Map<String, ClassMetaData> allClasses = [:]
        GroovyRootDoc rootDoc = groovyDoc.rootDoc
        rootDoc.classes().each { SimpleGroovyClassDoc doc ->
            String className = doc.qualifiedTypeName()
            String superClassName = doc.superclass()?.qualifiedTypeName()
            def interfaces = doc.interfaces().collect { it.qualifiedTypeName() }

            ClassMetaData classMetaData = new ClassMetaData(className, superClassName, doc.isGroovy(), doc.rawCommentText, doc.importedClassesAndPackages, interfaces)
            allClasses[className] = classMetaData

            doc.methods().each { GroovyMethodDoc method ->
                if (method.name().matches("get.+")) {
                    String propName = method.name()[3].toLowerCase() + method.name().substring(4)
                    classMetaData.addReadableProperty(propName, method.returnType().qualifiedTypeName(), method.rawCommentText)
                } else if (method.name().matches("set.+")) {
                    String propName = method.name()[3].toLowerCase() + method.name().substring(4)
                    classMetaData.addWriteableProperty(propName, method.returnType().qualifiedTypeName(), method.rawCommentText)
                }
            }

            // This bit of ugliness is to get Groovydoc to resolve the type names for properties by pretending that
            // they are fields
            doc.fields.clear()
            doc.fields.addAll(doc.properties())
            doc.methods.clear()
            doc.resolve(rootDoc)

            doc.properties().each { GroovyFieldDoc field ->
                classMetaData.addReadableProperty(field.name(), field.type().qualifiedTypeName(), field.rawCommentText)
                classMetaData.addWriteableProperty(field.name(), field.type().qualifiedTypeName(), field.rawCommentText)
            }
        }

        destFile.withObjectOutputStream { ObjectOutputStream outstr ->
            outstr.writeObject(allClasses)
        }
    }
}
