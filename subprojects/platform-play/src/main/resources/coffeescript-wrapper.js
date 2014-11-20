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

(function () {

    "use strict";

    var args = process.argv,
            fs = require("fs"),
            coffeeScript = require("coffee-script"),
            mkdirp = require("mkdirp"),
            path = require("path");

    var target = args[2];
    var source = args[3];

    function throwIfErr(e) {
        if (e) throw e;
    }

    fs.readFile(source, "utf8", function (e, contents) {
        throwIfErr(e);

        try {
            var compileResult = coffeeScript.compile(contents, null)

            mkdirp(path.dirname(target), function(e){
                throwIfErr(e)

                var js = compileResult.js;
                if (js === undefined) {
                    js = compileResult;
                }

                fs.writeFile(target, js, "utf8")
            })
        }
    })
})();
