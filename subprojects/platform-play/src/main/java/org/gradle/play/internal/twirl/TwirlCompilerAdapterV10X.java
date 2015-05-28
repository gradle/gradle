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

import org.gradle.scala.internal.reflect.ScalaCodecMapper;
import org.gradle.scala.internal.reflect.ScalaMethod;
import org.gradle.scala.internal.reflect.ScalaReflectionUtil;

import java.io.File;
import java.lang.reflect.InvocationTargetException;
import java.util.Arrays;
import java.util.List;

class TwirlCompilerAdapterV10X implements VersionedTwirlCompilerAdapter {

    private static final String DEFAULT_JAVA_IMPORTS =
              "import models._;"
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
              "import models._;"
            + "import controllers._;"
            + "import play.api.i18n._;"
            + "import play.api.mvc._;"
            + "import play.api.data._;"
            + "import views.html._;";

    private final String scalaVersion;
    private final String twirlVersion;

    public TwirlCompilerAdapterV10X(String twirlVersion, String scalaVersion) {
        this.scalaVersion = scalaVersion;
        this.twirlVersion = twirlVersion;
    }

    public ScalaMethod getCompileMethod(ClassLoader cl) throws ClassNotFoundException {
        return ScalaReflectionUtil.scalaMethod(
                cl,
                "play.twirl.compiler.TwirlCompiler",
                "compile",
                File.class,
                File.class,
                File.class,
                String.class,
                String.class,
                cl.loadClass(ScalaCodecMapper.getClassName()),
                boolean.class,
                boolean.class
        );
    }

    public Object[] createCompileParameters(ClassLoader cl, File file, File sourceDirectory, File destinationDirectory, boolean javaProject) throws ClassNotFoundException, NoSuchMethodException, InvocationTargetException, InstantiationException, IllegalAccessException {
        return new Object[] {
                file,
                sourceDirectory,
                destinationDirectory,
                "play.twirl.api.HtmlFormat",
                javaProject ? DEFAULT_JAVA_IMPORTS : DEFAULT_SCALA_IMPORTS,
                ScalaCodecMapper.create(cl, "UTF-8"),
                isInclusiveDots(),
                isUseOldParser()
        };
    }

    private boolean isInclusiveDots() {
        return false;
    }

    private boolean isUseOldParser() {
        return false;
    }

    public List<String> getClassLoaderPackages() {
        return Arrays.asList("play.twirl.compiler", "scala.io"); //scala.io is for Codec which is a parameter to twirl
    }

    public String getDependencyNotation() {
        return String.format("com.typesafe.play:twirl-compiler_%s:%s", scalaVersion, twirlVersion);
    }
}
