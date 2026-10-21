param(
    [Parameter(Mandatory=$true)][string]$GameDirectory
)
$ErrorActionPreference = 'Stop'
$project = Split-Path $PSScriptRoot -Parent
$game = (Resolve-Path -LiteralPath $GameDirectory).Path
$stage = Join-Path $project 'source/build/gold-console'
$system = Join-Path $stage 'System'
$classes = Join-Path $stage 'UnrealGoldAndroid/Classes'
if (!(Test-Path -LiteralPath (Join-Path $game 'System/UCC.exe'))) {
    throw 'Provide an Unreal Gold 226 directory containing System/UCC.exe and its DLLs.'
}
New-Item -ItemType Directory -Force $system, $classes | Out-Null
Copy-Item -Path (Join-Path $game 'System/*') -Destination $system -Force
Copy-Item -Path (Join-Path $project 'source/app/src/main/gold_console/Classes/*.uc') -Destination $classes -Force
$ini = [IO.File]::ReadAllText((Join-Path $game 'System/Unreal.ini'))
if (!$ini.Contains('EditPackages=UPak')) { throw 'The Gold configuration must include EditPackages=UPak.' }
$ini = $ini.Replace('EditPackages=UPak', "EditPackages=UPak`r`nEditPackages=UnrealGoldAndroid")
$paths = ''
foreach ($entry in @('Textures/*.utx', 'Textures/UPak/*.utx', 'Sounds/*.uax', 'Sounds/int/*.uax', 'Music/*.umx', 'Music/UPak/*.umx')) {
    $paths += 'Paths=' + (Join-Path $game $entry) + "`r`n"
}
$ini = $ini.Replace('[Core.System]', "[Core.System]`r`n$paths")
[IO.File]::WriteAllText((Join-Path $system 'ClassicConsole.ini'), $ini, [Text.Encoding]::GetEncoding(1252))
$output = Join-Path $system 'UnrealGoldAndroid.u'
if (Test-Path -LiteralPath $output) { Remove-Item -LiteralPath $output }
Push-Location $system
try {
    & '.\UCC.exe' make ini=ClassicConsole.ini
    if ($LASTEXITCODE -ne 0 -or !(Test-Path -LiteralPath $output)) { throw 'UnrealScript compilation failed.' }
} finally { Pop-Location }
$bytes = [IO.File]::ReadAllBytes($output)
if ([BitConverter]::ToUInt16($bytes, 4) -ne 68) { throw 'Expected a version 68 Unreal Gold 226 package.' }
$target = Join-Path $project 'source/app/src/main/assets/gold_patches/System/UnrealGoldAndroid.u'
New-Item -ItemType Directory -Force (Split-Path $target -Parent) | Out-Null
Copy-Item -LiteralPath $output -Destination $target -Force
