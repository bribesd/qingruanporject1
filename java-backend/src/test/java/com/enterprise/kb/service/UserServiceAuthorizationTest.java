package com.enterprise.kb.service;

import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.security.AuthUser;
import com.enterprise.kb.util.AuditLogger;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;

class UserServiceAuthorizationTest {

    private final UserService userService = new UserService(mock(JdbcTemplate.class), mock(AuditLogger.class));
    private final AuthUser admin = new AuthUser(2L, "admin-user", "admin", "active");

    @Test
    void adminMustProvideValidCreateData() {
        ApiException error = assertThrows(ApiException.class, () -> userService.create(Map.of(), admin));
        assertEquals(400, error.getStatus());
    }

    @Test
    void adminCannotModifyUserRole() {
        ApiException error = assertThrows(ApiException.class,
                () -> userService.update(1L, Map.of("roleId", 1), admin));
        assertEquals(403, error.getStatus());
    }

    @Test
    void adminGetsNotFoundWhenDeletingMissingUser() {
        ApiException error = assertThrows(ApiException.class, () -> userService.delete(1L, admin));
        assertEquals(404, error.getStatus());
    }
}
