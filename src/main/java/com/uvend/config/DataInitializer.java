package com.uvend.config;

import com.uvend.domain.entity.*;
import com.uvend.domain.enums.LocationType;
import com.uvend.domain.enums.Role;
import com.uvend.domain.enums.TempClass;
import com.uvend.domain.repo.*;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;

/** 初始化演示数据：测试账号、供应商、商品、点位货柜、货道与批次。 */
@Configuration
public class DataInitializer {

    @Bean
    @Transactional
    public ApplicationRunner seed(AppUserRepository users, SupplierRepository suppliers,
                                  ProductRepository products, MachineRepository machines,
                                  SlotRepository slots, BatchRepository batches,
                                  PasswordEncoder encoder) {
        return args -> {
            if (users.existsByUsername("admin")) return;

            String pw = encoder.encode("password123");
            AppUser admin = user(users, pw, "admin", "系统管理员", Role.ADMIN, null);
            AppUser operator = user(users, pw, "operator", "运营小李", Role.OPERATOR, null);
            AppUser staff = user(users, pw, "staff", "补货员老王", Role.STAFF, null);
            AppUser finance = user(users, pw, "finance", "财务小周", Role.FINANCE, null);
            AppUser customer = user(users, pw, "customer", "居民张阿姨", Role.CUSTOMER, null);
            user(users, pw, "supplier", "供应商联系人", Role.SUPPLIER, null); // supplierId 在下方回填

            Supplier fresh = supplier(suppliers, "SUP01", "鲜每日乳业", true);
            Supplier snack = supplier(suppliers, "SUP02", "咔哧零食", false);
            // 供应商账号绑定 SUP01
            users.findByUsername("supplier").ifPresent(u -> {
                u.setSupplierId(fresh.getId());
                users.save(u);
            });

            Product milk = product(products, "MILK250", "鲜牛奶250ml", "乳品", fresh,
                    "6.50", "4.00", 7, TempClass.CHILLED);
            Product yogurt = product(products, "YOG180", "低温酸奶180g", "乳品", fresh,
                    "8.00", "5.00", 10, TempClass.CHILLED);
            Product cookie = product(products, "COK100", "巧克力曲奇100g", "零食", snack,
                    "9.90", "5.50", 180, TempClass.AMBIENT);
            Product bread = product(products, "BRD120", "短保三明治", "鲜食", fresh,
                    "12.00", "7.00", 3, TempClass.CHILLED);

            Machine community = machine(machines, "VM-C001", "阳光社区东门柜", LocationType.COMMUNITY,
                    "阳光社区东门大堂", TempClass.CHILLED, new BigDecimal("2.0"), new BigDecimal("8.0"));
            Machine office = machine(machines, "VM-O001", "创智大厦A座柜", LocationType.OFFICE,
                    "创智大厦A座12层茶水间", TempClass.AMBIENT, null, null);
            machine(machines, "VM-H001", "市中心医院门诊柜", LocationType.HOSPITAL,
                    "门诊楼一层便利店区", TempClass.CHILLED, new BigDecimal("2.0"), new BigDecimal("8.0"));

            // 社区冷藏柜货道 A01-A04
            slot(slots, community, "A01", milk, 8);
            slot(slots, community, "A02", yogurt, 8);
            slot(slots, community, "A03", bread, 6);
            slot(slots, community, "A04", null, 6);
            // 办公楼常温柜 B01-B02
            slot(slots, office, "B01", cookie, 12);
            slot(slots, office, "B02", null, 12);

            LocalDate today = LocalDate.now();
            // 批次：一个正常、一个明日到期（用于演示临期）、一个已过期
            batch(batches, "B-MILK-" + today.minusDays(1), milk, fresh, today.minusDays(1), today.plusDays(6), 24);
            batch(batches, "B-YOG-NEAR", yogurt, fresh, today.minusDays(9), today.plusDays(1), 16);
            batch(batches, "B-BREAD-EXP", bread, fresh, today.minusDays(5), today.minusDays(2), 8);
            batch(batches, "B-COK-NEW", cookie, snack, today.minusDays(30), today.plusDays(150), 36);
        };
    }

    private AppUser user(AppUserRepository repo, String pw, String username, String name, Role role,
                         Long supplierId) {
        AppUser u = new AppUser(username, pw, name, role);
        u.setSupplierId(supplierId);
        return repo.save(u);
    }

    private Supplier supplier(SupplierRepository repo, String code, String name, boolean bearLoss) {
        Supplier s = new Supplier();
        s.setCode(code); s.setName(name); s.setContact(name + " 400-000-0000"); s.setBearLoss(bearLoss);
        return repo.save(s);
    }

    private Product product(ProductRepository repo, String sku, String name, String category, Supplier s,
                            String price, String cost, int life, TempClass temp) {
        Product p = new Product();
        p.setSku(sku); p.setName(name); p.setCategory(category); p.setSupplierId(s.getId());
        p.setUnitPrice(new BigDecimal(price)); p.setCostPrice(new BigDecimal(cost));
        p.setShelfLifeDays(life); p.setTempRequirement(temp);
        return repo.save(p);
    }

    private Machine machine(MachineRepository repo, String code, String name, LocationType loc,
                            String address, TempClass type, BigDecimal tmin, BigDecimal tmax) {
        Machine m = new Machine();
        m.setCode(code); m.setName(name); m.setLocationType(loc); m.setAddress(address);
        m.setMachineType(type); m.setTempMin(tmin); m.setTempMax(tmax);
        return repo.save(m);
    }

    private void slot(SlotRepository repo, Machine m, String code, Product p, int capacity) {
        Slot s = new Slot();
        s.setMachineId(m.getId()); s.setCode(code);
        s.setProductId(p == null ? null : p.getId()); s.setCapacity(capacity);
        repo.save(s);
    }

    private void batch(BatchRepository repo, String no, Product p, Supplier s,
                       LocalDate produced, LocalDate expiry, int qty) {
        Batch b = new Batch();
        b.setBatchNo(no); b.setProductId(p.getId()); b.setSupplierId(s.getId());
        b.setProductionDate(produced); b.setExpiryDate(expiry); b.setInboundQty(qty);
        repo.save(b);
    }
}
