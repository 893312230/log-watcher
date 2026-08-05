package com.smartops.infrastructure.config;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.scheduling.annotation.*;
import org.springframework.stereotype.Component;

@Component @EnableScheduling
public class DataRetentionScheduler {
    private static final Logger log = LoggerFactory.getLogger(DataRetentionScheduler.class);
    @Autowired private JdbcTemplate jdbc;
    @Scheduled(cron = "0 0 3 * * ?")
    @Transactional
    public void cleanup() {
        int audit = jdbc.update("DELETE FROM audit_event WHERE created_at < DATE_SUB(NOW(), INTERVAL 30 DAY)");
        int alerts = jdbc.update("DELETE FROM alert_record WHERE created_at < DATE_SUB(NOW(), INTERVAL 90 DAY)");
        if (audit > 0 || alerts > 0) log.info("data retention: audit={}, alerts={}", audit, alerts);
    }
}
