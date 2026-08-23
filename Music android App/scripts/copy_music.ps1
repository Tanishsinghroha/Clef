$files = Get-ChildItem 'd:\Projects\Music\Music_Files\*.mp3'
foreach ($f in $files) {
    $newName = $f.BaseName.ToLower() -replace '[^a-z0-9]', '_'
    $newName = $newName -replace '__+', '_'
    $newName = $newName.TrimStart('_').TrimEnd('_')
    if ($newName.Length -gt 80) { $newName = $newName.Substring(0, 80).TrimEnd('_') }
    $dest = "d:\Projects\Music\Music android App\app\src\main\res\raw\$newName.mp3"
    Write-Host "$($f.Name) -> $newName.mp3"
    Copy-Item $f.FullName -Destination $dest -Force
}
