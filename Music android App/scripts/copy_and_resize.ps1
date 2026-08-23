$srcDir = 'd:\Projects\Music\Photots'
$dstDir = 'd:\Projects\Music\Music android App\app\src\main\res\drawable'

# Copy and rename files
Get-ChildItem -Path $srcDir -Filter "Pic *.jpg" | ForEach-Object {
    $newName = $_.Name.Replace('Pic ', 'pic_').ToLower()
    $dstPath = Join-Path -Path $dstDir -ChildPath $newName
    Copy-Item -Path $_.FullName -Destination $dstPath -Force
}

# Resize all pic_*.jpg
Add-Type -AssemblyName System.Drawing
$maxSize = 300
Get-ChildItem -Path $dstDir -Filter "pic_*.jpg" | ForEach-Object {
    $path = $_.FullName
    $img = [System.Drawing.Image]::FromFile($path)
    $ratio = [math]::Min($maxSize / $img.Width, $maxSize / $img.Height)
    if ($ratio -lt 1) {
        $newW = [int]($img.Width * $ratio)
        $newH = [int]($img.Height * $ratio)
        $bmp = New-Object System.Drawing.Bitmap($newW, $newH)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($img, 0, 0, $newW, $newH)
        $g.Dispose()
        $img.Dispose()
        $tempPath = "$path.tmp"
        $bmp.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Jpeg)
        $bmp.Dispose()
        Remove-Item $path -Force
        Rename-Item $tempPath (Split-Path $path -Leaf)
    } else {
        $img.Dispose()
    }
}
