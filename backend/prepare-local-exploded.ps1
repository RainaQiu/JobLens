param(
    [string]$ProjectRoot = "D:\eclipse-workspace\DistributedSystem\Project4Task2"
)

$backendRoot = Join-Path $ProjectRoot "backend"
$explodedRoot = Join-Path $backendRoot "target\ROOT"
$classesDir = Join-Path $explodedRoot "WEB-INF\classes"
$libDir = Join-Path $explodedRoot "WEB-INF\lib"
$servletApiJar = "C:\Users\Raina\.m2\repository\javax\servlet\javax.servlet-api\4.0.1\javax.servlet-api-4.0.1.jar"

if (-not (Test-Path $explodedRoot)) {
    throw "Missing exploded webapp at $explodedRoot. Build the IntelliJ artifact once before using this script."
}

if (-not (Test-Path $servletApiJar)) {
    throw "Missing servlet API jar at $servletApiJar."
}

New-Item -ItemType Directory -Force $classesDir | Out-Null

$classpathEntries = @($servletApiJar)
if (Test-Path $libDir) {
    $classpathEntries += Get-ChildItem $libDir -Filter *.jar | ForEach-Object { $_.FullName }
}
$classpath = [string]::Join(';', $classpathEntries)

$sourceFiles = Get-ChildItem (Join-Path $backendRoot "src\main\java") -Recurse -Filter *.java |
    ForEach-Object { $_.FullName }

if ($sourceFiles.Count -eq 0) {
    throw "No backend Java source files were found."
}

& javac -encoding UTF-8 -cp $classpath -d $classesDir $sourceFiles
if ($LASTEXITCODE -ne 0) {
    throw "javac failed with exit code $LASTEXITCODE."
}

Write-Host "Compiled backend classes into $classesDir"
