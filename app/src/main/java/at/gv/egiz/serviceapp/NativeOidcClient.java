package at.gv.egiz.serviceapp;

import android.net.Uri;
import android.util.Base64;
import android.util.Log;

import androidx.annotation.NonNull;
import androidx.annotation.WorkerThread;

import com.nimbusds.jwt.SignedJWT;

import org.json.JSONObject;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Objects;

import okhttp3.FormBody;
import okhttp3.HttpUrl;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import okhttp3.ResponseBody;
import okio.Buffer;

public class NativeOidcClient {

    private static final String TAG = "NativeOidcClient";

    private static final String GRANT_TYPE_AUTHORIZATION_CODE = "authorization_code";

    private static final String PARAM_ACCESS_TOKEN = "access_token";
    private static final String PARAM_CLIENT_ID = "client_id";
    private static final String PARAM_CODE = "code";
    private static final String PARAM_NONCE = "nonce";
    private static final String PARAM_GRANT_TYPE = "grant_type";
    private static final String PARAM_ID_TOKEN = "id_token";
    private static final String PARAM_REDIRECT_URI = "redirect_uri";
    private static final String PARAM_RESPONSE_TYPE = "response_type";
    private static final String PARAM_SCOPE = "scope";
    private static final String PARAM_STATE = "state";
    private static final String PARAM_CODE_CHALLENGE = "code_challenge";
    private static final String PARAM_CODE_CHALLENGE_METHOD = "code_challenge_method";
    private static final String PARAM_CODE_VERIFIER = "code_verifier";

    private static final String SCOPE_OPENID = "openid";

    private static final String PREF_KEY_VERIFIER = "verifier";
    private static final String PREF_KEY_NONCE = "nonce";
    private static final String PREF_KEY_STATE = "state";

    private static final String CHALLENGE_METHOD_S256 = "S256";
    private static final String HEADER_AUTHORIZATION = "Authorization";
    private static final String HEADER_AUTHORIZATION_BEARER = "Bearer";

    public interface Delegate {
        void startViewIntentAndFinish(String urlString);

        void storePreference(String key, String value);

        String loadPreference(String key);

        void success(String text);
    }

    private final Delegate delegate;
    // PKCE would be strongly advised, if this app holds the id_token itself
    private final boolean enablePkce;
    // control whether to use REGISTERED_URL_SCHEME or REGISTERED_HTTPS_RETURN_URL
    private final boolean useHttpsInsteadOfNativeUrl;
    private final NativeOidcConfig config;
    private String authorizationServerAuthUrl;
    private String authorizationServerTokenUrl;

    public NativeOidcClient(Delegate delegate, boolean enablePkce, boolean useHttpsInsteadOfNativeUrl,
            NativeOidcConfig config) {
        this.delegate = delegate;
        this.enablePkce = enablePkce;
        this.useHttpsInsteadOfNativeUrl = useHttpsInsteadOfNativeUrl;
        this.config = config;

        new Thread(() -> {
            loadMetadata(config);
        }).start();
    }

    private void loadMetadata(NativeOidcConfig config) {
        if (authorizationServerTokenUrl != null && authorizationServerAuthUrl != null) {
            return;
        }
        try {
            HttpUrl url = HttpUrl.get(config.getAuthorizationServerUrl()).newBuilder()
                    .addPathSegment(".well-known").addPathSegment("openid-configuration").build();
            OkHttpClient client = new OkHttpClient.Builder().build();
            Request request = new Request.Builder().url(url).get().build();
            ResponseBody body = client.newCall(request).execute().body();
            String response = body.string();
            JSONObject jsonObject = new JSONObject(response);
            this.authorizationServerAuthUrl = jsonObject.getString("authorization_endpoint");
            this.authorizationServerTokenUrl = jsonObject.getString("token_endpoint");
        } catch (Throwable e) {
            Log.e(TAG, "Could not load metadata", e);
        }
    }

    @WorkerThread
    public void executeAuthorizationRequest() throws Exception {
        String state = base64Url(getRandomBytes(16));
        delegate.storePreference(PREF_KEY_STATE, state);
        String nonce = base64Url(getRandomBytes(16));
        delegate.storePreference(PREF_KEY_NONCE, nonce);
        HttpUrl.Builder builder = HttpUrl.get(authorizationServerAuthUrl).newBuilder()
                .addQueryParameter(PARAM_RESPONSE_TYPE, PARAM_CODE)
                .addQueryParameter(PARAM_CLIENT_ID, config.getClientId())
                .addQueryParameter(PARAM_SCOPE, SCOPE_OPENID)
                .addQueryParameter(PARAM_STATE, state)
                .addQueryParameter(PARAM_NONCE, nonce)
                .addQueryParameter(PARAM_REDIRECT_URI, buildRedirectUri());
        if (enablePkce) {
            byte[] verifier = getRandomBytes(32);
            String encodedVerifier = base64Url(verifier);
            delegate.storePreference(PREF_KEY_VERIFIER, encodedVerifier);
            byte[] challenge = sha256(encodedVerifier.getBytes(StandardCharsets.UTF_8));
            String encodedChallenge = base64Url(challenge);
            builder.addQueryParameter(PARAM_CODE_CHALLENGE_METHOD, CHALLENGE_METHOD_S256)
                    .addQueryParameter(PARAM_CODE_CHALLENGE, encodedChallenge);
        }
        HttpUrl url = builder.build();
        Log.d(TAG, "Starting view intent for " + url.toString());
        delegate.startViewIntentAndFinish(url.toString());
    }

