package com.rms.backend.security;

import com.rms.backend.users.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request;

@SpringBootTest(properties = {"spring.datasource.url=jdbc:h2:mem:provisioning;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
        "rms.security.bootstrap-key=test-bootstrap-secret-with-32-characters"})
@AutoConfigureMockMvc
class ProvisioningIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    private final ObjectMapper mapper = new ObjectMapper();
    private String call(String method, String path, String token, String json, String key, int status) throws Exception {
        var req = request(HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (key != null) req.header("X-Bootstrap-Key", key);
        if (json != null) req.content(json);
        var result = mvc.perform(req).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return result.getResponse().getContentAsString();
    }
    private String account(String name) {
        return "{\"username\":\""+name+"\",\"email\":\""+name+"@example.com\",\"fullName\":\"Test\",\"password\":\"initial-password-123\"}";
    }
    private String login(String name, String password, int status) throws Exception {
        var result = call("POST","/api/auth/login",null,"{\"username\":\""+name+"\",\"password\":\""+password+"\"}",null,status);
        return status == 200 ? mapper.readTree(result).get("accessToken").asText() : null;
    }
    @Test void rootLifecycleAndProductionDefaults() throws Exception {
        String key = "test-bootstrap-secret-with-32-characters";
        call("GET", "/admin/index.html",null,null,null,200);
        call("POST","/api/auth/register",null,account("public-owner"),null,403);
        call("POST","/api/admin/bootstrap",null,account("root-admin"),"incorrect",403);
        call("POST","/api/admin/bootstrap",null,account("root-admin"),key,201);
        call("POST","/api/admin/bootstrap",null,account("second-root"),key,409);
        assertFalse(mapper.readTree(call("GET","/api/client-config",null,null,null,200)).get("setupAvailable").asBoolean());
        String root = login("root-admin","initial-password-123",200);
        var owner = mapper.readTree(call("POST","/api/admin/owners",root,account("new-owner"),null,201));
        long ownerId = owner.get("id").asLong();
        assertNull(owner.get("passwordHash"));
        String token = login("new-owner","initial-password-123",200);
        call("GET","/api/admin/owners",token,null,null,403);
        call("POST","/api/admin/owners",token,account("escalated-owner"),null,403);
        call("PUT","/api/admin/owners/"+ownerId+"/status",root,"{\"status\":\"INACTIVE\"}",null,200);
        call("GET","/api/auth/me",token,null,null,401);
        call("PUT","/api/admin/owners/"+ownerId+"/status",root,"{\"status\":\"ACTIVE\"}",null,200);
        call("GET","/api/auth/me",token,null,null,401);
        for (int i=0;i<5;i++) login("new-owner","wrong-password",401);
        assertNotNull(users.findById(ownerId).orElseThrow().getLockedUntil());
        login("new-owner","initial-password-123",401);
        call("PUT","/api/admin/owners/"+ownerId+"/password",root,"{\"password\":\"reset-password-123\"}",null,204);
        token = login("new-owner","reset-password-123",200);
        call("POST","/api/auth/password",token,"{\"currentPassword\":\"reset-password-123\",\"newPassword\":\"changed-password-123\"}",null,204);
        call("GET","/api/auth/me",token,null,null,401);
        login("new-owner","changed-password-123",200);
        var headers = mvc.perform(request(HttpMethod.GET,"/admin/index.html")).andReturn().getResponse();
        assertNotNull(headers.getHeader("Content-Security-Policy"));
        assertEquals("nosniff",headers.getHeader("X-Content-Type-Options"));
    }
}
