/*
 * Copyright 2017 the original author or authors.
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

package org.gradle.play.internal.twirl;

import org.gradle.language.twirl.TwirlImports;
import org.gradle.language.twirl.TwirlTemplateFormat;
import org.gradle.scala.internal.reflect.ScalaCodecMapper;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;
import org.gradle.util.CollectionUtils;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

class TwirlCompilerAdapterV13X extends TwirlCompilerAdapterV10X {

    // Also available via play.japi.twirl.compiler.TwirlCompiler.DEFAULT_IMPORTS but we would have to grab it via reflection
    private static final List<String> DEFAULT_TEMPLATE_IMPORTS = Collections.unmodifiableList(
        Arrays.asList(
            // Based on https://github.com/playframework/twirl/blob/1.3.13/compiler/src/main/scala/play/twirl/compiler/TwirlCompiler.scala#L156    
            "_root_.play.twirl.api.TwirlFeatureImports._", 
            "_root_.play.twirl.api.TwirlHelperImports._",  
            "_root_.play.twirl.api.Html",  
            "_root_.play.twirl.api.JavaScript",    
            "_root_.play.twirl.api.Txt",   
            "_root_.play.twirl.api.Xml"    
        ));

    public TwirlCompilerAdapterV13X(String twirlVersion, String scalaVersion, VersionedPlayTwirlAdapter playTwirlAdapter) {
        super(twirlVersion, scalaVersion, playTwirlAdapter);
    }

    @Override
    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        // We could do this in Java, which would be easier. However, Twirl only has a Java interface in version 1.3+
        // If we used Java here then Gradle's TwirlCompiler would need to support both ScalaMethod for Twirl 1.0-1.2 and Java's Method for Twirl 1.3+
        // Method definition: https://github.com/playframework/twirl/blob/1.3.12/compiler/src/main/scala/play/twirl/compiler/TwirlCompiler.scala#L167
        return ScalaReflectionUtil.scalaMethod(
            cl,
            "play.twirl.compiler.TwirlCompiler",
            "compile",
            File.class,
            File.class,
            File.class,
            String.class,
            cl.loadClass("scala.collection.Seq"),
            cl.loadClass("scala.collection.Seq"),
            cl.loadClass(ScalaCodecMapper.getClassName()),
            boolean.class
        );
    }

    @Override
    public Object[] createCompileParameters(ClassLoader cl, File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultPlayImports, TwirlTemplateFormat templateFormat, List<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        final List<String> defaultImports = new ArrayList<String>(DEFAULT_TEMPLATE_IMPORTS);
        defaultImports.addAll(playTwirlAdapter.getDefaultImports(defaultPlayImports));
        return new Object[]{
            file,
            sourceDirectory,
            destinationDirectory,
            templateFormat.getFormatType(),
            toScalaSeq(CollectionUtils.flattenCollections(defaultImports, additionalImports, templateFormat.getTemplateImports()), cl),
            toScalaSeq(Collections.emptyList(), cl),
            ScalaCodecMapper.create(cl, "UTF-8"),
            isInclusiveDots(),
        };
    }

    private Object toScalaSeq(Collection<?> list, ClassLoader classLoader) {
        ScalaMethod method = ScalaReflectionUtil.scalaMethod(classLoader, "scala.collection.JavaConversions", "asScalaBuffer", List.class);
        return method.invoke(list);
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<String> getDependencyNotation() { 
        if (scalaVersion.startsWith("2.12")) {
            // We need scala.util.parsing.input.Positional
            return (List<String>) CollectionUtils.flattenCollections(super.getDependencyNotation(), "org.scala-lang.modules:scala-parser-combinators_2.12:1.0.6");
        } else {
            return super.getDependencyNotation();
        }
    }

}
