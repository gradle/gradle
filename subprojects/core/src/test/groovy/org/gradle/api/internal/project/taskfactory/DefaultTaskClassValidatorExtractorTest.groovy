/*
 * Copyright 2016 the original author or authors.
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

package org.gradle.api.internal.project.taskfactory

import org.gradle.api.DefaultTask
import org.gradle.api.file.FileCollection
import spock.lang.Specification

import java.lang.annotation.Annotation

class DefaultTaskClassValidatorExtractorTest extends Specification {
    class TaskWithCustomAnnotation extends DefaultTask {
        @SearchPath FileCollection searchPath;
    }

    class SearchPathAnnotationHandler implements PropertyAnnotationHandler {
        private final UpdateAction configureAction

        SearchPathAnnotationHandler(UpdateAction configureAction) {
            this.configureAction = configureAction
        }

        @Override
        Class<? extends Annotation> getAnnotationType() {
            SearchPath
        }

        @Override
        void attachActions(TaskPropertyActionContext context) {
            assert context.isAnnotationPresent(SearchPath)
            context.configureAction = configureAction
        }
    }

    def "can use custom annotation processor"() {
        def configureAction = Mock(UpdateAction)
        def annotationHandler = new SearchPathAnnotationHandler(configureAction)
        def extractor = new DefaultTaskClassValidatorExtractor(annotationHandler)

        expect:
        def validator = extractor.extractValidator(TaskWithCustomAnnotation)
        validator.validatedProperties*.name as List == ["searchPath"]
        validator.validatedProperties[0].configureAction == configureAction
    }
}
