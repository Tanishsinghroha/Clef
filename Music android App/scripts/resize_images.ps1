Add-Type -AssemblyName System.Drawing
$dir = 'd:\Projects\Music\Music android App\app\src\main\res\drawable'

function Resize-Image($path, $maxSize) {
    try {
        $img = [System.Drawing.Image]::FromFile($path)
        $ratio = [math]::Min($maxSize / $img.Width, $maxSize / $img.Height)
        if ($ratio -ge 1) { $img.Dispose(); return }
        $newW = [int]($img.Width * $ratio)
        $newH = [int]($img.Height * $ratio)
        $bmp = New-Object System.Drawing.Bitmap($newW, $newH)
        $g = [System.Drawing.Graphics]::FromImage($bmp)
        $g.InterpolationMode = [System.Drawing.Drawing2D.InterpolationMode]::HighQualityBicubic
        $g.DrawImage($img, 0, 0, $newW, $newH)
        $g.Dispose()
        $img.Dispose()
        $tempPath = "$path.tmp"
        
        # Save as original format
        if ($path.EndsWith(".png")) {
            $bmp.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Png)
        } else {
            $bmp.Save($tempPath, [System.Drawing.Imaging.ImageFormat]::Jpeg)
        }
        $bmp.Dispose()
        
        Remove-Item $path -Force
        Rename-Item $tempPath (Split-Path $path -Leaf)
        Write-Output "Resized $path to ${newW}x${newH}"
    } catch {
        Write-Error "Failed to resize $path : $_"
    }
}

Resize-Image "$dir\pic_1.jpg" 300
Resize-Image "$dir\pic_2.jpg" 300
Resize-Image "$dir\pic_3.jpg" 300
Resize-Image "$dir\pic_4.jpg" 300
Resize-Image "$dir\background.png" 1080
