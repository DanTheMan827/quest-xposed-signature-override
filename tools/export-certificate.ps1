param(
    [Parameter(Mandatory=$true)][string]$OriginalApk,
    [string]$ApkSigner = "$env:LOCALAPPDATA\Android\Sdk\build-tools\34.0.0\apksigner.bat",
    [Parameter(Mandatory=$true)][string]$OutputFile
)
$ErrorActionPreference = 'Stop'
if (!(Test-Path -LiteralPath $OriginalApk -PathType Leaf)) { throw 'Original APK not found' }
if (!(Test-Path -LiteralPath $ApkSigner -PathType Leaf)) { throw 'apksigner not found; pass -ApkSigner with its full path' }
$output = & $ApkSigner verify --print-certs-pem $OriginalApk
if ($LASTEXITCODE -ne 0) { throw 'apksigner verification failed; no certificate written' }
$blocks = [regex]::Matches(($output -join "`n"), '(?s)-----BEGIN CERTIFICATE-----.*?-----END CERTIFICATE-----')
if ($blocks.Count -ne 1) {
    throw "Expected exactly one certificate, found $($blocks.Count). Multiple signers/rotation chains require explicit selection and are not handled by this helper."
}
$destination = [System.IO.Path]::GetFullPath($OutputFile)
[System.IO.Directory]::CreateDirectory([System.IO.Path]::GetDirectoryName($destination)) | Out-Null
[System.IO.File]::WriteAllText([System.IO.Path]::GetFullPath($destination), $blocks[0].Value + "`n", [System.Text.Encoding]::ASCII)
Write-Output "Wrote public certificate: $destination"
