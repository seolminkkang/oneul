# ===============================
# 부하 측정 실행기 (2026-08-11)
#
# 하는 일:
#   1) DB를 3,000건 상황으로 초기화
#   2) 진행중 챌린지 ID를 알아냄 (실행할 때마다 바뀌므로 하드코딩 불가)
#   3) k6 실행 -> HTML 리포트 + JSON 요약 + 터미널 원본 로그를 전부 남김
#
# 사용법:
#   .\run-load-test.ps1 -Label baseline
#   .\run-load-test.ps1 -Label before-sequential
#   .\run-load-test.ps1 -Label after-threadpool -Rate 30 -Duration 60s
#
# 결과는 notes/raw/<날짜>/ 에 <Label> 이름으로 저장된다.
# ===============================

param(
    [Parameter(Mandatory = $true)]
    [string]$Label,                       # 이 측정의 이름. 파일명이 된다
    [int]$Rate = 30,                      # 초당 요청 수
    [string]$Duration = '60s',
    [int]$TokenCount = 50,
    [switch]$SkipSeed                     # 데이터 초기화를 건너뛴다 (배치 직후 상태를 보고 싶을 때)
)

# native 명령(docker/k6)의 stderr 는 PowerShell 5.1 에서 에러로 잡히므로 Stop 을 쓰지 않는다.
$ErrorActionPreference = 'Continue'

$docker  = 'C:\Program Files\Docker\Docker\resources\bin\docker.exe'
$k6      = 'C:\Program Files\k6\k6.exe'
$appDir  = 'C:\seolmin\portfolio\oneul\oneul-app\oneul'
$today   = Get-Date -Format 'yyyy-MM-dd'
$outDir  = "C:\seolmin\portfolio\oneul\notes\raw\$today"

New-Item -ItemType Directory -Force $outDir | Out-Null
Set-Location $appDir

# ---------- 1. 데이터 초기화 ----------
if (-not $SkipSeed) {
    Write-Host "[1/3] DB를 3,000건 상황으로 초기화..." -ForegroundColor Cyan
    # 비밀번호를 -p 로 넘기면 mysql 이 경고를 stderr 로 뱉고, 그게 에러로 잡힌다.
    # MYSQL_PWD 환경변수로 넘기면 경고 자체가 없다.
    Get-Content 'seed-load.sql' -Raw -Encoding UTF8 |
        & $docker exec -i -e MYSQL_PWD=1234 oneul-mysql mysql -uroot --default-character-set=utf8mb4 |
        Write-Host
} else {
    Write-Host "[1/3] 초기화 건너뜀 (-SkipSeed)" -ForegroundColor Yellow
}

# ---------- 2. 진행중 챌린지 ID ----------
Write-Host "[2/3] 진행중 챌린지 ID 조회..." -ForegroundColor Cyan
$liveId = (& $docker exec -e MYSQL_PWD=1234 oneul-mysql mysql -uroot -N -B -e `
    "SELECT challenge_id FROM oneul.challenge WHERE name='load-challenge-live';") -join ''
if (-not $liveId) { throw "진행중 챌린지를 못 찾았다. seed-load.sql 을 먼저 돌려야 한다." }
Write-Host "    진행중 챌린지 ID = $liveId"

# ---------- 3. k6 실행 ----------
$html = Join-Path $outDir "k6-$Label.html"
$json = Join-Path $outDir "k6-$Label-summary.json"
$log  = Join-Path $outDir "k6-$Label.txt"

Write-Host "[3/3] k6 실행 (초당 ${Rate}건, $Duration)..." -ForegroundColor Cyan
Write-Host "    실행 중 실시간 그래프: http://127.0.0.1:5665" -ForegroundColor DarkGray

$env:K6_WEB_DASHBOARD        = 'true'
$env:K6_WEB_DASHBOARD_EXPORT = $html
$env:K6_WEB_DASHBOARD_PERIOD = '1s'

& $k6 run `
    -e LIVE_CHALLENGE_ID=$liveId `
    -e RATE=$Rate `
    -e DURATION=$Duration `
    -e TOKEN_COUNT=$TokenCount `
    --summary-export $json `
    load-test.js 2>&1 | Tee-Object $log

Write-Host ""
Write-Host "저장 완료:" -ForegroundColor Green
Write-Host "  HTML 리포트 (캡처용) : $html"
Write-Host "  JSON 요약            : $json"
Write-Host "  터미널 원본          : $log"
