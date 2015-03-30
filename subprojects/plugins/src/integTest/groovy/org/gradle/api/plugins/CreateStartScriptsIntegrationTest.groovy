/*
 * Copyright 2015 the original author or authors.
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

package org.gradle.api.plugins

import org.gradle.integtests.fixtures.AbstractIntegrationSpec

class CreateStartScriptsIntegrationTest extends AbstractIntegrationSpec {
    def "can use default start script generators"() {
        given:
        buildFile << '''
task createStartScripts(type: CreateStartScripts) {
    outputDir = file('build/sample')
    mainClassName = 'org.gradle.test.Main'
    applicationName = 'myApp'
    classpath = files('path/to/some.jar')
}
'''
        when:
        succeeds('createStartScripts')

        then:
        File unixStartScript = file('build/sample/myApp')
        unixStartScript.exists()
        unixStartScript.text == expectedGeneratedUnixScript
        File windowsStartScript = file('build/sample/myApp.bat')
        windowsStartScript.exists()
        windowsStartScript.text.replaceAll("\r", "") == expectedGeneratedWindowsScript
    }

    def "can change template file for default start script generators"() {
        given:
        file('customUnixStartScript.txt') << '${applicationName} start up script for UN*X'
        file('customWindowsStartScript.txt') << '${applicationName} start up script for Windows'

        buildFile << '''
task createStartScripts(type: CreateStartScripts) {
    outputDir = file('build/sample')
    mainClassName = 'org.gradle.test.Main'
    applicationName = 'myApp'
    classpath = files('path/to/some.jar')
    unixStartScriptGenerator.template = new FileReader('customUnixStartScript.txt')
    windowsStartScriptGenerator.template = new FileReader('customWindowsStartScript.txt')
}
'''
        when:
        succeeds('createStartScripts')

        then:
        File unixStartScript = file('build/sample/myApp')
        unixStartScript.exists()
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = file('build/sample/myApp.bat')
        windowsStartScript.exists()
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    def "can use custom start script generators"() {
        given:
        buildFile << '''
task createStartScripts(type: CreateStartScripts) {
    outputDir = file('build/sample')
    mainClassName = 'org.gradle.test.Main'
    applicationName = 'myApp'
    classpath = files('path/to/Jar.jar')
    unixStartScriptGenerator = new CustomUnixStartScriptGenerator()
    windowsStartScriptGenerator = new CustomWindowsStartScriptGenerator()
}

class CustomUnixStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            destination << "\${details.applicationName} start up script for UN*X"
        } finally {
            destination.close()
        }
    }
}

class CustomWindowsStartScriptGenerator implements ScriptGenerator<JavaAppStartScriptGenerationDetails> {
    void generateScript(JavaAppStartScriptGenerationDetails details, Writer destination) {
        try {
            destination << "\${details.applicationName} start up script for Windows"
        } finally {
            destination.close()
        }
    }
}
'''

        when:
        succeeds('createStartScripts')

        then:
        File unixStartScript = file('build/sample/myApp')
        unixStartScript.exists()
        unixStartScript.text == 'myApp start up script for UN*X'
        File windowsStartScript = file('build/sample/myApp.bat')
        windowsStartScript.exists()
        windowsStartScript.text == 'myApp start up script for Windows'
    }

    private String getExpectedGeneratedUnixScript() {
        """#!/usr/bin/env bash

##############################################################################
##
##  myApp start up script for UN*X
##
##############################################################################

# Add default JVM options here. You can also use JAVA_OPTS and MY_APP_OPTS to pass JVM options to this script.
DEFAULT_JVM_OPTS=""

APP_NAME="myApp"
APP_BASE_NAME=`basename "\$0"`

# Use the maximum available, or set MAX_FD != -1 to use that value.
MAX_FD="maximum"

warn ( ) {
    echo "\$*"
}

die ( ) {
    echo
    echo "\$*"
    echo
    exit 1
}

# OS specific support (must be 'true' or 'false').
cygwin=false
msys=false
darwin=false
case "`uname`" in
  CYGWIN* )
    cygwin=true
    ;;
  Darwin* )
    darwin=true
    ;;
  MINGW* )
    msys=true
    ;;
esac

# For Cygwin, ensure paths are in UNIX format before anything is touched.
if \$cygwin ; then
    [ -n "\$JAVA_HOME" ] && JAVA_HOME=`cygpath --unix "\$JAVA_HOME"`
fi

# Attempt to set APP_HOME
# Resolve links: \$0 may be a link
PRG="\$0"
# Need this for relative symlinks.
while [ -h "\$PRG" ] ; do
    ls=`ls -ld "\$PRG"`
    link=`expr "\$ls" : '.*-> \\(.*\\)\$'`
    if expr "\$link" : '/.*' > /dev/null; then
        PRG="\$link"
    else
        PRG=`dirname "\$PRG"`"/\$link"
    fi
done
SAVED="`pwd`"
cd "`dirname \\"\$PRG\\"`/.." >&-
APP_HOME="`pwd -P`"
cd "\$SAVED" >&-

CLASSPATH=\$APP_HOME/lib/some.jar

# Determine the Java command to use to start the JVM.
if [ -n "\$JAVA_HOME" ] ; then
    if [ -x "\$JAVA_HOME/jre/sh/java" ] ; then
        # IBM's JDK on AIX uses strange locations for the executables
        JAVACMD="\$JAVA_HOME/jre/sh/java"
    else
        JAVACMD="\$JAVA_HOME/bin/java"
    fi
    if [ ! -x "\$JAVACMD" ] ; then
        die "ERROR: JAVA_HOME is set to an invalid directory: \$JAVA_HOME

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
    fi
else
    JAVACMD="java"
    which java >/dev/null 2>&1 || die "ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.

Please set the JAVA_HOME variable in your environment to match the
location of your Java installation."
fi

# Increase the maximum file descriptors if we can.
if [ "\$cygwin" = "false" -a "\$darwin" = "false" ] ; then
    MAX_FD_LIMIT=`ulimit -H -n`
    if [ \$? -eq 0 ] ; then
        if [ "\$MAX_FD" = "maximum" -o "\$MAX_FD" = "max" ] ; then
            MAX_FD="\$MAX_FD_LIMIT"
        fi
        ulimit -n \$MAX_FD
        if [ \$? -ne 0 ] ; then
            warn "Could not set maximum file descriptor limit: \$MAX_FD"
        fi
    else
        warn "Could not query maximum file descriptor limit: \$MAX_FD_LIMIT"
    fi
fi

# For Darwin, add options to specify how the application appears in the dock
if \$darwin; then
    GRADLE_OPTS="\$GRADLE_OPTS \\"-Xdock:name=\$APP_NAME\\" \\"-Xdock:icon=\$APP_HOME/media/gradle.icns\\""
fi

# For Cygwin, switch paths to Windows format before running java
if \$cygwin ; then
    APP_HOME=`cygpath --path --mixed "\$APP_HOME"`
    CLASSPATH=`cygpath --path --mixed "\$CLASSPATH"`

    # We build the pattern for arguments to be converted via cygpath
    ROOTDIRSRAW=`find -L / -maxdepth 1 -mindepth 1 -type d 2>/dev/null`
    SEP=""
    for dir in \$ROOTDIRSRAW ; do
        ROOTDIRS="\$ROOTDIRS\$SEP\$dir"
        SEP="|"
    done
    OURCYGPATTERN="(^(\$ROOTDIRS))"
    # Add a user-defined pattern to the cygpath arguments
    if [ "\$GRADLE_CYGPATTERN" != "" ] ; then
        OURCYGPATTERN="\$OURCYGPATTERN|(\$GRADLE_CYGPATTERN)"
    fi
    # Now convert the arguments - kludge to limit ourselves to /bin/sh
    i=0
    for arg in "\$@" ; do
        CHECK=`echo "\$arg"|egrep -c "\$OURCYGPATTERN" -`
        CHECK2=`echo "\$arg"|egrep -c "^-"`                                 ### Determine if an option

        if [ \$CHECK -ne 0 ] && [ \$CHECK2 -eq 0 ] ; then                    ### Added a condition
            eval `echo args\$i`=`cygpath --path --ignore --mixed "\$arg"`
        else
            eval `echo args\$i`="\\"\$arg\\""
        fi
        i=\$((i+1))
    done
    case \$i in
        (0) set -- ;;
        (1) set -- "\$args0" ;;
        (2) set -- "\$args0" "\$args1" ;;
        (3) set -- "\$args0" "\$args1" "\$args2" ;;
        (4) set -- "\$args0" "\$args1" "\$args2" "\$args3" ;;
        (5) set -- "\$args0" "\$args1" "\$args2" "\$args3" "\$args4" ;;
        (6) set -- "\$args0" "\$args1" "\$args2" "\$args3" "\$args4" "\$args5" ;;
        (7) set -- "\$args0" "\$args1" "\$args2" "\$args3" "\$args4" "\$args5" "\$args6" ;;
        (8) set -- "\$args0" "\$args1" "\$args2" "\$args3" "\$args4" "\$args5" "\$args6" "\$args7" ;;
        (9) set -- "\$args0" "\$args1" "\$args2" "\$args3" "\$args4" "\$args5" "\$args6" "\$args7" "\$args8" ;;
    esac
fi

# Split up the JVM_OPTS And MY_APP_OPTS values into an array, following the shell quoting and substitution rules
function splitJvmOpts() {
    JVM_OPTS=("\$@")
}
eval splitJvmOpts \$DEFAULT_JVM_OPTS \$JAVA_OPTS \$MY_APP_OPTS


exec "\$JAVACMD" "\${JVM_OPTS[@]}" -classpath "\$CLASSPATH" org.gradle.test.Main "\$@"
"""
    }

    private String getExpectedGeneratedWindowsScript() {
        """@if "%DEBUG%" == "" @echo off
@rem ##########################################################################
@rem
@rem  myApp startup script for Windows
@rem
@rem ##########################################################################

@rem Set local scope for the variables with windows NT shell
if "%OS%"=="Windows_NT" setlocal

@rem Add default JVM options here. You can also use JAVA_OPTS and MY_APP_OPTS to pass JVM options to this script.
set DEFAULT_JVM_OPTS=

set DIRNAME=%~dp0
if "%DIRNAME%" == "" set DIRNAME=.
set APP_BASE_NAME=%~n0
set APP_HOME=%DIRNAME%..

@rem Find java.exe
if defined JAVA_HOME goto findJavaFromJavaHome

set JAVA_EXE=java.exe
%JAVA_EXE% -version >NUL 2>&1
if "%ERRORLEVEL%" == "0" goto init

echo.
echo ERROR: JAVA_HOME is not set and no 'java' command could be found in your PATH.
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:findJavaFromJavaHome
set JAVA_HOME=%JAVA_HOME:"=%
set JAVA_EXE=%JAVA_HOME%/bin/java.exe

if exist "%JAVA_EXE%" goto init

echo.
echo ERROR: JAVA_HOME is set to an invalid directory: %JAVA_HOME%
echo.
echo Please set the JAVA_HOME variable in your environment to match the
echo location of your Java installation.

goto fail

:init
@rem Get command-line arguments, handling Windowz variants

if not "%OS%" == "Windows_NT" goto win9xME_args
if "%@eval[2+2]" == "4" goto 4NT_args

:win9xME_args
@rem Slurp the command line arguments.
set CMD_LINE_ARGS=
set _SKIP=2

:win9xME_args_slurp
if "x%~1" == "x" goto execute

set CMD_LINE_ARGS=%*
goto execute

:4NT_args
@rem Get arguments from the 4NT Shell from JP Software
set CMD_LINE_ARGS=%\$

:execute
@rem Setup the command line

set CLASSPATH=%APP_HOME%\\lib\\some.jar

@rem Execute myApp
"%JAVA_EXE%" %DEFAULT_JVM_OPTS% %JAVA_OPTS% %MY_APP_OPTS%  -classpath "%CLASSPATH%" org.gradle.test.Main %CMD_LINE_ARGS%

:end
@rem End local scope for the variables with windows NT shell
if "%ERRORLEVEL%"=="0" goto mainEnd

:fail
rem Set variable MY_APP_EXIT_CONSOLE if you need the _script_ return code instead of
rem the _cmd.exe /c_ return code!
if  not "" == "%MY_APP_EXIT_CONSOLE%" exit 1
exit /b 1

:mainEnd
if "%OS%"=="Windows_NT" endlocal

:omega
"""
    }
}
