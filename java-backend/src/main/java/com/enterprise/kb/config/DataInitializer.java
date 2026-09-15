package com.enterprise.kb.config;

import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * 数据初始化：合并原 initDb.js 与 seedUsers.js 的逻辑，幂等写入种子数据。
 * 运行在 schema.sql 建表之后。
 */
@Component
@ConditionalOnProperty(prefix = "app.bootstrap", name = "enabled", havingValue = "true")
public class DataInitializer implements ApplicationRunner {

    private final JdbcTemplate jdbcTemplate;
    private final String adminUsername;
    private final String adminPassword;
    private final BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();

    public DataInitializer(JdbcTemplate jdbcTemplate,
                           @Value("${app.bootstrap.admin-username}") String adminUsername,
                           @Value("${app.bootstrap.admin-password}") String adminPassword) {
        this.jdbcTemplate = jdbcTemplate;
        this.adminUsername = adminUsername;
        this.adminPassword = adminPassword;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (adminPassword == null || adminPassword.isBlank()) {
            throw new IllegalStateException("APP_INITIAL_ADMIN_PASSWORD must be set when bootstrap is enabled");
        }
        seedRoles();
        seedAdmin();
        seedCategories();
        seedKnowledge();
    }

    private void seedRoles() {
        if (count("roles") == 0) {
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "super_admin", "超级管理员");
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "admin", "管理员");
            jdbcTemplate.update("INSERT INTO roles (name, description) VALUES (?, ?)", "user", "普通用户");
        }
    }

    private void seedAdmin() {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM users WHERE username = ?", Integer.class, adminUsername);
        if (count == null || count == 0) {
            Long adminRoleId = roleId("super_admin");
            String hash = passwordEncoder.encode(adminPassword);
            jdbcTemplate.update(
                    "INSERT INTO users (username, password, real_name, email, role_id, status) VALUES (?, ?, ?, ?, ?, ?)",
                    adminUsername, hash, "System Administrator", adminUsername + "@localhost", adminRoleId, "active");
        }
    }

    private void seedCategories() {
        String[] names = {"公司制度", "产品说明", "技术文档", "FAQ", "流程规范"};
        for (String name : names) {
            Integer c = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM categories WHERE name = ?", Integer.class, name);
            if (c == null || c == 0) {
                jdbcTemplate.update("INSERT INTO categories (name) VALUES (?)", name);
            }
        }
    }

    private void seedKnowledge() {
        List<Long> adminIds = jdbcTemplate.queryForList(
                "SELECT id FROM users WHERE username = ?", Long.class, adminUsername);
        if (adminIds.isEmpty()) {
            throw new IllegalStateException("Bootstrap administrator not found: " + adminUsername);
        }
        long adminId = adminIds.get(0);

        Object[][] seed = {
                {"员工考勤管理制度", "公司制度", "员工应按规定打卡考勤，因故迟到、早退或请假须提前办理审批手续。"},
                {"产品试用申请说明", "产品说明", "试用产品前请填写申请信息并说明使用目的，审核通过后即可获得试用权限。"},
                {"密码安全配置规范", "技术文档", "密码应设置为高强度组合并定期更换，不得与他人共享或重复使用。"},
                {"如何申请账号权限？", "FAQ", "请通过权限申请入口提交账号、所需权限及申请理由，经负责人审批后开通。"},
                {"客户问题处理流程", "流程规范", "客户问题应及时登记、分类、分派并跟进处理，完成后确认结果并记录归档。"},
                {"员工手册（2026版）", "公司制度", "附件：员工手册.pdf。适用于新员工入职培训、岗位说明及日常工作规范。"},
                {"采购审批与付款说明", "公司制度", "附件：采购审批流程.pdf。请在报销、付款和供应商结算前严格按流程执行审批。"},
                {"网络安全事件上报流程", "技术文档", "附件：信息安全事件上报模板.docx。发现异常访问、恶意代码或资料泄露需立即上报。"},
                {"常见问题排查手册", "FAQ", "附件：FAQ排查清单.xlsx。适合客服、IT 和业务支持同事对常见问题做快速判断。"},
                {"CRM 客户问题处理模板", "流程规范", "附件：客户问题处理模板.docx。用于记录问题归因、处理步骤、结果确认与回访记录。"}
        };

        for (Object[] item : seed) {
            String title = (String) item[0];
            String categoryName = (String) item[1];
            String content = (String) item[2];
            Integer exists = jdbcTemplate.queryForObject(
                    "SELECT COUNT(*) FROM knowledge_items WHERE title = ?", Integer.class, title);
            if (exists != null && exists > 0) {
                continue;
            }

            List<Long> categoryIds = jdbcTemplate.queryForList(
                    "SELECT id FROM categories WHERE name = ?", Long.class, categoryName);
            if (categoryIds.isEmpty()) {
                continue;
            }
            jdbcTemplate.update(
                    "INSERT INTO knowledge_items (title, content, category_id, author_id, status) VALUES (?, ?, ?, ?, ?)",
                    title, content, categoryIds.get(0), adminId, "published");
        }
    }

    private Long roleId(String name) {
        List<Long> ids = jdbcTemplate.queryForList(
                "SELECT id FROM roles WHERE name = ?", Long.class, name);
        return ids.isEmpty() ? null : ids.get(0);
    }

    private long count(String table) {
        Long c = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM " + table, Long.class);
        return c == null ? 0 : c;
    }
}
