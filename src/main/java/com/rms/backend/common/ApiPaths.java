package com.rms.backend.common;

/** Canonical HTTP paths shared by controllers, security and the admin client configuration. */
public final class ApiPaths {
    private ApiPaths() {}
    public static final String API = "/api";
    public static final String API_PATTERN = "/api/**";
    public static final String AUTH = "/api/auth";
    public static final String ADMIN = "/api/admin";
    public static final String ADMIN_UI = "/admin/index.html";
    public static final String ADMIN_ASSETS = "/admin/**";
    public static final String CLIENT_CONFIG = "/api/client-config";
    public static final String BOOTSTRAP = "/bootstrap";
    public static final String OWNERS = "/owners";
    public static final String OWNER_PROPERTIES = "/owners/{id}/properties";
    public static final String OWNER_ID = "/owners/{id}";
    public static final String OWNER_STATUS = "/owners/{id}/status";
    public static final String OWNER_PASSWORD = "/owners/{id}/password";
    public static final String PASSWORD = "/password";
    public static final String REGISTER = "/register";
    public static final String LOGIN = "/login";
    public static final String LOGOUT = "/logout";
    public static final String ME = "/me";
    public static final String WHATSAPP_WEBHOOK = "/whatsapp/webhook";
    public static final String WEBHOOKS_VACATE_REQUEST = "/webhooks/vacate-request";
    public static final String VACATE_WEBHOOK = "/vacate/webhook";
    public static final String PAYMENTS_WEBHOOK = "/payments/webhook";
    public static final String WEBHOOKS_PAYMENTS = "/webhooks/payments";
    public static final String WEBHOOKS_STATUS = "/webhooks/status";
    public static final String WEBHOOKS_HEALTH = "/webhooks/health";
    public static final String API_VACATE_REQUESTS = "/api/vacate-requests";
    public static final String ID = "/{id}";
    public static final String ID_APPROVE = "/{id}/approve";
    public static final String ID_REJECT = "/{id}/reject";
    public static final String ID_CHARGES = "/{id}/charges";
    public static final String ID_COMPLETE = "/{id}/complete";
    public static final String API_SYSTEM = "/api/system";
    public static final String CLEAN_DATABASE = "/clean-database";
    public static final String API_TENANTS = "/api/tenants";
    public static final String UID = "/{uid}";
    public static final String ROOM_ROOMNO = "/room/{roomNo}";
    public static final String UID_VERIFY_ADVANCE = "/{uid}/verify-advance";
    public static final String API_PROPERTIES = "/api/properties";
    public static final String API_USERS = "/api/users";
    public static final String API_ADMISSIONS = "/api/admissions";
    public static final String CHECK_EXISTING = "/check-existing";
    public static final String ADMISSIONNUMBER = "/{admissionNumber}";
    public static final String ADMISSIONNUMBER_CONFIRM = "/{admissionNumber}/confirm";
    public static final String API_ROOMS = "/api/rooms";
    public static final String ROOMNO = "/{roomNo}";
    public static final String API_NOTIFICATIONS_WHATSAPP = "/api/notifications/whatsapp";
    public static final String WELCOME_PREVIEW_TENANTUID = "/welcome-preview/{tenantUid}";
    public static final String SEND_WELCOME_TENANTUID = "/send-welcome/{tenantUid}";
    public static final String API_INVOICES = "/api/invoices";
    public static final String GENERATE_CYCLE = "/generate-cycle";
    public static final String ID_PAY = "/{id}/pay";
    public static final String API_LEDGER = "/api/ledger";
    public static final String BALANCE = "/balance";
}
