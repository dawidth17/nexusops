export const config = Object.freeze({
    issuer:
        import.meta.env.VITE_OIDC_ISSUER?.trim()
        || "http://127.0.0.1:8081/realms/nexusops",

    clientId: "nexusops-console",

    scope: "openid profile email",

    opsSightBaseUrl:
        "/opssight/api/v1",

    serviceCoreBaseUrl:
        "/servicecore/api/v1"
});