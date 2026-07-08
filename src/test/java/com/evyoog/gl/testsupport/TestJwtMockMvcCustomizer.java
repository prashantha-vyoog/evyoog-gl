package com.evyoog.gl.testsupport;

import com.evyoog.gl.auth.domain.Permission;
import com.evyoog.gl.auth.domain.User;
import com.evyoog.gl.auth.repository.PermissionRepository;
import com.evyoog.gl.auth.service.JwtService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.webmvc.test.autoconfigure.MockMvcBuilderCustomizer;
import org.springframework.stereotype.Component;
import org.springframework.test.web.servlet.request.MockMvcRequestBuilders;
import org.springframework.test.web.servlet.setup.ConfigurableMockMvcBuilder;

import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * AUTH-01 secures every existing endpoint with @PreAuthorize. The ~25 pre-existing
 * IT suites predate authentication and issue requests with no bearer token, so this
 * customizer merges a superuser JWT (every permission in auth.permissions) into every
 * MockMvc request as a default header. Per-request headers set explicitly in a test
 * override this default (ConfigurableMockMvcBuilder#defaultRequest), so AUTH-specific
 * IT tests can still exercise 401/403 paths by supplying their own Authorization header.
 */
@Component
@RequiredArgsConstructor
public class TestJwtMockMvcCustomizer implements MockMvcBuilderCustomizer {

    private final JwtService jwtService;
    private final PermissionRepository permissionRepository;

    @Override
    public void customize(ConfigurableMockMvcBuilder<?> builder) {
        Set<String> allPermissions = permissionRepository.findAll().stream()
                .map(Permission::getCode)
                .collect(Collectors.toSet());

        User superuser = User.builder()
                .id(UUID.randomUUID())
                .email("it-superuser@evyoog.test")
                .fullName("IT Superuser")
                .build();

        String token = jwtService.generateAccessToken(superuser, UUID.randomUUID(), allPermissions);
        builder.defaultRequest(MockMvcRequestBuilders.get("/").header("Authorization", "Bearer " + token));
    }
}
