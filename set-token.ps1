# Pushes a Home Assistant long-lived token to Joint Launcher on the watch.
# Create the token in HA: Perfil -> Seguridad -> Tokens de acceso de larga duración -> Crear ("Joint Launcher watch").
# The token never appears on a command line: it goes through a temp file and `run-as` into the app's private files.
# `run-as` needs a debuggable build for a moment: install the debug APK, run this, then `install -r` the release (it keeps the token).
# Example: .\set-token.ps1 -Watch 192.168.1.50:5555
param([Parameter(Mandatory = $true)][string]$Watch, [string]$Adb = "adb")

$app = "com.mau.hub.wear"
$sec = Read-Host "Pega el token de HA" -AsSecureString
$tok = [Runtime.InteropServices.Marshal]::PtrToStringBSTR([Runtime.InteropServices.Marshal]::SecureStringToBSTR($sec)).Trim()
if ($tok.Length -lt 100) { Write-Error "Eso no parece un token de larga duración de HA."; exit 1 }

$tmp = New-TemporaryFile
try {
    [IO.File]::WriteAllText($tmp, $tok)
    & $Adb -s $Watch push $tmp /data/local/tmp/jlw_token | Out-Null
    & $Adb -s $Watch shell "run-as $app sh -c 'mkdir -p files && cat /data/local/tmp/jlw_token > files/token'; rm -f /data/local/tmp/jlw_token"
} finally {
    Remove-Item $tmp -Force
}
& $Adb -s $Watch shell "am force-stop $app; am start -n $app/.MainActivity" | Out-Null
"Token guardado y Joint Launcher reiniciado."
