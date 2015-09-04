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

package org.gradle.play.tasks

import org.gradle.integtests.fixtures.AbstractIntegrationSpec
import org.gradle.test.fixtures.archive.JarTestFixture
import org.gradle.test.fixtures.file.TestFile
import org.gradle.util.Requires
import org.gradle.util.TestPrecondition
import org.gradle.util.TextUtil

@Requires(TestPrecondition.JDK7_OR_LATER)
abstract class AbstractJavaScriptMinifyIntegrationTest extends AbstractIntegrationSpec {

    def setup() {
        settingsFile << """ rootProject.name = 'js-play-app' """
    }

    abstract String getDefaultSourceSet();

    JarTestFixture getAssetsJar() {
        jar("build/playBinary/lib/js-play-app-assets.jar")
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    TestFile assets(String fileName) {
        file("app/assets/${fileName}")
    }

    TestFile copied(String fileName) {
        return minified(fileName)
    }

    TestFile copied(String sourceSet, String fileName) {
        return minified(sourceSet, fileName)
    }

    TestFile minified(String fileName) {
        return minified(getDefaultSourceSet(), fileName)
    }

    TestFile minified(String sourceSet, String fileName) {
        file("build/playBinary/src/minifyPlayBinary${sourceSet}/${fileName}")
    }

    void matchesExpected(File file) {
        assert TextUtil.normaliseLineSeparators(file.text) == TextUtil.normaliseLineSeparators(expectedMinifiedJavaScript())
    }

    void matchesExpected(String fileName) {
        matchesExpected(getDefaultSourceSet(), fileName)
    }

    void matchesExpected(String sourceSet, String fileName) {
        assert minified(sourceSet, fileName).exists()
        matchesExpected(minified(sourceSet, fileName))
    }

    void matchesExpectedRaw(File file) {
        assert compareWithoutWhiteSpace(file.text, expectedJavaScript())
    }

    boolean compareWithoutWhiteSpace(String string1, String string2) {
        return withoutWhiteSpace(string1) == withoutWhiteSpace(string2)
    }

    def withoutWhiteSpace(String string) {
        return string.replaceAll("\\s+", " ");
    }

    def withJavaScriptSource(String path) {
        withJavaScriptSource(file(path))
    }

    def withJavaScriptSource(File file) {
        file << expectedJavaScript()
    }

    def expectedJavaScript() {
        return """(function() {
var cubes, list, math, num, number, opposite, race, square,
  __slice = [].slice;

number = 42;

opposite = true;

if (opposite) {
  number = -42;
}

square = function(x) {
  return x * x;
};

list = [1, 2, 3, 4, 5];

math = {
  root: Math.sqrt,
  square: square,
  cube: function(x) {
    return x * square(x);
  }
};

race = function() {
  var runners, winner;
  winner = arguments[0], runners = 2 <= arguments.length ? __slice.call(arguments, 1) : [];
  return print(winner, runners);
};

if (typeof elvis !== "undefined" && elvis !== null) {
  alert("I knew it!");
}

cubes = (function() {
  var _i, _len, _results;
  _results = [];
  for (_i = 0, _len = list.length; _i < _len; _i++) {
    num = list[_i];
    _results.push(math.cube(num));
  }
  return _results;
})();
}).call(this);
"""
    }

    def expectedMinifiedJavaScript() {
        return "(function(){var c,e,f,b;b=function(a){return a*a};c=[1,2,3,4,5];e={root:Math.sqrt,square:b,cube:function(a){return a*b(a)}};\"undefined\"!==typeof elvis&&null!==elvis&&alert(\"I knew it!\");(function(){var a,b,d;d=[];a=0;for(b=c.length;a<b;a++)f=c[a],d.push(e.cube(f));return d})()}).call(this);"
    }
}
