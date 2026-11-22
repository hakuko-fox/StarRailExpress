$ErrorActionPreference = 'Stop'
# v2.1 replaces the old role acceptance rules, including Kana's party/knife system.
& node (Join-Path $PSScriptRoot 'verify_vtuber_v21.mjs')
if ($LASTEXITCODE -ne 0) { throw 'VTuber v2.1 verification failed.' }
