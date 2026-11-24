@echo off
@rem
@rem Copyright 2026 the original author or authors.
@rem
@rem Licensed under the Apache License, Version 2.0 (the "License");
@rem you may not use this file except in compliance with the License.
@rem You may obtain a copy of the License at
@rem
@rem      http://www.apache.org/licenses/LICENSE-2.0
@rem
@rem Unless required by applicable law or agreed to in writing, software
@rem distributed under the License is distributed on an "AS IS" BASIS,
@rem WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
@rem See the License for the specific language governing permissions and
@rem limitations under the License.

@rem Reports Maven state left in the user home as a build problem unless CHECK_CLEAN_USER_HOME=false.
@rem A failing post-build hook does not fail the build, so this emits a buildProblem service message.

for %%D in (".m2\repository" ".m2\.gradle-enterprise" ".m2\.develocity") do call :check "%USERPROFILE%\%%~D"
exit /b 0

:check
if not exist "%~1" exit /b 0
dir /B /A "%~1" | findstr "^" >nul
if errorlevel 1 exit /b 0
tree /F "%~1"
echo %~1 was polluted during the build
if /I not "%CHECK_CLEAN_USER_HOME%"=="false" echo ##teamcity[buildProblem description='%~1 was polluted during the build']
exit /b 0