    @WorkerThread
    public void handleAuthenticationResponse(@NonNull Uri uri) throws Exception {
        Log.d(TAG, "Intent: " + uri);
        String state = uri.getQueryParameter(PARAM_STATE);
        String authorizationCode = uri.getQueryParameter(PARAM_CODE);
        verifyAuthenticationResponse(state, authorizationCode);
    }

    private void verifyAuthenticationResponse(String state, String authorizationCode) throws Exception {
        String storedState = delegate.loadPreference(PREF_KEY_STATE);
        if (!Objects.equals(state, storedState)) {
            throw new IllegalArgumentException("state");
        }
        requestOpenIdToken(authorizationCode);
    }

    @WorkerThread
    private void requestOpenIdToken(String authorizationCode) throws Exception {
        loadMetadata(config);
        FormBody.Builder builder = new FormBody.Builder()
                .add(PARAM_GRANT_TYPE, GRANT_TYPE_AUTHORIZATION_CODE)
                .add(PARAM_CODE, authorizationCode)
                .add(PARAM_REDIRECT_URI, buildRedirectUri())
                .add(PARAM_CLIENT_ID, config.getClientId());
        if (enablePkce) {
            String encodedVerifier = delegate.loadPreference(PREF_KEY_VERIFIER);
            builder.add(PARAM_CODE_VERIFIER, encodedVerifier);
        }
        FormBody body = builder.build();
        Log.d(TAG, "Request POST to " + authorizationServerTokenUrl + " with " + bodyToString(body));
        OkHttpClient client = new OkHttpClient.Builder().followRedirects(false).build();
        Request request = new Request.Builder().url(authorizationServerTokenUrl).post(body).build();
        ResponseBody newCallBody = client.newCall(request).execute().body();
        if (newCallBody == null) {
            throw new IOException("Body null!");
        }
        String response = newCallBody.string();
        JSONObject jsonObject = new JSONObject(response);
        Log.d(TAG, "Response: " + jsonObject);
        if (!jsonObject.has(PARAM_ID_TOKEN)) {
            throw new IllegalArgumentException(PARAM_ID_TOKEN);
        }
        String idToken = jsonObject.getString(PARAM_ID_TOKEN);
        // todo verify token_type etc
        // Use of "access_token" is left out for demonstration purposes
        String accessToken = jsonObject.getString(PARAM_ACCESS_TOKEN);
        validateOpenIdToken(idToken);
        useOpenIdToken(idToken);
    }

    private String buildRedirectUri() {
        if (useHttpsInsteadOfNativeUrl) {
            return config.getWebRedirectUrl();
        } else {
            return config.getNativeRedirectUrl();
        }
    }

    @WorkerThread
    private void validateOpenIdToken(String input) throws Exception {
        SignedJWT token = SignedJWT.parse(input);
        String tokenNonce = token.getJWTClaimsSet().getStringClaim(PARAM_NONCE);
        String storedNonce = delegate.loadPreference(PREF_KEY_NONCE);
        if (!Objects.equals(tokenNonce, storedNonce)) {
            Log.e(TAG, "Nonces do not match");
            throw new IllegalArgumentException("nonce");
        }
        // todo more checks
    }

    @WorkerThread
    private void useOpenIdToken(String idToken) throws Exception {
        OkHttpClient client = new OkHttpClient.Builder().build();
        Request request = new Request.Builder().url(config.getNativeRelyingPartyUrl())
                .header(HEADER_AUTHORIZATION, HEADER_AUTHORIZATION_BEARER + " " + idToken)
                .build();
        Response response = client.newCall(request).execute();

        ResponseBody body = response.body();
        String text = body != null ? body.string() : "<null>";
        delegate.success(text);
    }

    private String base64Url(byte[] challenge) {
        return Base64.encodeToString(challenge, Base64.NO_WRAP | Base64.URL_SAFE | Base64.NO_PADDING);
    }

    private byte[] sha256(byte[] randomSecret) throws Exception {
        return MessageDigest.getInstance("SHA256").digest(randomSecret);
    }

    private byte[] getRandomBytes(int len) {
        byte[] random = new byte[len];
        new SecureRandom().nextBytes(random);
        return random;
    }

    private static String bodyToString(final RequestBody request) {
        try {
            @SuppressWarnings("UnnecessaryLocalVariable")
            final RequestBody copy = request;
            final Buffer buffer = new Buffer();
            copy.writeTo(buffer);
            return buffer.readUtf8();
        } catch (final IOException e) {
            return "did not work";
        }
    }

    public NativeOidcConfig getConfig() {
        return config;
    }
}
