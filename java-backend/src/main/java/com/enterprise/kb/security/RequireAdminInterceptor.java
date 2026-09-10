package com.enterprise.kb.security;

import com.enterprise.kb.exception.ApiException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.method.HandlerMethod;
import org.springframework.web.servlet.HandlerInterceptor;

/**
 * 管理员拦截器，等价 middleware/requireAdmin.js：
 * 对标注 @RequireAdmin 的接口校验当前用户角色。
 */
@Component
public class RequireAdminInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
        if (!(handler instanceof HandlerMethod handlerMethod)) {
            return true;
        }
        boolean superAdminRequired = handlerMethod.hasMethodAnnotation(RequireSuperAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireSuperAdmin.class);
        boolean required = handlerMethod.hasMethodAnnotation(RequireAdmin.class)
                || handlerMethod.getBeanType().isAnnotationPresent(RequireAdmin.class);
        if (!required && !superAdminRequired) {
            return true;
        }

        AuthUser user = UserContext.get();
        if (user == null || (superAdminRequired ? !user.isSuperAdmin() : !user.isAdmin())) {
            throw new ApiException(403, "无权限执行此操作");
        }
        return true;
    }
}
