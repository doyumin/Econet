#!/usr/bin/env bash
# 테스트용 Paper 서버. 플러그인을 빌드해 run-paper(./gradlew :paper:runServer)로 띄운다.
# 맵은 평지, 시야 거리는 작게 잡아 가볍게 돌린다. 서버 폴더는 paper/run (git에 안 올라감).
#
# 사용법 (Windows는 Git Bash에서):
#   bash scripts/test-server.sh           # 서버 실행
#   bash scripts/test-server.sh --reset   # 월드와 Econet 데이터를 지우고 새 평지 맵으로 실행
set -euo pipefail

cd "$(dirname "$0")/.."
RUN_DIR="paper/run"
PROPERTIES="$RUN_DIR/server.properties"

if [[ "${1:-}" == "--reset" ]]; then
    # level-type은 월드를 처음 만들 때만 적용되므로, 평지로 바꾸려면 기존 월드를 지워야 한다.
    echo "월드와 Econet 데이터(장부 포함)를 지웁니다: $RUN_DIR/world, $RUN_DIR/plugins/Econet"
    rm -rf "$RUN_DIR/world" "$RUN_DIR/plugins/Econet"
fi

mkdir -p "$RUN_DIR"
touch "$PROPERTIES"

set_property() {
    local key="$1" value="$2"
    if grep -q "^${key}=" "$PROPERTIES"; then
        sed -i "s|^${key}=.*|${key}=${value}|" "$PROPERTIES"
    else
        echo "${key}=${value}" >> "$PROPERTIES"
    fi
}

# 서버는 시작할 때 server.properties를 다시 쓰지만 이미 있는 값은 유지하므로, 매번 덮어써 둔다.
set_property level-type 'minecraft\:flat'
# 26.3은 generator-settings가 비어 있으면({}) 평지 레이어가 없다며 오버월드를 만들지 못한다. 기본 평지(기반암·흙 2·잔디)를 직접 적는다.
set_property generator-settings '{"biome":"minecraft:plains","layers":[{"block":"minecraft:bedrock","height":1},{"block":"minecraft:dirt","height":2},{"block":"minecraft:grass_block","height":1}],"structure_overrides":[],"features":false,"lakes":false}'
set_property generate-structures false
set_property view-distance 6
set_property simulation-distance 4
set_property spawn-protection 0
set_property difficulty easy
set_property max-players 8
set_property motd 'Econet test server'

# gradlew가 저장소에 실행 권한 없이 올라가 있어도 돌도록 bash로 직접 실행한다.
bash ./gradlew :paper:runServer
