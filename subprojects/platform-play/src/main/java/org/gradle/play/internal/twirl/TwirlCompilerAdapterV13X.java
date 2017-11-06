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

    private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("play.twirl.compiler", "scala.io", "scala.util.parsing.input", "scala.collection");

    // Default imports are based on:
    // https://github.com/playframework/playframework/blob/2.6.0/framework/src/build-link/src/main/java/play/TemplateImports.java
    private final static List<String> DEFAULT_JAVA_TEMPLATE_IMPORTS;
    private final static List<String> DEFAULT_SCALA_TEMPLATE_IMPORTS;

    private final static List<String> DEFAULT_TEMPLATE_IMPORTS = Collections.unmodifiableList(
        Arrays.asList(
            "models._",
            "controllers._",
            "play.api.i18n._",
            "play.api.templates.PlayMagic._",
            // Based on https://github.com/playframework/twirl/blob/1.3.13/compiler/src/main/scala/play/twirl/compiler/TwirlCompiler.scala#L156
            "_root_.play.twirl.api.TwirlFeatureImports._",
            "_root_.play.twirl.api.TwirlHelperImports._",
            "_root_.play.twirl.api.Html",
            "_root_.play.twirl.api.JavaScript",
            "_root_.play.twirl.api.Txt",
            "_root_.play.twirl.api.Xml"
        ));

    static {
        List<String> defaultJavaImports = new ArrayList<String>();
        defaultJavaImports.addAll(DEFAULT_TEMPLATE_IMPORTS);
        defaultJavaImports.add("java.lang._");
        defaultJavaImports.add("java.util._");
        defaultJavaImports.add("scala.collection.JavaConverters._");
        defaultJavaImports.add("play.core.j.PlayMagicForJava._");
        defaultJavaImports.add("play.mvc._");
        defaultJavaImports.add("play.libs.F");
        defaultJavaImports.add("play.api.data.Field");
        defaultJavaImports.add("play.mvc.Http.Context.Implicit._");
        defaultJavaImports.add("play.data._");
        defaultJavaImports.add("play.core.j.PlayFormsMagicForJava._");
        DEFAULT_JAVA_TEMPLATE_IMPORTS = Collections.unmodifiableList(defaultJavaImports);

        List<String> scalaImports = new ArrayList<String>();
        scalaImports.addAll(DEFAULT_TEMPLATE_IMPORTS);
        scalaImports.add("play.api.mvc._");
        scalaImports.add("play.api.data._");
        DEFAULT_SCALA_TEMPLATE_IMPORTS = Collections.unmodifiableList(scalaImports);
    }

    public TwirlCompilerAdapterV13X(String twirlVersion, String scalaVersion) {
        super(twirlVersion, scalaVersion);
    }

    @Override
    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        // https://github.com/playframework/twirl/blob/1.3.12/compiler/src/main/scala/play/twirl/compiler/TwirlCompiler.scala#L167
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
    public Object[] createCompileParameters(ClassLoader cl, final File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultImports, TwirlTemplateFormat templateFormat, List<String> additionalImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[]{
            file,
            sourceDirectory,
            destinationDirectory,
            templateFormat.getFormatType(),
            toScalaSeq(CollectionUtils.flattenCollections(getDefaultImports(defaultImports), additionalImports, templateFormat.getTemplateImports()), cl),
            toScalaSeq(Collections.emptyList(), cl),
            ScalaCodecMapper.create(cl, "UTF-8"),
            isInclusiveDots(),
        };
    }

    private Object toScalaSeq(Collection<?> list, ClassLoader classLoader) {
        ScalaMethod method = ScalaReflectionUtil.scalaMethod(classLoader, "scala.collection.JavaConversions", "asScalaBuffer", List.class);
        return method.invoke(list);
    }

    private boolean isInclusiveDots() {
        return false;
    }

    @Override
    public Iterable<String> getClassLoaderPackages() {
        return SHARED_PACKAGES;
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

    @Override
    protected Collection<String> getDefaultScalaImports() {
        return DEFAULT_SCALA_TEMPLATE_IMPORTS;
    }

    @Override
    protected Collection<String> getDefaultJavaImports() {
        return DEFAULT_JAVA_TEMPLATE_IMPORTS;
    }
}
