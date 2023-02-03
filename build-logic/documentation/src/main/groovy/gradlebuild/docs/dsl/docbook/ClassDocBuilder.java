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

package gradlebuild.docs.dsl.docbook;

import gradlebuild.docs.dsl.docbook.model.ClassDoc;

public class ClassDocBuilder {
    private final ClassDocCommentBuilder commentBuilder;
    private final ClassDocPropertiesBuilder propertiesBuilder;
    private final ClassDocMethodsBuilder methodsBuilder;
    private final ClassDocExtensionsBuilder extensionsBuilder;
    private final ClassDocSuperTypeBuilder superTypeBuilder;
    private final GenerationListener listener = new DefaultGenerationListener();

    public ClassDocBuilder(DslDocModel model, JavadocConverter javadocConverter) {
        commentBuilder = new ClassDocCommentBuilder(javadocConverter, listener);
        propertiesBuilder = new ClassDocPropertiesBuilder(javadocConverter, listener);
        methodsBuilder = new ClassDocMethodsBuilder(javadocConverter, listener);
        extensionsBuilder = new ClassDocExtensionsBuilder(model, listener);
        superTypeBuilder = new ClassDocSuperTypeBuilder(model, listener);
    }

    void build(ClassDoc classDoc) {
        listener.start(String.format("class %s", classDoc.getName()));
        try {
            superTypeBuilder.build(classDoc);
            commentBuilder.build(classDoc);
            propertiesBuilder.build(classDoc);
            methodsBuilder.build(classDoc);
            extensionsBuilder.build(classDoc);
            classDoc.mergeContent();
        } finally {
            listener.finish();
        }
    }
}
