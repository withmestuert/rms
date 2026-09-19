package com.rms.backend.security;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.http.MediaType;
import tools.jackson.databind.ObjectMapper;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.junit.jupiter.api.Assertions.*;
import com.rms.backend.users.repository.UserRepository;
import com.rms.backend.properties.repository.PropertyRepository;
import java.util.Set;

@SpringBootTest(properties = "rms.security.self-registration-enabled=true") @AutoConfigureMockMvc
class AuthorizationIntegrationTest {
    @Autowired MockMvc mvc;
    @Autowired UserRepository users;
    @Autowired PropertyRepository properties;
    @Autowired AuthSessionRepository sessions;
    private final ObjectMapper json = new ObjectMapper();

    private MvcResult request(String method, String path, String token, String body, int status) throws Exception {
        var req = org.springframework.test.web.servlet.request.MockMvcRequestBuilders.request(
                org.springframework.http.HttpMethod.valueOf(method), path).contentType(MediaType.APPLICATION_JSON);
        if (token != null) req.header("Authorization", "Bearer " + token);
        if (body != null) req.content(body);
        var result = mvc.perform(req).andReturn();
        assertEquals(status, result.getResponse().getStatus(), result.getResponse().getContentAsString());
        return result;
    }
    private String token(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()).get("accessToken").asText(); }
    private long id(MvcResult r) throws Exception { return json.readTree(r.getResponse().getContentAsString()).get("id").asLong(); }
    private String register(String name) throws Exception {
        return token(request("POST", "/api/auth/register", null,
                "{\"username\":\"" + name + "\",\"email\":\"" + name + "@test.example\",\"fullName\":\"Owner\",\"password\":\"very-secure-password\"}",201));
    }
    private String login(String name) throws Exception {
        return token(request("POST", "/api/auth/login", null,
                "{\"username\":\"" + name + "\",\"password\":\"very-secure-password\"}",200));
    }
    @Test void hierarchyIsolationRevocationAndReadOnlyAccess() throws Exception {
        request("GET", "/api/properties", null, null,401);
        String owner = register("owner-a"), other = register("owner-b");
        long p1 = id(request("POST", "/api/properties",owner,"{\"name\":\"Owner A PG 1\"}",201));
        long p2 = id(request("POST", "/api/properties",owner,"{\"name\":\"Owner A PG 2\"}",201));
        long p3 = id(request("POST", "/api/properties",other,"{\"name\":\"Owner B PG\"}",201));
        request("GET", "/api/properties/"+p3, owner,null,403);
        var list = request("GET", "/api/properties",owner,null,200);
        assertEquals(2,json.readTree(list.getResponse().getContentAsString()).size());
        String member = "{\"username\":\"rep\",\"email\":\"rep@test.example\",\"fullName\":\"Representative\",\"password\":\"very-secure-password\",\"role\":\"REPRESENTATIVE\",\"propertyIds\":["+p1+","+p2+"]}";
        long repId = id(request("POST", "/api/users",owner,member,201));
        String rep = login("rep");
        request("GET", "/api/properties/"+p1,rep,null,200);
        request("GET", "/api/properties/"+p2,rep,null,200);
        request("GET", "/api/properties/"+p3,rep,null,403);
        request("POST", "/api/properties",rep,"{\"name\":\"Forbidden\"}",403);
        request("POST", "/api/users",rep,member,403);
        request("PUT", "/api/users/"+repId,other,member,403);
        String room = "{\"roomNo\":\"A-1\",\"floor\":\"1\",\"roomType\":\"SINGLE\",\"rentPerMonth\":5000,\"occupancy\":1,\"available\":true,\"propertyId\":"+p1+"}";
        request("POST", "/api/rooms",rep,room,201);
        request("GET", "/api/rooms/A-1",other,null,403);
        String tenant = """
            {"uid":"tenant-a","name":"Resident","aadhaarNo":"123456789012","mobileNumber":"9876543210",
             "tenantType":"Working","organizationName":"Company","roomNo":"A-1","advancePaid":5000,"standardRent":5000}
            """;
        request("POST", "/api/tenants",rep,tenant,201);
        request("GET", "/api/tenants/tenant-a",other,null,403);
        request("DELETE", "/api/tenants/tenant-a",other,null,403);
        var lookup = request("GET", "/api/admissions/check-existing?mobileNumber=9876543210",other,null,200);
        assertFalse(json.readTree(lookup.getResponse().getContentAsString()).get("exists").asBoolean());
        String invoice = "{\"tenantUid\":\"tenant-a\",\"monthYear\":\"September 2026\",\"amount\":5000,\"dueDate\":\"2026-09-30\"}";
        request("POST", "/api/invoices",other,invoice,403);
        request("POST", "/api/invoices",rep,invoice.replace("}", ",\"propertyId\":"+p3+"}"),400);
        long invoiceId = id(request("POST", "/api/invoices",rep,invoice,201));
        request("GET", "/api/invoices/"+invoiceId,other,null,403);
        request("POST", "/api/vacate-requests",other,"{\"tenantUid\":\"tenant-a\"}",403);
        long vacateId = id(request("POST", "/api/vacate-requests",rep,"{\"tenantUid\":\"tenant-a\"}",201));
        request("PUT", "/api/vacate-requests/"+vacateId+"/approve",other,null,403);
        request("GET", "/api/notifications/whatsapp/welcome-preview/tenant-a",other,null,403);
        for (String endpoint : java.util.List.of("tenants", "invoices", "vacate-requests", "rooms", "admissions", "ledger")) {
            var empty = request("GET", "/api/"+endpoint,other,null,200);
            assertEquals(0,json.readTree(empty.getResponse().getContentAsString()).size());
        }

        request("GET", "/api/rooms?propertyId="+p3,rep,null,403);
        request("POST", "/api/rooms",rep,room.replace("A-1", "spoof").replace("\"propertyId\":"+p1,"\"propertyId\":"+p3),403);

        request("POST", "/api/users",owner,member.replace("rep@test", "sub@test").replace("\"rep\"", "\"sub\"").replace("REPRESENTATIVE", "SUB_MEMBER"),201);
        String sub = login("sub");
        request("GET", "/api/rooms/A-1",sub,null,200);
        request("DELETE", "/api/rooms/A-1",sub,null,403);
        request("POST", "/api/invoices/generate-cycle",sub,"{}",403);
        request("GET", "/api/users",sub,null,403);
        request("POST", "/api/system/clean-database",owner,"{}",403);
        request("POST", "/api/webhooks/vacate-request",owner,"{}",403);
        request("PUT", "/api/users/"+repId,owner,member.replace("["+p1+","+p2+"]", "["+p3+"]"),403);
        // Grants are reloaded on every request, even for an already-issued token.
        var user = users.findById(repId).orElseThrow(); user.setPropertyIds(Set.of(p1)); users.save(user);
        request("GET", "/api/properties/"+p2,rep,null,403);
        request("GET", "/api/properties/"+p1,rep,null,200);
        assertNotEquals("very-secure-password",user.getPasswordHash());
        assertFalse(sessions.existsById(rep));
        request("POST", "/api/users",owner,member.replace("REPRESENTATIVE", "OWNER"),400);
        var expired = sessions.findById(AuthService.hash(rep)).orElseThrow();
        expired.setExpiresAt(java.time.Instant.now().minusSeconds(1)); sessions.save(expired);
        request("GET", "/api/auth/me",rep,null,401);
        rep = login("rep");
        request("POST", "/api/auth/logout",rep,null,204);
        request("GET", "/api/auth/me",rep,null,401);
        request("POST", "/api/auth/logout",sub,null,204);
        sub = login("sub");
        var ownerUser = users.findByUsername("owner-a").orElseThrow(); ownerUser.setStatus("INACTIVE"); users.save(ownerUser);
        request("GET", "/api/auth/me",sub,null,401);
        request("POST", "/api/auth/login",null,"{\"username\":\"owner-b\",\"password\":\"wrong-password\"}",401);
    }
}
