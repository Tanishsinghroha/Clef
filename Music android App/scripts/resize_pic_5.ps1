Add-Type -AssemblyName System.Drawing
$path = 'd:\Projects\Music\Music android App\app\src\main\res\drawable\pic_5.jpg'
$maxSize = 300
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
} else { $img.Dispose() }
