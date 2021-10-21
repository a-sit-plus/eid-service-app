package at.gv.egiz.serviceapp;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.CookieManager;
import android.webkit.WebChromeClient;
import android.webkit.WebResourceRequest;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.snackbar.Snackbar;

public class OidcFragment extends Fragment {

    private static final String TAG = "OidcFragment";

    private static final String PARAM_ERROR_DESCRIPTION = "error_description";

    private final OidcConfig config = new OidcConfig();

    public static OidcFragment newInstance() {
        return new OidcFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        view.findViewById(R.id.btOpenIdStartWebView).setOnClickListener(v -> {
            startProgress();
            CookieManager.getInstance().removeAllCookies(null);
            setupWebView().loadUrl(((EditText) requireView().findViewById(R.id.edOpenIdSpUrl)).getText().toString());
        });

        view.findViewById(R.id.btOpenIdStartManual).setOnClickListener(v -> {
            startProgress();
            CookieManager.getInstance().removeAllCookies(null);
            setupWebView().loadUrl(config.getWebManualLoginUrl());
            Snackbar.make(view.findViewById(R.id.container), "Click login link in WebView", Snackbar.LENGTH_LONG)
                    .setAction("OK", v1 -> { }).show();
        });

        view.findViewById(R.id.btOpenIdSetUrl)
                .setOnClickListener(v -> ((TextView) view.findViewById(R.id.edOpenIdSpUrl)).setText(
                        config.getWebRelyingPartyUrl()));

        super.onViewCreated(view, savedInstanceState);
    }

    @Override
    public void onResume() {
        Intent intent = requireActivity().getIntent();
        if (intent != null) {
            handleIntent(intent);
        }
        super.onResume();
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_oidc, container, false);
    }

    private void handleIntent(@NonNull Intent intent) {
        Log.d(TAG, "Intent: " + intent.getData());
        if (intent.getData() == null || !intent.getData().toString().startsWith(config.getWebRedirectUrl())) {
            return;
        }
        SectionsPagerAdapter.changeTab(requireActivity(), 0);
        String dataString = intent.getDataString();
        showLog("Got intent: " + (dataString.length() > 128 ? dataString.substring(0, 128) + "..." : dataString));
        String errorDescription = intent.getData().getQueryParameter(PARAM_ERROR_DESCRIPTION);
        if (dataString.equals(config.getWebRelyingPartyUrl())) {
            // called from E-ID App with an "SSO-Link"
            finishProgress();
            View buttonLogin = requireView().findViewById(R.id.btOpenIdFromSsoLogIn);
            buttonLogin.setVisibility(View.VISIBLE);
            buttonLogin.setOnClickListener(v -> loadUrlInWebView(config.getWebRelyingPartyUrl()));
        } else if (dataString.startsWith(config.getWebRedirectUrl())) {
            finishProgress();
            loadUrlInWebView(dataString);
        } else if (errorDescription != null) {
            showLog("Error: " + errorDescription);
        }
    }

    private void loadUrlInWebView(String dataString) {
        requireActivity().runOnUiThread(() -> {
            WebView webView = setupWebView();
            Log.d(TAG, "Intent: Loading URL " + dataString);
            webView.loadUrl(dataString);
        });
    }

    private void showLog(String text) {
        requireActivity().runOnUiThread(
                () -> ((TextView) requireActivity().findViewById(R.id.tvOpenIdLog)).setText(text));
    }

    private WebView setupWebView() {
        WebView webView = requireView().findViewById(R.id.wvOpenId);
        // Be sure to open links to the IdP in external browser (or E-ID App if installed)
        WebViewClient client = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    Uri url = request.getUrl();
                    if (url.toString().startsWith(config.getAuthorizationServerUrl())) {
                        Log.d(TAG, "Starting view intent for " + url.toString());
                        new Thread(() -> {
                            startActivity(new Intent(Intent.ACTION_VIEW, url));
                            requireActivity().finish();
                        }).start();
                        return true;
                    }
                }
                return false;
            }
        };
        webView.setWebViewClient(client);
        webView.setWebChromeClient(new WebChromeClient());
        webView.getSettings().setSupportZoom(true);
        webView.getSettings().setBuiltInZoomControls(true);
        webView.getSettings().setDisplayZoomControls(false);
        return webView;
    }

    private void startProgress() {
        ProgressBar progressBar = requireView().findViewById(R.id.progressOpenId);
        progressBar.bringToFront();
        progressBar.setVisibility(View.VISIBLE);
        WebView webView = requireView().findViewById(R.id.wvOpenId);
        webView.loadUrl("about:blank");
    }

    private void finishProgress() {
        ProgressBar progressBar = requireView().findViewById(R.id.progressOpenId);
        progressBar.setVisibility(View.GONE);
    }

}
