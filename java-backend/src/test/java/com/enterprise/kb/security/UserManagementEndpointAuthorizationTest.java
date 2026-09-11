package com.enterprise.kb.security;

import com.enterprise.kb.controller.UserController;
import com.enterprise.kb.exception.ApiException;
import com.enterprise.kb.service.UserService;
import com.enterprise.kb.util.AuditLogger;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.method.HandlerMethod;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class UserManagementEndpointAuthorizationTest {

    private final RequireAdminInterceptor interceptor = new RequireAdminInterceptor();
    private final UserController controller = new UserController(mock(UserService.class));
    private final HttpServletRequest request = mock(HttpServletRequest.class);
    private final HttpServletResponse response = mock(HttpServletResponse.class);

    @AfterEach
    void clearContext() {
        UserContext.clear();
    }

    @Test
    void adminCanAccessCreateUserEndpoint() throws Exception {
        assertAllowed("create", Map.class);
    }

    @Test
    void adminCanAccessUpdateUserEndpoint() throws Exception {
        assertAllowed("update", long.class, Map.class);
    }

    @Test
    void adminCanAccessDeleteUserEndpoint() throws Exception {
        assertAllowed("delete", long.class);
    }

    @Test
    void ordinaryAdminCannotDeleteOtherAdminAccount() {
        JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
        when(jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, "super_admin"))
                .thenReturn(1L);
        when(jdbcTemplate.queryForObject("SELECT id FROM roles WHERE name = ?", Long.class, "admin"))
                .thenReturn(2L);
        when(jdbcTemplate.queryForList("SELECT id, role_id FROM users WHERE id = ?", 3L))
                .thenReturn(List.of(Map.of("id", 3L, "role_id", 2L)));

        UserService userService = new UserService(jdbcTemplate, mock(AuditLogger.class));
        AuthUser currentUser = new AuthUser(1L, "manager", "admin", "active");

        ApiException ex = assertThrows(ApiException.class, () -> userService.delete(3L, currentUser));
        assertEquals(403, ex.getStatus());
    }

    private void assertAllowed(String methodName, Class<?>... parameterTypes) throws Exception {
        UserContext.set(new AuthUser(2L, "ordinary-admin", "admin", "active"));
        HandlerMethod handler = new HandlerMethod(controller,
                UserController.class.getMethod(methodName, parameterTypes));

        boolean allowed = interceptor.preHandle(request, response, handler);
        assertEquals(true, allowed);
    }
}
