$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot
$output = Join-Path $project 'build/regression'
New-Item -ItemType Directory -Force -Path $output | Out-Null
$source = [IO.File]::ReadAllText((Join-Path $project 'app/src/main/java/com/fengluanchui/fengsuan/MainActivity.java'))
# Compile the exact production parser without requiring an Android runtime.
$start = $source.IndexOf('    private static class Parser {')
if ($start -lt 0) { throw 'Production parser was not found' }
$parser = $source.Substring($start).Trim()
$parser = $parser.Substring(0, $parser.LastIndexOf('}')).Replace('private static class Parser', 'static class Parser')
[IO.File]::WriteAllText((Join-Path $output 'ParserHost.java'), 'import java.util.Locale; public class ParserHost {' + [Environment]::NewLine + $parser + '}')
& javac -encoding UTF-8 -d $output (Join-Path $output 'ParserHost.java') (Join-Path $PSScriptRoot 'CalculatorRegression.java')
if ($LASTEXITCODE -ne 0) { throw 'Regression compilation failed' }
& java -cp $output CalculatorRegression
if ($LASTEXITCODE -ne 0) { throw 'Calculator regression failed' }
