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

package org.gradle.play.integtest.fixtures.app

import org.gradle.integtests.fixtures.SourceFile

abstract class BasicPlayApp extends PlayApp{


    List<SourceFile> getConfSources() {
        return [
                sourceFile("conf", "application.conf", """
application.secret="TY9[b`xw2MeXUt;M<i_B0kUKm8/?PD1cS1WhFYyZ[1^6`Apew34q6DyNL=UqG/1l"
application.langs="en"

# Root logger:
logger.root=ERROR

# Logger used by the framework:
logger.play=INFO

# Logger provided to your application:
logger.application=DEBUG
""")
        ]
    }

    @Override
    List<SourceFile> getAssetSources() {
        return [
                sourceFile("public/images", "favicon.svg", """
<?xml version="1.0" standalone="no"?>
<!DOCTYPE svg PUBLIC "-//W3C//DTD SVG 20010904//EN"
 "http://www.w3.org/TR/2001/REC-SVG-20010904/DTD/svg10.dtd">
<svg version="1.0" xmlns="http://www.w3.org/2000/svg"
 width="16.000000pt" height="16.000000pt" viewBox="0 0 16.000000 16.000000"
 preserveAspectRatio="xMidYMid meet">
<g transform="translate(0.000000,16.000000) scale(0.100000,-0.100000)"
fill="#000000" stroke="none">
</g>
</svg>"""),

                sourceFile("public/javascripts", "hello.js", """
                    if (window.console) {
                        console.log("Welcome to your Play application's JavaScript!");
                    }"""),

                sourceFile("public/stylesheets", "main.css", "")
        ]
    }
}
