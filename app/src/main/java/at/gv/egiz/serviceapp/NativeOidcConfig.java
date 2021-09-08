package at.gv.egiz.serviceapp;

public class NativeOidcConfig {

    /**
     * Has to be a registered URL scheme for this activity, see AndroidManifest.xml
     * (Note: Has to be registered for this RP on the OpenId Authorization Server)
     */
    public String getNativeUrlScheme() {
        return "at.asitplus.notes.app";
    }

    /**
     * Has to be a registered URL scheme for this activity, see AndroidManifest.xml
     * (Note: Has to be registered for this RP on the OpenId Authorization Server)
     */
    public String getWebRedirectUrl() {
        return getWebRelyingPartyUrl() + "login/oauth2/code/eid";
    }

    public String getClientId() {
        return "https://eid.a-sit.at/notes";
    }

    public String getAuthorizationServerUrl() {
        return "https://eid.egiz.gv.at/";
    }

    public String getNativeRedirectUrl() {
        return getNativeUrlScheme() + ":/oauth2redirect";
    }

    public String getNativeRelyingPartyUrl() {
        return "https://eid.a-sit.at/notes-api/";
    }

    public String getWebRelyingPartyUrl() {
        return "https://eid.a-sit.at/notes/";
    }

    public String getWebManualLoginUrl() {
        return getWebRelyingPartyUrl() + "manuallogin";
    }
}
