param(
    [string]$BackupName = 'production-20261110T191006024Z.dump',
    [string]$ExpectedHash = '9953317AA81B41DA63B066DB96EDBC9965C11D833CFDD9FF46E5B46A81415DF6'
)
# Ensaio isolado. Nao conecta em production nem sobrescreve banco existente.
$ErrorActionPreference = 'Stop'
if ($BackupName -ne [System.IO.Path]::GetFileName($BackupName) -or
    $BackupName -notmatch '^production-[0-9TZ]+\.dump$' -or
    $ExpectedHash -notmatch '^[0-9a-fA-F]{64}$') {
    throw 'Nome do backup ou hash invalido.'
}
$backupFolder = Join-Path $env:LOCALAPPDATA 'FrioVax\backups'
$backupPath = Join-Path $backupFolder $backupName
if (-not (Test-Path -LiteralPath $backupPath -PathType Leaf)) {
    throw 'Arquivo de backup nao encontrado.'
}
if ((Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash -ne $expectedHash) {
    throw 'Hash do backup diferente do registrado. Interrompido.'
}

function Invoke-RestoreDocker {
    param([string[]]$DockerArguments)
    $ErrorActionPreference = 'Continue'
    & docker @DockerArguments *> $null
    return $LASTEXITCODE
}

$pgNames = @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD',
             'PGSSLMODE', 'PGSSLROOTCERT', 'PGCONNECT_TIMEOUT')
$previousValues = @{}
foreach ($name in $pgNames) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}
try {
    $env:PGHOST = 'ep-broad-recipe-b6n9avvd.c-2.sa-east-1.aws.neon.tech'
    $env:PGPORT = '5432'
    $env:PGDATABASE = 'neondb'
    $env:PGSSLMODE = 'verify-full'
    $env:PGSSLROOTCERT = '/etc/ssl/certs/ca-certificates.crt'
    $env:PGCONNECT_TIMEOUT = '30'
    Write-Host 'Destino exclusivo: branch teste-restauracao-88.'
    $env:PGUSER = Read-Host 'Usuario dessa branch de teste'
    $securePassword = Read-Host 'Senha dessa branch de teste' -AsSecureString
    $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
    if (-not $env:PGUSER -or -not $env:PGPASSWORD) { throw 'Credenciais obrigatorias.' }

    $commonArgs = @('run', '--rm', '-e', 'PGHOST', '-e', 'PGPORT', '-e', 'PGDATABASE',
        '-e', 'PGUSER', '-e', 'PGPASSWORD', '-e', 'PGSSLMODE', '-e', 'PGSSLROOTCERT',
        '-e', 'PGCONNECT_TIMEOUT')
    $image = 'postgres:18.6-alpine'
    $testDatabase = 'restore_88_' + [DateTime]::UtcNow.ToString('yyyyMMddHHmmssfff')
    $createExit = Invoke-RestoreDocker -DockerArguments ($commonArgs + @(
        $image, 'createdb', '--maintenance-db=neondb', $testDatabase))
    if ($createExit -ne 0) {
        throw 'Nao foi possivel criar um banco novo no destino isolado. Nenhuma restauracao executada.'
    }
    $restoreExit = Invoke-RestoreDocker -DockerArguments ($commonArgs + @(
        '--mount', "type=bind,source=$backupFolder,target=/backup,readonly", $image,
        'pg_restore', '--exit-on-error', '--single-transaction', '--no-owner', '--no-acl',
        "--dbname=$testDatabase", "/backup/$backupName"))
    if ($restoreExit -ne 0) {
        throw "Restauracao falhou e foi revertida. Banco isolado criado: $testDatabase. Investigar antes de prosseguir."
    }
    Write-Host 'PASS: backup restaurado sem erro no destino isolado.'
    Write-Host "Banco restaurado: $testDatabase"
    Write-Host "SHA256 conferido: $expectedHash"
    Write-Host 'Conferir tabelas e contagens no SQL Editor desse banco antes de aprovar a recuperacao.'
} finally {
    foreach ($name in $pgNames) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Remove-Variable securePassword -ErrorAction SilentlyContinue
}
