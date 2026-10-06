#!/usr/bin/env bash
# 无人货柜服务一键演示：服务需已启动（默认 http://127.0.0.1:8080）
# 用法: bash scripts/demo.sh [BASE_URL]
set -euo pipefail
BASE="${1:-http://127.0.0.1:8080}"
PW=password123
Y=$(date -d 'yesterday' +%F 2>/dev/null || date -v-1d +%F)
MILK="B-MILK-$Y"

jget() { python3 -c "import sys,json;d=json.load(sys.stdin);print(eval(\"d$1\"))"; }
say() { printf '\n\033[1;36m== %s ==\033[0m\n' "$1"; }

say "0) 健康检查 / Swagger"
curl -s -o /dev/null -w "  openapi json: %{http_code}\n" "$BASE/v3/api-docs"

say "1) 补货：正常(牛奶 A01) / 放错(酸奶申报A02实际A04) / 卡货(三明治)"
curl -s -u staff:$PW -XPOST "$BASE/api/staff/restock" -H 'Content-Type: application/json' -d "{
  \"machineCode\":\"VM-C001\",\"photoUrl\":\"https://img/demo.jpg\",\"note\":\"demo补货\",\"items\":[
    {\"batchNo\":\"$MILK\",\"slotCode\":\"A01\",\"qty\":5,\"tempRequirement\":\"CHILLED\"},
    {\"batchNo\":\"B-YOG-NEAR\",\"slotCode\":\"A02\",\"actualSlotCode\":\"A04\",\"qty\":2,\"tempRequirement\":\"CHILLED\"},
    {\"batchNo\":\"B-BREAD-EXP\",\"slotCode\":\"A03\",\"qty\":2,\"tempRequirement\":\"CHILLED\",\"jammed\":true}
  ]}" | python3 -m json.tool

say "2) 开门 -> 拿取牛奶(VISION+RFID 双高置信度) -> 关门自动对账成单"
SID=$(curl -s -u customer:$PW -XPOST "$BASE/api/shopping/sessions" -H 'Content-Type: application/json' \
  -d '{"machineCode":"VM-C001","videoClipUrl":"https://video/demo.mp4"}' | jget "['sessionId']")
curl -s -u customer:$PW -XPOST "$BASE/api/shopping/sessions/$SID/events" -H 'Content-Type: application/json' -d "{
  \"events\":[
    {\"type\":\"PICK\",\"method\":\"VISION\",\"slotCode\":\"A01\",\"batchNo\":\"$MILK\",\"qty\":1},
    {\"type\":\"RECOGNITION\",\"method\":\"VISION\",\"slotCode\":\"A01\",\"batchNo\":\"$MILK\",\"productSku\":\"MILK250\",\"qty\":1,\"confidence\":0.95},
    {\"type\":\"RFID\",\"slotCode\":\"A01\",\"batchNo\":\"$MILK\",\"productSku\":\"MILK250\",\"qty\":1,\"confidence\":0.91}
  ]}" >/dev/null
curl -s -u customer:$PW -XPOST "$BASE/api/shopping/sessions/$SID/close" | python3 -m json.tool

say "3) 冷藏柜上报 15℃ -> 告警锁柜；锁定期补货被拦截(409)"
ALERT=$(curl -s -u operator:$PW -XPOST "$BASE/api/temperature/reports" -H 'Content-Type: application/json' \
  -d '{"machineCode":"VM-C001","tempC":15}')
echo "  $ALERT"
curl -s -o /dev/null -w "  锁定期补货 HTTP: %{http_code}\n" -u staff:$PW -XPOST "$BASE/api/staff/restock" \
  -H 'Content-Type: application/json' -d "{\"machineCode\":\"VM-C001\",\"photoUrl\":\"x\",\"items\":[{\"batchNo\":\"$MILK\",\"slotCode\":\"A01\",\"qty\":1,\"tempRequirement\":\"CHILLED\"}]}"
ALERTNO=$(echo "$ALERT" | jget "['alertNo']")

say "4) 温控处置 CONDEMNED：在柜批次整柜报损下架"
curl -s -u operator:$PW -XPOST "$BASE/api/temperature/alerts/$ALERTNO/resolve?resolution=CONDEMNED&note=power-off-demo" | python3 -m json.tool

say "5) 补临期酸奶 -> 临期扫描 -> 下架转报损"
curl -s -u staff:$PW -XPOST "$BASE/api/staff/restock" -H 'Content-Type: application/json' \
  -d '{"machineCode":"VM-C001","photoUrl":"y","items":[{"batchNo":"B-YOG-NEAR","slotCode":"A02","qty":3,"tempRequirement":"CHILLED"}]}' >/dev/null
echo "  扫描: $(curl -s -u operator:$PW -XPOST "$BASE/api/off-shelf/scan")"
TASK=$(curl -s -u operator:$PW "$BASE/api/off-shelf/tasks?machineCode=VM-C001" \
  | python3 -c "import sys,json;print(next((t['taskNo'] for t in json.load(sys.stdin) if t['reason']=='NEAR_EXPIRY'),''))")
[ -n "$TASK" ] && curl -s -u operator:$PW -XPOST "$BASE/api/off-shelf/tasks/$TASK/complete?convertToLoss=true&note=near-expiry-demo" | python3 -m json.tool

say "6) 投诉证据回放 + 退款（取顾客最近 CONFIRMED 单）"
ONO=$(curl -s -u customer:$PW "$BASE/api/query/my-orders" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print(next((o['orderNo'] for o in reversed(d) if o['status']=='CONFIRMED'),''))")
if [ -n "$ONO" ]; then
  CP=$(curl -s -u customer:$PW -XPOST "$BASE/api/complaints" -H 'Content-Type: application/json' \
    -d "{\"orderNo\":\"$ONO\",\"type\":\"WRONG_CHARGE\",\"description\":\"demo误扣款\"}" | jget "['complaintNo']")
  echo "  证据包摘要:"
  curl -s -u operator:$PW "$BASE/api/complaints/$CP/evidence" \
    | python3 -c "import sys,json;d=json.load(sys.stdin);print('   时间线',len(d['eventTimeline']),'识别',len(d['recognitions']),'库存变化',len(d['stockChanges']),'视频',d['videoClipUrl'])"
  curl -s -u operator:$PW -XPOST "$BASE/api/complaints/$CP/decision" -H 'Content-Type: application/json' \
    -d '{"decision":"REFUND","resolution":"demo全额退款"}' | python3 -m json.tool
else
  echo "  无 CONFIRMED 订单，跳过"
fi

say "7) 批次全链路追踪（牛奶）"
curl -s -u operator:$PW "$BASE/api/batches/$MILK/trace" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('  ',d['batchNo'],d['summary']);print('   报损',[(x['reason'],x['qty'],x['bearer']) for x in d['losses']])"

say "8) 财务结算（供应商 SUP01 近 7 天）"
S=$(date -d '-3 days' +%F 2>/dev/null || date -v-3d +%F); E=$(date -d '+3 days' +%F 2>/dev/null || date -v+3d +%F)
curl -s -u finance:$PW -XPOST "$BASE/api/finance/settlements" -H 'Content-Type: application/json' \
  -d "{\"supplierId\":1,\"periodStart\":\"$S\",\"periodEnd\":\"$E\"}" \
  | python3 -c "import sys,json;d=json.load(sys.stdin);print('  结算单',d['settlementNo'],'售卖',d['salesAmount'],'供应商承担损耗',d['supplierBorneLoss'],'应付',d['payableAmount'],'批次数',len(d['items']))" 2>/dev/null || echo "  该周期结算单已存在（重复生成被拦截，符合预期）"

say "9) 运营点位分析"
curl -s -u operator:$PW "$BASE/api/analytics/locations" | python3 -m json.tool

echo
printf '\033[1;32m演示完成。Swagger: %s/swagger-ui.html\033[0m\n' "$BASE"
