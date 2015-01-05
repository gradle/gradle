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
import org.gradle.util.TextUtil

class CoffeeScriptCompileIntegrationTest extends AbstractIntegrationSpec {
    def setup() {
        buildFile << """
            plugins {
                id 'play'
                id 'play-coffeescript'
            }

            repositories{
                jcenter()
                maven{
                    name = "typesafe-maven-release"
                    url = "https://repo.typesafe.com/typesafe/maven-releases"
                }
                maven {
                    name = "gradle-js"
                    url = "https://repo.gradle.org/gradle/javascript-public"
                }
            }
        """
    }

    def "compiles default coffeescript source set as part of play application build" () {
        when:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":processPlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        matchesExpected("test.js")
        matchesExpectedMinified("test.min.js")
        assetsJar.containsDescendants(
                "public/test.js",
                "public/test.min.js"
        )
    }

    def "compiles multiple coffeescript source sets as part of play application build" () {
        given:
        withCoffeeScriptSource(assets("test1.coffee"))
        withCoffeeScriptSource("src/play/extraCoffeeScript/xxx/test2.coffee")
        withCoffeeScriptSource("extra/a/b/c/test3.coffee")
        withJavaScriptSource('src/play/extraJavaScript/test/test4.js')
        withJavaScriptSource(assets("test5.js"))

        when:
        buildFile << """
            model {
                components {
                    play {
                        sources {
                            extraCoffeeScript(CoffeeScriptSourceSet)
                            anotherCoffeeScript(CoffeeScriptSourceSet) {
                                source.srcDir "extra"
                            }
                            extraJavaScript(JavaScriptSourceSet)
                        }
                    }
                }
            }
        """
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":processPlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":compilePlayBinaryExtraCoffeeScript",
                ":processPlayBinaryExtraCoffeeScript",
                ":minifyPlayBinaryExtraCoffeeScript",
                ":compilePlayBinaryAnotherCoffeeScript",
                ":processPlayBinaryAnotherCoffeeScript",
                ":minifyPlayBinaryAnotherCoffeeScript",
                ":processPlayBinaryJavaScriptAssets",
                ":minifyPlayBinaryJavaScriptAssets",
                ":processPlayBinaryExtraJavaScript",
                ":minifyPlayBinaryExtraJavaScript",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        matchesExpected("test1.js")
        matchesExpected("ExtraCoffeeScript", "xxx/test2.js")
        matchesExpected("AnotherCoffeeScript", "a/b/c/test3.js")
        matchesExpectedMinified("test1.min.js")
        matchesExpectedMinified("ExtraCoffeeScript", "xxx/test2.min.js")
        matchesExpectedMinified("AnotherCoffeeScript", "a/b/c/test3.min.js")
        assetsJar.containsDescendants(
                "public/test1.js",
                "public/xxx/test2.js",
                "public/a/b/c/test3.js",
                "public/test/test4.js",
                "public/test5.js",
                "public/test1.min.js",
                "public/xxx/test2.min.js",
                "public/a/b/c/test3.min.js",
                "public/test/test4.min.js",
                "public/test5.min.js"
        )
    }

    def "does not recompile when inputs and outputs are unchanged" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        succeeds "assemble"

        then:
        skipped(":compilePlayBinaryCoffeeScriptAssets",
                ":processPlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryJar",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "recompiles when inputs are changed" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        assets("test.coffee") << '\nalert "this is a change!"'
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":processPlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
    }

    def "recompiles when outputs are removed" () {
        given:
        withCoffeeScriptSource(assets("test.coffee"))
        succeeds "assemble"

        when:
        processed("test.js").delete()
        processedJS("test.js").delete()
        processedMin("test.min.js").delete()
        assetsJar.file.delete()
        succeeds "assemble"

        then:
        executedAndNotSkipped(
                ":compilePlayBinaryCoffeeScriptAssets",
                ":processPlayBinaryCoffeeScriptAssets",
                ":minifyPlayBinaryCoffeeScriptAssets",
                ":createPlayBinaryAssetsJar",
                ":playBinary")
        processed("test.js").exists()
    }

