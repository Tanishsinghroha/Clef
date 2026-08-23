Add-Type -AssemblyName System.Drawing
Get-ChildItem 'd:\Projects\Music\Music android App\app\src\main\res\drawable\*.*' | ForEach-Object {
    $img = [System.Drawing.Image]::FromFile($_.FullName)
    Write-Output "$($_.Name): $($img.Width)x$($img.Height)"
    $img.Dispose()
}
