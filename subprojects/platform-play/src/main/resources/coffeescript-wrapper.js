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
            path = require("path");

    var target = args[3];
    var source = args[2];

    //console.log('target ' + target);
    //console.log('source ' + source);

    function throwIfErr(e) {
        if (e) throw e;
    }

    fs.mkdirParent = function(dirPath, mode, callback) {
        //Call the standard fs.mkdir
        fs.mkdir(dirPath, mode, function(error) {
            //When it fail in this way, do the custom steps
            if (error && error.errno === 34) {
                //Create all the parents recursively
                fs.mkdirParent(path.dirname(dirPath), mode, callback);
                //And then the directory
                fs.mkdirParent(dirPath, mode, callback);
            }
            //Manually run the callback since we used our own callback to do all these
            callback && callback(error);
        });
    };

    fs.readFile(source, "utf8", function (e, contents) {
        throwIfErr(e);

        var compileResult = CoffeeScript.compile(contents, null);

        fs.mkdirParent(path.dirname(target), function(err) {
            if (err && err.code != 'EEXIST') {
                throwIfErr(err);
            }

            var js = compileResult.js;
            if (js === undefined) {
                js = compileResult;
            }

            fs.writeFile(target, js, "utf8");
        });
    });
})();
