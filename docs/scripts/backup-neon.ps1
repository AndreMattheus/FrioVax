# Backup da origem de produção. Não executa migração nem restauração.
$ErrorActionPreference = 'Stop'

function Invoke-DockerQuiet {
    param([string[]]$DockerArguments)
    # Windows PowerShell 5.1 representa stderr nativo como ErrorRecord.
    # Avisos do Docker nao indicam falha: conferir o exit code do processo.
    $ErrorActionPreference = 'Continue'
    & docker @DockerArguments *> $null
    return $LASTEXITCODE
}

$backupFolder = Join-Path $env:LOCALAPPDATA 'FrioVax\backups'
New-Item -ItemType Directory -Force -Path $backupFolder | Out-Null
$backupName = 'production-' + [DateTime]::UtcNow.ToString('yyyyMMddTHHmmssfffZ') + '.dump'
$partialName = $backupName + '.partial'
$pgNames = @('PGHOST', 'PGPORT', 'PGDATABASE', 'PGUSER', 'PGPASSWORD',
             'PGSSLMODE', 'PGSSLROOTCERT', 'PGCONNECT_TIMEOUT')
$previousValues = @{}
foreach ($name in $pgNames) {
    $previousValues[$name] = [Environment]::GetEnvironmentVariable($name, 'Process')
}

try {
    $env:PGHOST = 'ep-jolly-violet-b6b5kky7.c-2.sa-east-1.aws.neon.tech'
    $env:PGPORT = '5432'
    $env:PGDATABASE = 'neondb'
    $env:PGSSLMODE = 'verify-full'
    $env:PGSSLROOTCERT = '/etc/ssl/certs/ca-certificates.crt'
    $env:PGCONNECT_TIMEOUT = '30'
    Write-Host 'Origem: branch production, banco neondb. Operação somente de backup.'
    $env:PGUSER = Read-Host 'Usuário do banco de produção'
    $securePassword = Read-Host 'Senha do banco de produção' -AsSecureString
    $env:PGPASSWORD = [System.Net.NetworkCredential]::new('', $securePassword).Password
    if (-not $env:PGUSER -or -not $env:PGPASSWORD) {
        throw 'Usuário e senha são obrigatórios.'
    }

    $dumpExit = Invoke-DockerQuiet -DockerArguments @(
        'run', '--rm',
        '-e', 'PGHOST', '-e', 'PGPORT', '-e', 'PGDATABASE', '-e', 'PGUSER', '-e', 'PGPASSWORD',
        '-e', 'PGSSLMODE', '-e', 'PGSSLROOTCERT', '-e', 'PGCONNECT_TIMEOUT',
        '--mount', "type=bind,source=$backupFolder,target=/backup",
        'postgres:18.6-alpine', 'pg_dump', '--format=custom', '--no-owner', '--no-acl',
        "--file=/backup/$partialName"
    )
    if ($dumpExit -ne 0) {
        throw 'Backup falhou. Detalhes suprimidos para proteger credenciais; arquivo parcial não é backup válido.'
    }

    $restoreExit = Invoke-DockerQuiet -DockerArguments @(
        'run', '--rm', '--mount', "type=bind,source=$backupFolder,target=/backup,readonly",
        'postgres:18.6-alpine', 'pg_restore', '--list', "/backup/$partialName"
    )
    if ($restoreExit -ne 0) {
        throw 'Arquivo não passou na leitura do pg_restore. Não usar como backup válido.'
    }

    $partialPath = Join-Path $backupFolder $partialName
    $backupPath = Join-Path $backupFolder $backupName
    Move-Item -LiteralPath $partialPath -Destination $backupPath
    $hash = (Get-FileHash -LiteralPath $backupPath -Algorithm SHA256).Hash
    Write-Host "Backup criado: $backupPath"
    Write-Host "SHA256: $hash"
    Write-Host 'Arquivo legível. A restauração em destino isolado ainda precisa ser testada.'
} finally {
    foreach ($name in $pgNames) {
        [Environment]::SetEnvironmentVariable($name, $previousValues[$name], 'Process')
    }
    Remove-Variable securePassword -ErrorAction SilentlyContinue
}
