/*
 * Copyright 2020 the original author or authors.
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
package gradlebuild.docs.dsl.source

import com.github.javaparser.JavaParser
import groovy.time.TimeCategory
import groovy.time.TimeDuration
import org.gradle.api.Action
import org.gradle.api.Transformer
import org.gradle.api.file.FileTree
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.CacheableTask
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.PathSensitive
import org.gradle.api.tasks.PathSensitivity
import org.gradle.api.tasks.SourceTask
import org.gradle.api.tasks.TaskAction
import gradlebuild.docs.DocGenerationException
import gradlebuild.docs.model.ClassMetaDataRepository
import gradlebuild.docs.model.SimpleClassMetaDataRepository

/**
 * Extracts meta-data from the Groovy and Java source files which make up the Gradle API. Persists the meta-data to a file
 * for later use in generating documentation for the DSL, such as by {@link gradlebuild.docs.dsl.docbook.AssembleDslDocTask}.
 */
@CacheableTask
abstract class ExtractDslMetaDataTask extends SourceTask {
    @OutputFile
    abstract RegularFileProperty getDestinationFile();

    /**
     * {@inheritDoc}
     */
    @Override
    @PathSensitive(PathSensitivity.NAME_ONLY)
    FileTree getSource() {
        return super.getSource();
    }

    @TaskAction
    def extract() {
        Date start = new Date()

        //parsing all input files into metadata
        //and placing them in the repository object
        SimpleClassMetaDataRepository<gradlebuild.docs.dsl.source.model.ClassMetaData> repository = new SimpleClassMetaDataRepository<gradlebuild.docs.dsl.source.model.ClassMetaData>()
        int counter = 0
        source.filter { File f -> f.name.endsWith(".java") || f.name.endsWith(".groovy") }.each { File f ->
            parse(f, repository)
            counter++
        }

        //updating/modifying the metadata and making sure every type reference across the metadata is fully qualified
        //so, the superClassName, interfaces and types needed by declared properties and declared methods will have fully qualified name
        TypeNameResolver resolver = new TypeNameResolver(repository)
        repository.each { name, metaData ->
            fullyQualifyAllTypeNames(metaData, resolver)
        }
        repository.store(destinationFile.get().asFile)

        Date stop = new Date()
        TimeDuration elapsedTime = TimeCategory.minus(stop, start)
        println "Parsed $counter classes in ${elapsedTime}"
    }

    def parse(File sourceFile, ClassMetaDataRepository<gradlebuild.docs.dsl.source.model.ClassMetaData> repository) {
        if (!sourceFile.name.endsWith('.java')) {
            throw new DocGenerationException("Parsing non-Java files is not supported: $sourceFile")
        }
        try {
            new JavaParser().parse(sourceFile).getResult().get().accept(new SourceMetaDataVisitor(), repository)
        } catch (Exception e) {
            throw new DocGenerationException("Could not parse '$sourceFile'.", e)
        }
    }

    def fullyQualifyAllTypeNames(gradlebuild.docs.dsl.source.model.ClassMetaData classMetaData, TypeNameResolver resolver) {
        try {
            classMetaData.resolveTypes(new Transformer<String, String>(){
                String transform(String i) {
                    return resolver.resolve(i, classMetaData)
                }
            })
            classMetaData.visitTypes(new Action<gradlebuild.docs.dsl.source.model.TypeMetaData>() {
                void execute(gradlebuild.docs.dsl.source.model.TypeMetaData t) {
                    resolver.resolve(t, classMetaData)
                }
            })
        } catch (Exception e) {
            throw new RuntimeException("Could not resolve types in class '$classMetaData.className'.", e)
        }
    }
}
