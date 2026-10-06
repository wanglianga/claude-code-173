package com.uvend;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.httpBasic;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 全流程集成测试（H2 PG 模式 + 本地事件总线）：
 * 补货/放错货道/卡货/温控锁定、识别对账（放回净额、证据不足、人工复核）、
 * 温控报损、临期下架、盘点差异、投诉证据回放退款、结算、运营分析、批次追踪、品类调整、鉴权。
 */
@SpringBootTest
@AutoConfigureMockMvc
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullFlowIntegrationTest {

    @Autowired MockMvc mockMvc;
    @Autowired ObjectMapper mapper;

    private static final String MILK_BATCH = "B-MILK-" + LocalDate.now().minusDays(1);
    private static String pendingOrderNo;

    private MvcResult call(String user, MockHttpServletRequestBuilder req) throws Exception {
        return mockMvc.perform(req.with(httpBasic(user, "password123"))).andReturn();
    }

    private JsonNode json(MvcResult r) throws Exception {
        return mapper.readTree(r.getResponse().getContentAsByteArray());
    }


    private Long openSession(String user, String machine, String video) throws Exception {
        MvcResult r = call(user, post("/api/shopping/sessions")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineCode\":\"" + machine + "\",\"videoClipUrl\":\"" + video + "\"}"));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        return json(r).get("sessionId").asLong();
    }

    private void sendEvents(String user, Long sid, String eventsJson) throws Exception {
        MvcResult r = call(user, post("/api/shopping/sessions/" + sid + "/events")
                .contentType(MediaType.APPLICATION_JSON).content(eventsJson));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
    }

    private MvcResult close(String user, Long sid) throws Exception {
        return call(user, post("/api/shopping/sessions/" + sid + "/close"));
    }

    // 1. 补货：正常 / 放错货道 / 卡货
    @Test @Order(1)
    void restock_normal_misplaced_jammed() throws Exception {
        String body = """
        {
          "machineCode": "VM-C001",
          "photoUrl": "https://oss.example.com/restock/c001-01.jpg",
          "note": "晨间补货",
          "items": [
            {"batchNo":"%s","slotCode":"A01","qty":6,"tempRequirement":"CHILLED"},
            {"batchNo":"B-YOG-NEAR","slotCode":"A02","actualSlotCode":"A04","qty":4,"tempRequirement":"CHILLED"},
            {"batchNo":"B-BREAD-EXP","slotCode":"A03","qty":3,"tempRequirement":"CHILLED","jammed":true}
          ]
        }
        """.formatted(MILK_BATCH);

        MvcResult r = call("staff", post("/api/staff/restock")
                .contentType(MediaType.APPLICATION_JSON).content(body));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode n = json(r);
        Assertions.assertEquals("COMPLETED", n.get("status").asText());
        JsonNode items = n.get("items");
        Assertions.assertEquals("PLACED", items.get(0).get("state").asText());
        Assertions.assertEquals("MISPLACED", items.get(1).get("state").asText());
        Assertions.assertEquals("A04", items.get(1).get("actualSlotCode").asText());
        Assertions.assertTrue(items.get(1).has("offShelfTaskNo"));
        Assertions.assertEquals("JAMMED", items.get(2).get("state").asText());
    }

    // 2. 库存只体现正常入位与错放实际货道，卡货不入库存
    @Test @Order(2)
    void inventory_reflects_restock_only() throws Exception {
        MvcResult r = call("operator", get("/api/query/machines/VM-C001/inventory"));
        JsonNode n = json(r);
        int milk = 0, yogurt = 0, bread = 0;
        for (JsonNode it : n.get("items")) {
            if ("A01".equals(it.get("slotCode").asText())) milk = it.get("qty").asInt();
            if ("A04".equals(it.get("slotCode").asText())) yogurt = it.get("qty").asInt();
            if ("A03".equals(it.get("slotCode").asText())) bread = it.get("qty").asInt();
        }
        Assertions.assertEquals(6, milk);
        Assertions.assertEquals(4, yogurt);
        Assertions.assertEquals(0, bread);
    }

    // 3. 放错货道归位 A04 -> A02
    @Test @Order(3)
    void relocate_misplaced() throws Exception {
        MvcResult tasks = call("operator", get("/api/off-shelf/tasks").param("machineCode", "VM-C001"));
        JsonNode arr = json(tasks);
        String taskNo = null;
        for (JsonNode t : arr) {
            if ("MISPLACED".equals(t.get("reason").asText())) taskNo = t.get("taskNo").asText();
        }
        Assertions.assertNotNull(taskNo, "应有放错货道任务");
        MvcResult r = call("operator", post("/api/off-shelf/tasks/" + taskNo + "/relocate")
                .param("targetSlotCode", "A02"));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        Assertions.assertEquals("DONE", json(r).get("status").asText());
    }

    // 10. 拿起又放回：净额为 0，订单取消不扣款
    @Test @Order(10)
    void shopping_pick_and_putback_no_charge() throws Exception {
        Long sid = openSession("customer", "VM-C001", "https://video/putback.mp4");
        String events = """
        {"events":[
          {"type":"PICK","method":"VISION","slotCode":"A01","batchNo":"%s","qty":1},
          {"type":"RECOGNITION","method":"VISION","slotCode":"A01","batchNo":"%s","productSku":"MILK250","qty":1,"confidence":0.95},
          {"type":"PUT_BACK","method":"VISION","slotCode":"A01","batchNo":"%s","qty":1}
        ]}
        """.formatted(MILK_BATCH, MILK_BATCH, MILK_BATCH);
        sendEvents("customer", sid, events);
        JsonNode order = json(close("customer", sid));
        Assertions.assertEquals("CANCELLED", order.get("status").asText(), order.toString());
        Assertions.assertEquals(0, order.get("totalAmount").decimalValue().compareTo(BigDecimal.ZERO), order.toString());
    }

    // 11. 证据充分（VISION+RFID 双高置信度）→ 自动成单扣款
    @Test @Order(11)
    void shopping_sufficient_evidence_confirmed_order() throws Exception {
        Long sid = openSession("customer", "VM-C001", "https://video/buy1.mp4");
        String events = """
        {"events":[
          {"type":"PICK","method":"VISION","slotCode":"A01","batchNo":"%s","qty":1},
          {"type":"RECOGNITION","method":"VISION","slotCode":"A01","batchNo":"%s","productSku":"MILK250","qty":1,"confidence":0.96},
          {"type":"RFID","slotCode":"A01","batchNo":"%s","productSku":"MILK250","qty":1,"confidence":0.91}
        ]}
        """.formatted(MILK_BATCH, MILK_BATCH, MILK_BATCH);
        sendEvents("customer", sid, events);
        JsonNode order = json(close("customer", sid));
        Assertions.assertEquals("CONFIRMED", order.get("status").asText(), order.toString());
        Assertions.assertEquals("SUFFICIENT", order.get("evidence").asText());
        Assertions.assertEquals(1, order.get("confirmedItems").size());
        Assertions.assertEquals(0, order.get("totalAmount").decimalValue().compareTo(new BigDecimal("6.50")), order.toString());
    }

    // 12. 低置信度 → 证据不足挂起 → 运营人工确认扣款
    @Test @Order(12)
    void shopping_low_confidence_pending_then_manual_confirm() throws Exception {
        Long sid = openSession("customer", "VM-C001", "https://video/uncertain.mp4");
        String events = """
        {"events":[
          {"type":"PICK","method":"VISION","slotCode":"A01","batchNo":"%s","qty":1},
          {"type":"RECOGNITION","method":"VISION","slotCode":"A01","batchNo":"%s","productSku":"MILK250","qty":1,"confidence":0.52}
        ]}
        """.formatted(MILK_BATCH, MILK_BATCH);
        sendEvents("customer", sid, events);

        JsonNode order = json(close("customer", sid));
        Assertions.assertEquals("PENDING", order.get("status").asText());
        Assertions.assertEquals("INSUFFICIENT", order.get("evidence").asText());
        Assertions.assertEquals(0, order.get("totalAmount").decimalValue().compareTo(BigDecimal.ZERO), order.toString());
        pendingOrderNo = order.get("orderNo").asText();

        MvcResult pend = call("operator", get("/api/shopping/orders/pending"));
        Long orderId = null;
        for (JsonNode o : json(pend)) {
            if (pendingOrderNo.equals(o.get("orderNo").asText())) orderId = o.get("orderId").asLong();
        }
        Assertions.assertNotNull(orderId, "待复核列表应包含该订单");

        MvcResult review = call("operator", post("/api/shopping/orders/" + orderId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"decision":"CONFIRM","note":"视频确认拿取","items":[{"batchNo":"%s","qty":1}]}"""
                        .formatted(MILK_BATCH)));
        JsonNode rn = json(review);
        Assertions.assertEquals(200, review.getResponse().getStatus(), rn.toString());
        Assertions.assertEquals("CONFIRMED", rn.get("status").asText());
        Assertions.assertEquals(0, rn.get("totalAmount").decimalValue().compareTo(new BigDecimal("6.50")), rn.toString());
    }

    // 13. 货道卡货：识别为拿取但货道 JAM → 证据不足挂起，人工驳回不扣款
    @Test @Order(13)
    void jammed_pick_pending_and_reject() throws Exception {
        // 补 2 瓶牛奶到 A01（此前剩余 3 瓶）
        MvcResult rr = call("staff", post("/api/staff/restock")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"machineCode":"VM-C001","photoUrl":"jam.jpg","items":[
                      {"batchNo":"%s","slotCode":"A01","qty":2,"tempRequirement":"CHILLED"}]}
                    """.formatted(MILK_BATCH)));
        Assertions.assertEquals(200, rr.getResponse().getStatus(), rr.getResponse().getContentAsString());

        Long sid = openSession("customer", "VM-C001", "https://video/jam.mp4");
        String events = """
        {"events":[
          {"type":"PICK","method":"VISION","slotCode":"A01","batchNo":"%s","qty":1},
          {"type":"RECOGNITION","method":"VISION","slotCode":"A01","batchNo":"%s","productSku":"MILK250","qty":1,"confidence":0.93},
          {"type":"JAM","slotCode":"A01"}
        ]}
        """.formatted(MILK_BATCH, MILK_BATCH);
        sendEvents("customer", sid, events);
        JsonNode order = json(close("customer", sid));
        Assertions.assertEquals("PENDING", order.get("status").asText(), order.toString());
        Assertions.assertTrue(order.get("pendingReviewItems").get(0).get("reason").asText().contains("卡货"));

        MvcResult pend = call("operator", get("/api/shopping/orders/pending"));
        Long orderId = null;
        for (JsonNode o : json(pend)) {
            if (!pendingOrderNo.equals(o.get("orderNo").asText())) orderId = o.get("orderId").asLong();
        }
        Assertions.assertNotNull(orderId);
        MvcResult review = call("operator", post("/api/shopping/orders/" + orderId + "/review")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REJECT\",\"note\":\"卡货未交付\"}"));
        Assertions.assertEquals("CANCELLED", json(review).get("status").asText());
    }

    // 20. 温控异常 → 锁柜/补货拦截/整柜报损
    @Test @Order(20)
    void temperature_alert_locks_and_condemns() throws Exception {
        MvcResult r = call("operator", post("/api/temperature/reports")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineCode\":\"VM-C001\",\"tempC\":14.0}"));
        JsonNode n = json(r);
        Assertions.assertEquals("CRITICAL", n.get("status").asText());
        Assertions.assertEquals("OPEN_ALERT_AND_LOCK", n.get("action").asText());
        String alertNo = n.get("alertNo").asText();

        MvcResult blocked = call("staff", post("/api/staff/restock")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"machineCode":"VM-C001","photoUrl":"x.jpg",
                     "items":[{"batchNo":"%s","slotCode":"A01","qty":1,"tempRequirement":"CHILLED"}]}
                    """.formatted(MILK_BATCH)));
        Assertions.assertEquals(409, blocked.getResponse().getStatus());

        MvcResult res = call("operator",
                post("/api/temperature/alerts/" + alertNo + "/resolve")
                        .param("resolution", "CONDEMNED").param("note", "断电超4小时"));
        JsonNode rn = json(res);
        Assertions.assertEquals("CONDEMNED", rn.get("resolution").asText());
        Assertions.assertTrue(rn.get("condemnedQty").asInt() >= 5, rn.toString());
        Assertions.assertFalse(rn.get("machineLocked").asBoolean());

        MvcResult inv = call("operator", get("/api/query/machines/VM-C001/inventory"));
        Assertions.assertEquals(0, json(inv).get("items").size());
    }

    // 30. 临期扫描 + 下架转报损
    @Test @Order(30)
    void expiry_scan_and_off_shelf() throws Exception {
        MvcResult rr = call("staff", post("/api/staff/restock")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"machineCode":"VM-O001","photoUrl":"o.jpg","items":[
                      {"batchNo":"B-COK-NEW","slotCode":"B01","qty":10,"tempRequirement":"AMBIENT"}]}"""));
        Assertions.assertEquals(200, rr.getResponse().getStatus(), rr.getResponse().getContentAsString());

        MvcResult rr2 = call("staff", post("/api/staff/restock")
                .contentType(MediaType.APPLICATION_JSON).content("""
                    {"machineCode":"VM-C001","photoUrl":"c.jpg","items":[
                      {"batchNo":"B-YOG-NEAR","slotCode":"A02","qty":3,"tempRequirement":"CHILLED"}]}"""));
        Assertions.assertEquals(200, rr2.getResponse().getStatus(), rr2.getResponse().getContentAsString());

        MvcResult scan = call("operator", post("/api/off-shelf/scan"));
        int created = json(scan).get("createdTasks").asInt();
        Assertions.assertTrue(created >= 1, "应至少生成 1 个临期/过期任务");

        MvcResult tasks = call("operator", get("/api/off-shelf/tasks").param("machineCode", "VM-C001"));
        JsonNode arr = json(tasks);
        Assertions.assertEquals(1, arr.size(), "社区柜应只有一个新临期任务");
        String taskNo = arr.get(0).get("taskNo").asText();
        Assertions.assertEquals("NEAR_EXPIRY", arr.get(0).get("reason").asText());

        MvcResult done = call("operator",
                post("/api/off-shelf/tasks/" + taskNo + "/complete")
                        .param("convertToLoss", "true").param("note", "临期下架报损"));
        JsonNode dn = json(done);
        Assertions.assertTrue(dn.get("status").asText().contains("LOSS"));
        Assertions.assertTrue(!dn.get("lossNo").asText().isBlank());
    }

    // 40. 盘点盘亏 → 报损 → 关单
    @Test @Order(40)
    void stocktake_shortage_resolved_as_loss() throws Exception {
        MvcResult open = call("operator", post("/api/stocktake/open")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineCode\":\"VM-O001\",\"note\":\"月末盘点\"}"));
        String stNo = json(open).get("stocktakeNo").asText();

        MvcResult count = call("operator", post("/api/stocktake/" + stNo + "/count")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                    {"machineCode":"VM-O001","counts":[{"slotCode":"B01","batchNo":"B-COK-NEW","actualQty":8}]}"""));
        JsonNode ci = json(count).get("items").get(0);
        Assertions.assertEquals(-2, ci.get("diffQty").asInt());
        Assertions.assertEquals("MISMATCH", ci.get("state").asText());
        Long itemId = ci.get("itemId").asLong();

        MvcResult res = call("operator",
                post("/api/stocktake/" + stNo + "/items/" + itemId + "/resolve")
                        .param("resolution", "LOSS").param("note", "两件丢失"));
        Assertions.assertEquals(200, res.getResponse().getStatus(), res.getResponse().getContentAsString());
        Assertions.assertTrue(json(res).has("lossNo"));

        MvcResult close = call("operator", post("/api/stocktake/" + stNo + "/close"));
        Assertions.assertEquals("CLOSED", json(close).get("status").asText());
    }

    // 50. 投诉 → 证据回放 → 全额退款
    @Test @Order(50)
    void complaint_evidence_refund() throws Exception {
        JsonNode arr = json(call("customer", get("/api/query/my-orders")));
        // my-orders 按时间倒序；取最早的 CONFIRMED（证据充分自动成单，含 VISION+RFID 两条识别）
        String confirmedOrderNo = null;
        for (int i = arr.size() - 1; i >= 0; i--) {
            if ("CONFIRMED".equals(arr.get(i).get("status").asText())) {
                confirmedOrderNo = arr.get(i).get("orderNo").asText();
                break;
            }
        }
        Assertions.assertNotNull(confirmedOrderNo, "应至少有一个 CONFIRMED 订单");

        MvcResult cp = call("customer", post("/api/complaints")
                .contentType(MediaType.APPLICATION_JSON)
                .content(("{\"orderNo\":\"" + confirmedOrderNo + "\",\"type\":\"WRONG_CHARGE\","
                        + "\"description\":\"我没拿牛奶却被扣费\"}")));
        Assertions.assertEquals(200, cp.getResponse().getStatus(), cp.getResponse().getContentAsString());
        String complaintNo = json(cp).get("complaintNo").asText();

        JsonNode bundle = json(call("operator", get("/api/complaints/" + complaintNo + "/evidence")));
        Assertions.assertTrue(bundle.get("eventTimeline").size() >= 3);
        Assertions.assertTrue(bundle.get("recognitions").size() >= 2);
        Assertions.assertEquals("https://video/buy1.mp4", bundle.get("videoClipUrl").asText());
        Assertions.assertTrue(bundle.get("stockChanges").size() > 0);

        MvcResult dec = call("operator", post("/api/complaints/" + complaintNo + "/decision")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"decision\":\"REFUND\",\"resolution\":\"证据冲突，全额退款\"}"));
        JsonNode dn = json(dec);
        Assertions.assertEquals("REFUNDED", dn.get("status").asText());
        Assertions.assertEquals(0, dn.get("amount").decimalValue().compareTo(new BigDecimal("6.50")), dn.toString());
    }

    // 60. 批次全链路
    @Test @Order(60)
    void batch_trace_full_lifecycle() throws Exception {
        JsonNode n = json(call("operator", get("/api/batches/" + MILK_BATCH + "/trace")));
        Assertions.assertEquals(MILK_BATCH, n.get("batchNo").asText());
        Assertions.assertTrue(n.get("summary").get("restockedQty").asInt() >= 8);
        Assertions.assertTrue(n.get("summary").get("soldQty").asInt() >= 2);
        Assertions.assertTrue(n.get("timeline").size() >= 4, "应含补货/售卖/报损流水");
        Assertions.assertTrue(n.get("losses").size() >= 1, "应有温控报损记录");
        Assertions.assertTrue(n.get("losses").get(0).has("bearer"));
    }

    // 70. 财务结算（真实售卖扣退款 + 供应商承担报损扣减）
    @Test @Order(70)
    void settlement_by_real_sales_and_loss() throws Exception {
        String body = "{\"supplierId\":1,\"periodStart\":\"" + LocalDate.now().minusDays(1)
                + "\",\"periodEnd\":\"" + LocalDate.now().plusDays(1) + "\"}";
        MvcResult r = call("finance", post("/api/finance/settlements")
                .contentType(MediaType.APPLICATION_JSON).content(body));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        JsonNode n = json(r);
        String stlNo = n.get("settlementNo").asText();
        // 一单已退（不计收入），一单真实售出 6.50
        Assertions.assertEquals(0, n.get("salesAmount").decimalValue().compareTo(new BigDecimal("6.50")), n.toString());
        Assertions.assertTrue(n.get("supplierBorneLoss").decimalValue().compareTo(BigDecimal.ZERO) > 0);
        Assertions.assertTrue(n.get("items").size() > 0);

        MvcResult confirm = call("finance", post("/api/finance/settlements/" + stlNo + "/confirm"));
        Assertions.assertEquals("CONFIRMED", json(confirm).get("status").asText());

        // 重复生成被拦截
        MvcResult again = call("finance", post("/api/finance/settlements")
                .contentType(MediaType.APPLICATION_JSON).content(body));
        Assertions.assertEquals(409, again.getResponse().getStatus());
    }

    // 80. 运营分析
    @Test @Order(80)
    void analytics_reports() throws Exception {
        JsonNode n = json(call("operator", get("/api/analytics/machines/VM-C001")));
        Assertions.assertTrue(n.has("nearExpiryRatePct"));
        Assertions.assertTrue(n.get("lossQty").asInt() > 0, "温控报损后损耗应>0");
        Assertions.assertTrue(json(call("operator", get("/api/analytics/locations"))).size() >= 2);
    }

    // 90. 品类调整 + 温控不符拦截
    @Test @Order(90)
    void assortment_adjust() throws Exception {
        MvcResult r = call("operator", post("/api/assortment/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineCode\":\"VM-O001\",\"slotCode\":\"B02\","
                        + "\"newProductSku\":\"COK100\",\"reason\":\"办公楼零食销量高\"}"));
        Assertions.assertEquals(200, r.getResponse().getStatus(), r.getResponse().getContentAsString());
        Assertions.assertEquals("巧克力曲奇100g", json(r).get("newProduct").asText());

        MvcResult bad = call("operator", post("/api/assortment/adjust")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"machineCode\":\"VM-O001\",\"slotCode\":\"B02\","
                        + "\"newProductSku\":\"MILK250\",\"reason\":\"x\"}"));
        Assertions.assertEquals(400, bad.getResponse().getStatus());
    }

    // 100. 鉴权
    @Test @Order(100)
    void role_based_access_denied() throws Exception {
        String validRestock = "{\"machineCode\":\"VM-C001\",\"photoUrl\":\"x.jpg\","
                + "\"items\":[{\"batchNo\":\"B-COK-NEW\",\"slotCode\":\"A01\",\"qty\":1,\"tempRequirement\":\"AMBIENT\"}]}";
        mockMvc.perform(post("/api/staff/restock")
                        .with(httpBasic("customer", "password123"))
                        .contentType(MediaType.APPLICATION_JSON).content(validRestock))
                .andExpect(status().isForbidden());
        mockMvc.perform(get("/api/analytics/locations"))
                .andExpect(r -> Assertions.assertTrue(
                        r.getResponse().getStatus() == 401 || r.getResponse().getStatus() == 403));
    }
}
