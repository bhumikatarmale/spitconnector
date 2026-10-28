# Check common JDK locations and add to session PATH if java is missing
if (-not (Get-Command java -ErrorAction SilentlyContinue)) {
    $candidates = @(
        "$env:JAVA_HOME\bin",
        (Get-Item "C:\Program Files\Microsoft\jdk*\bin" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName),
        (Get-Item "C:\Program Files\Eclipse Adoptium\jdk*\bin" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName),
        (Get-Item "C:\Program Files\Java\jdk*\bin" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName),
        (Get-Item "$env:USERPROFILE\.jdks\*\bin" -ErrorAction SilentlyContinue | Select-Object -ExpandProperty FullName)
    )
    foreach ($cand in $candidates) {
        if ($cand -and (Test-Path "$cand\java.exe")) {
            $env:PATH = "$cand;" + $env:PATH
            break
        }
    }
}

Set-Location "$PSScriptRoot\MiniProject"
& cmd.exe /c run-web.bat $args
