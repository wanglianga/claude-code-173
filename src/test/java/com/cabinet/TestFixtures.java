package com.cabinet;

import com.cabinet.domain.*;
import com.cabinet.domain.enums.*;
import com.cabinet.repo.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 测试夹具：快速创建供应商/商品/点位/柜机/货道/批次 */
@Component
public class TestFixtures {

    @Autowired private SupplierRepository supplierRepository;
    @Autowired private ProductRepository productRepository;
    @Autowired private PointRepository pointRepository;
    @Autowired private CabinetRepository cabinetRepository;
    @Autowired private ChannelRepository channelRepository;
    @Autowired private ProductBatchRepository batchRepository;
    @Autowired private AppUserRepository userRepository;

    private long seq = System.nanoTime() % 100_000;

    public AppUser user(String role) {
        String name = role.toLowerCase() + "_" + (seq++);
        return userRepository.save(AppUser.builder()
                .username(name).password("{noop}x").role(role).displayName(role).build());
    }

    public Supplier supplier() {
        return supplierRepository.save(Supplier.builder()
                .code("S" + seq++).name("测试供应商" + seq)
                .paymentTermDays(30).balancePayable(BigDecimal.ZERO).build());
    }

    public Product chilledProduct(Supplier s) {
        return productRepository.save(Product.builder()
                .sku("SKU-" + seq++).name("鲜牛奶").category("乳品")
                .tempZone(TempZone.CHILLED).shelfLifeDays(7)
                .supplier(s).costPrice(new BigDecimal("2.80"))
                .retailPrice(new BigDecimal("5.50")).unitWeightGram(260).build());
    }

    public Point point(LocationType type) {
        return pointRepository.save(Point.builder()
                .code("PT" + seq++).name("测试点位").type(type).address("测试路").build());
    }

    public Cabinet chilledCabinet(Point p) {
        return cabinetRepository.save(Cabinet.builder()
                .cabinetNo("CAB-" + seq++).point(p).model("C5")
                .tempZone(TempZone.CHILLED).tempMin(0.0).tempMax(8.0)
                .lastTempC(4.0).status(CabinetStatus.NORMAL).build());
    }

    public Channel channel(Cabinet c, Product p, String code) {
        return channelRepository.save(Channel.builder()
                .cabinet(c).code(code).plannedProduct(p).capacity(20).build());
    }

    public ProductBatch batch(Product p, int expiryInDays, int received) {
        Supplier s = p.getSupplier();
        return batchRepository.save(ProductBatch.builder()
                .batchNo("B-" + seq++).product(p).supplier(s)
                .productionDate(LocalDate.now().minusDays(1))
                .expiryDate(LocalDate.now().plusDays(expiryInDays))
                .receivedQty(received).costPrice(p.getCostPrice())
                .status(BatchStatus.IN_STOCK).build());
    }
}
