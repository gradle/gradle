param(
    [Parameter(Mandatory = $true)][string]$JavaHome,
    [Parameter(Mandatory = $true)][string]$OutDir,
    [int]$IntervalSeconds = 8
)

# Periodically capture a full thread dump (with lock info) of every running
# java.exe process. Intended to run detached, in parallel with a Gradle build,
# so that a hang during build shutdown (the build-event listener executor
# failing to drain within 60s) is captured before the process exits.

$ErrorActionPreference = "Continue"
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

$jcmd = Join-Path $JavaHome "bin\jcmd.exe"
$jstack = Join-Path $JavaHome "bin\jstack.exe"

while ($true) {
    $ts = Get-Date -Format "yyyyMMdd-HHmmss-fff"
    foreach ($proc in (Get-CimInstance Win32_Process -Filter "Name = 'java.exe'")) {
        $procId = $proc.ProcessId
        $file = Join-Path $OutDir "threaddump-$ts-pid$procId.txt"
        "==== pid=$procId time=$ts ====" | Out-File -FilePath $file -Encoding utf8
        ("cmdline: " + $proc.CommandLine) | Out-File -FilePath $file -Append -Encoding utf8
        "" | Out-File -FilePath $file -Append -Encoding utf8
        try {
            if (Test-Path $jcmd) {
                & $jcmd $procId Thread.print -l 2>&1 | Out-File -FilePath $file -Append -Encoding utf8
            } else {
                & $jstack -l $procId 2>&1 | Out-File -FilePath $file -Append -Encoding utf8
            }
        } catch {
            ("FAILED to dump pid {0}: {1}" -f $procId, $_) | Out-File -FilePath $file -Append -Encoding utf8
        }
    }
    Start-Sleep -Seconds $IntervalSeconds
}
