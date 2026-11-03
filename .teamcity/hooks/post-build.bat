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

@rem Post-build hook entry point on Windows (file name required by the teamcity-hooks-plugin).

call "%~dp0scripts\check-maven-home-not-polluted.bat"
call "%~dp0scripts\clean-maven-and-android-home.bat"
exit /b 0
