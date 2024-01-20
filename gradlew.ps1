#
# Copyright 2024 the original author or authors.
#
# Licensed under the Apache License, Version 2.0 (the "License");
# you may not use this file except in compliance with the License.
# You may obtain a copy of the License at
#
#      https://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.
#

##############################################################################
#
#   Gradle startup script for Windows
#
##############################################################################

$APP_BASE_NAME = Split-Path -Path $PSCommandPath -LeafBase
$APP_HOME = Split-Path -Path $PSCommandPath -Parent

$DEFAULT_JVM_OPTS = '-Dfile.encoding=UTF-8', '-Xmx64m', '-Xms64m'

if ($null -ne $Env:JAVA_HOME) {
    $JAVA_EXE = Join-Path -Path $Env:JAVA_HOME -ChildPath 'bin' -AdditionalChildPath 'java.exe'

    if (-not (Test-Path -Path $JAVA_EXE -PathType Leaf)) {
        Write-Error -Message 'Please set the JAVA_HOME variable in your environment to match the location of your Java installation.'
        throw [System.IO.FileNotFoundException] "JAVA_HOME is set to an invalid directory: $Env:JAVA_HOME."
    }
}
else {
    $JAVA_EXE = 'java.exe'

    if ($null -eq $(Get-Command -Name $JAVA_EXE -ErrorAction Ignore)) {
        Write-Error -Message 'Please set the JAVA_HOME variable in your environment to match the location of your Java installation.'
        throw [System.IO.FileNotFoundException] "JAVA_HOME is not set and no 'java' command could be found in your PATH."
    }
}

$CLASSPATH = Join-Path -Path $APP_HOME -ChildPath 'gradle' -AdditionalChildPath 'wrapper', 'gradle-wrapper.jar'

& $JAVA_EXE $DEFAULT_JVM_OPTS $Env:JAVA_OPTS $Env:GRADLE_OPTS "-Dorg.gradle.appname=$APP_BASE_NAME" -classpath $CLASSPATH 'org.gradle.wrapper.GradleWrapperMain' $args