    def "cleans removed source file on compile" () {
        given:
        withCoffeeScriptSource(assets("test1.coffee"))
        def source2 = withCoffeeScriptSource(assets("test2.coffee"))

        when:
        succeeds "assemble"

        then:
        assetsJar.containsDescendants(
                "public/test1.js",
                "public/test2.js",
                "public/test1.min.js",
                "public/test2.min.js"
        )

        when:
        source2.delete()
        succeeds "assemble"

        then:
        ! processed("test2.js").exists()
        ! processedJS("test2.js").exists()
        ! processedMin("test2.js").exists()
        assetsJar.countFiles("public/test2.js") == 0
        assetsJar.countFiles("public/test2.min.js") == 0
    }

    def "produces sensible error on compile failure" () {
        given:
        assets("test1.coffee") << "if"

        when:
        fails "assemble"

        then:
        failure.assertHasDescription "Execution failed for task ':compilePlayBinaryCoffeeScriptAssets'."
        failure.assertHasCause "Failed to compile coffeescript file: test1.coffee"
        failure.assertHasCause "SyntaxError: unexpected if (coffee-script-js-1.8.0.js#10)"
    }

    JarTestFixture getAssetsJar() {
        jar("build/playBinary/lib/play-assets.jar")
    }

    JarTestFixture jar(String fileName) {
        new JarTestFixture(file(fileName))
    }

    TestFile processed(String sourceSet = "CoffeeScriptAssets", String fileName) {
        file("build/playBinary/src/compilePlayBinary${sourceSet}/${fileName}")
    }

    TestFile processedJS(String sourceSet = "CoffeeScriptAssets", String fileName) {
        file("build/playBinary/src/processPlayBinary${sourceSet}/${fileName}")
    }

    TestFile processedMin(String sourceSet = "CoffeeScriptAssets", String fileName) {
        file("build/playBinary/src/minifyPlayBinary${sourceSet}/${fileName}")
    }

    TestFile assets(String fileName) {
        file("app/assets/${fileName}")
    }

    boolean matchesExpected(String sourceSet = "CoffeeScriptAssets", String fileName) {
        return processed(sourceSet, fileName).exists() && compareWithoutWhiteSpace(processed(sourceSet, fileName).text, expectedJavaScript())
    }

    boolean matchesExpectedMinified(String sourceSet = "CoffeeScriptAssets", String fileName) {
        return processedMin(sourceSet, fileName).exists() && TextUtil.normaliseLineSeparators(processedMin(sourceSet, fileName).text) == TextUtil.normaliseLineSeparators(expectedMinifiedJavaScript())
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

    def withCoffeeScriptSource(String path) {
        withCoffeeScriptSource(file(path))
    }

    def withCoffeeScriptSource(File file) {
        file << coffeeScriptSource()
    }

    def coffeeScriptSource() {
        return """
# Assignment:
number   = 42
opposite = true

# Conditions:
number = -42 if opposite

# Functions:
square = (x) -> x * x

# Arrays:
list = [1, 2, 3, 4, 5]

# Objects:
math =
  root:   Math.sqrt
  square: square
  cube:   (x) -> x * square x

# Splats:
race = (winner, runners...) ->
  print winner, runners

# Existence:
alert "I knew it!" if elvis?

# Array comprehensions:
cubes = (math.cube num for num in list)"""
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
        return "(function(){var c,e,f,b;b=function(a){return a*a};c=[1,2,3,4,5];e={root:Math.sqrt,square:b,cube:function(a){return a*b(a)}};\"undefined\"!==typeof elvis&&null!==elvis&&alert(\"I knew it!\");(function(){var a,b,d;d=[];a=0;for(b=c.length;a<b;a++)f=c[a],d.push(e.cube(f));return d})()}).call(this);\n"
    }
}
