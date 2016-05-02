/*
 * Copyright 2014 the original author or authors.
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
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;

class TwirlCompilerAdapterV22X implements VersionedTwirlCompilerAdapter {
    private static final Iterable<String> SHARED_PACKAGES = Arrays.asList("play.templates");

    // Based on https://github.com/playframework/playframework/blob/2.2.6/framework/src/sbt-plugin/src/main/scala/PlayKeys.scala
    private static final String DEFAULT_JAVA_IMPORTS =
              "import play.api.templates._;"
            + "import play.api.templates.PlayMagic._;"
            + "import models._;"
            + "import controllers._;"
            + "import java.lang._;"
            + "import java.util._;"
            + "import scala.collection.JavaConversions._;"
            + "import scala.collection.JavaConverters._;"
            + "import play.api.i18n._;"
            + "import play.core.j.PlayMagicForJava._;"
            + "import play.mvc._;"
            + "import play.data._;"
            + "import play.api.data.Field;"
            + "import play.mvc.Http.Context.Implicit._;"
            + "import views.html._;";

    private static final String DEFAULT_SCALA_IMPORTS =
              "import play.api.templates._;"
            + "import play.api.templates.PlayMagic._;"
            + "import models._;"
            + "import controllers._;"
            + "import play.api.i18n._;"
            + "import play.api.mvc._;"
            + "import play.api.data._;"
            + "import views.html._;";

    private final String twirlVersion;
    private final String scalaVersion;

    public TwirlCompilerAdapterV22X(String twirlVersion, String scalaVersion) {
        this.twirlVersion = twirlVersion;
        this.scalaVersion = scalaVersion;
    }

    @Override
    public ScalaMethod getCompileMethod(final ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
                cl,
                "play.templates.ScalaTemplateCompiler",
                "compile",
                File.class,
                File.class,
                File.class,
                String.class,
                String.class
        );
    }

    @Override
    public Object[] createCompileParameters(ClassLoader cl, File file, File sourceDirectory, File destinationDirectory, TwirlImports defaultImports) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[] {
                file,
                sourceDirectory,
                destinationDirectory,
                "play.api.templates.HtmlFormat",
                defaultImports == TwirlImports.JAVA ? DEFAULT_JAVA_IMPORTS : DEFAULT_SCALA_IMPORTS
        };
    }

    @Override
    public Iterable<String> getClassLoaderPackages() {
        return SHARED_PACKAGES;
    }

    @Override
    public String getDependencyNotation() {
        return "com.typesafe.play:templates-compiler_" + scalaVersion + ":" + twirlVersion;
    }
}
