/*
 * Copyright 2012 the original author or authors.
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

package org.gradle.api.internal.tasks.scala;

import org.gradle.api.internal.tasks.compile.DefaultJavaCompileSpec;
import org.gradle.api.tasks.scala.ScalaCompileOptions;

import java.io.File;
import java.util.Map;

public class DefaultScalaJavaJointCompileSpec extends DefaultJavaCompileSpec implements ScalaJavaJointCompileSpec {
    private ScalaCompileOptions options;
    private Iterable<File> scalaClasspath;
    private Iterable<File> zincClasspath;
    private Map<File, File> analysisMap;

    public ScalaCompileOptions getScalaCompileOptions() {
        return options;
    }

    public void setScalaCompileOptions(ScalaCompileOptions options) {
        this.options = options;
    }

    public Iterable<File> getScalaClasspath() {
        return scalaClasspath;
    }

    public void setScalaClasspath(Iterable<File> scalaClasspath) {
        this.scalaClasspath = scalaClasspath;
    }

    public Iterable<File> getZincClasspath() {
        return zincClasspath;
    }

    public void setZincClasspath(Iterable<File> zincClasspath) {
        this.zincClasspath = zincClasspath;
    }

    public Map<File, File> getAnalysisMap() {
        return analysisMap;
    }

    public void setAnalysisMap(Map<File, File> analysisMap) {
        this.analysisMap = analysisMap;
    }
}
