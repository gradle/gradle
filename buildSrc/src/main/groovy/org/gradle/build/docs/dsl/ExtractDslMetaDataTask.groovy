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

import groovyjarjarantlr.collections.AST
import org.codehaus.groovy.antlr.AntlrASTProcessor
import org.codehaus.groovy.antlr.SourceBuffer
import org.codehaus.groovy.antlr.UnicodeEscapingReader
import org.codehaus.groovy.antlr.java.Java2GroovyConverter
import org.codehaus.groovy.antlr.java.JavaLexer
import org.codehaus.groovy.antlr.java.JavaRecognizer
import org.codehaus.groovy.antlr.parser.GroovyLexer
import org.codehaus.groovy.antlr.parser.GroovyRecognizer
import org.codehaus.groovy.antlr.treewalker.PreOrderTraversal
import org.codehaus.groovy.antlr.treewalker.SourceCodeTraversal
import org.codehaus.groovy.antlr.treewalker.Visitor
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import org.gradle.build.docs.dsl.model.ClassMetaData
import org.gradle.build.docs.dsl.model.MethodMetaData
import org.gradle.build.docs.dsl.model.PropertyMetaData
import org.gradle.build.docs.model.ClassMetaDataRepository
import org.gradle.build.docs.model.SimpleClassMetaDataRepository
import org.gradle.build.docs.dsl.model.ParameterMetaData
import org.gradle.util.Clock

/**
 * Extracts meta-data from the Groovy and Java source files which make up the Gradle DSL. Persists the meta-data to a file
 * for later use in generating the docbook source for the DSL, such as by {@link org.gradle.build.docs.dsl.docbook.AssembleDslDocTask}.
 */
class ExtractDslMetaDataTask extends SourceTask {
    @OutputFile
    def File destFile

    @TaskAction
    def extract() {
        Clock clock = new Clock()

        SimpleClassMetaDataRepository<ClassMetaData> repository = new SimpleClassMetaDataRepository<ClassMetaData>()
        int counter = 0
        source.each { File f ->
            parse(f, repository)
            counter++
        }

        TypeNameResolver resolver = new TypeNameResolver(repository)
        repository.each { name, metaData ->
            resolve(metaData, resolver)
        }
        repository.store(destFile)

        println "Parsed $counter classes in ${clock.time}"
    }

    def parse(File sourceFile, ClassMetaDataRepository<ClassMetaData> repository) {
        try {
            sourceFile.withReader { reader ->
                if (sourceFile.name.endsWith('.java')) {
                    parseJava(sourceFile, reader, repository)
                } else {
                    parseGroovy(sourceFile, reader, repository)
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not parse '$sourceFile'.", e)
        }
    }

    def parseJava(File sourceFile, Reader input, ClassMetaDataRepository<ClassMetaData> repository) {
        SourceBuffer sourceBuffer = new SourceBuffer();
        UnicodeEscapingReader unicodeReader = new UnicodeEscapingReader(input, sourceBuffer);
        JavaLexer lexer = new JavaLexer(unicodeReader);
        unicodeReader.setLexer(lexer);
        JavaRecognizer parser = JavaRecognizer.make(lexer);
        parser.setSourceBuffer(sourceBuffer);
        String[] tokenNames = parser.getTokenNames();

        parser.compilationUnit();
        AST ast = parser.getAST();

        // modify the Java AST into a Groovy AST (just token types)
        Visitor java2groovyConverter = new Java2GroovyConverter(tokenNames);
        AntlrASTProcessor java2groovyTraverser = new PreOrderTraversal(java2groovyConverter);
        java2groovyTraverser.process(ast);

        def visitor = new SourceMetaDataVisitor(sourceBuffer, repository, false)
        AntlrASTProcessor traverser = new SourceCodeTraversal(visitor);
        traverser.process(ast);
        visitor.complete()
    }

    def parseGroovy(File sourceFile, Reader input, ClassMetaDataRepository<ClassMetaData> repository) {
        SourceBuffer sourceBuffer = new SourceBuffer();
        UnicodeEscapingReader unicodeReader = new UnicodeEscapingReader(input, sourceBuffer);
        GroovyLexer lexer = new GroovyLexer(unicodeReader);
        unicodeReader.setLexer(lexer);
        GroovyRecognizer parser = GroovyRecognizer.make(lexer);
        parser.setSourceBuffer(sourceBuffer);

        parser.compilationUnit();
        AST ast = parser.getAST();

        def visitor = new SourceMetaDataVisitor(sourceBuffer, repository, true)
        AntlrASTProcessor traverser = new SourceCodeTraversal(visitor);
        traverser.process(ast);
        visitor.complete()
    }

    def resolve(ClassMetaData classMetaData, TypeNameResolver resolver) {
        try {
            if (classMetaData.superClassName) {
                classMetaData.superClassName = resolver.resolve(classMetaData.superClassName, classMetaData)
            }
            for (int i = 0; i < classMetaData.interfaceNames.size(); i++) {
                classMetaData.interfaceNames[i] = resolver.resolve(classMetaData.interfaceNames[i], classMetaData)
            }
            classMetaData.declaredProperties.each { PropertyMetaData prop ->
                resolver.resolve(prop.type, classMetaData)
            }
            classMetaData.declaredMethods.each { MethodMetaData method ->
                resolver.resolve(method.returnType, classMetaData)
                method.parameters.each { ParameterMetaData parameter ->
                    resolver.resolve(parameter.type, classMetaData)
                }
            }
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve types in class '$classMetaData.className'.", e)
        }
    }
}