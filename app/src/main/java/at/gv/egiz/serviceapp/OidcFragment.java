package at.gv.egiz.serviceapp;

import android.content.Intent;
import android.content.pm.PackageManager;
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
import androidx.browser.customtabs.CustomTabsIntent;
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
        if (dataString == null) {
            showLog("Error: data string of intent null");
            return;
        }
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

    /**
     * Sets up the web view to be sure to detect redirects to the ID Austria system,
     * so that the URL can be opened in the custom browser tab or the E-ID App (if installed).
     */
    private WebView setupWebView() {
        WebView webView = requireView().findViewById(R.id.wvOpenId);
        WebViewClient client = new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                if (request != null && request.getUrl() != null) {
                    Uri url = request.getUrl();
                    if (url.toString().startsWith(config.getAuthorizationServerUrl())) {
                        Log.d(TAG, "Starting view intent for " + url);
                        new Thread(() -> callEidAppOrInAppBrowser(url)).start();
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

    /**
     * Starts the E-ID App (if it is installed), or launches the login in
     * a custom tab (see <a href="https://developer.chrome.com/docs/android/custom-tabs">Overview of Android Custom Tabs</a>.
     * <p>
     * In both cases, this app will get called back with the redirect back to the service provider,
     * containing URL query parameters for the OIDC auth code and state
     */
    private void callEidAppOrInAppBrowser(Uri url) {
        boolean isEidAppInstalled = checkInstalledApp(url.toString());
        if (isEidAppInstalled) {
            startActivity(new Intent(Intent.ACTION_VIEW, url));
        } else {
            CustomTabsIntent intent = new CustomTabsIntent.Builder()
                    .build();
            intent.launchUrl(requireContext(), url);
        }
    }

    /**
     * Checks if the E-ID app is installed, depending on the login URL of our backend.
     * <p>
     * Note that this code is customized to the SP running at <a href="https://eid.a-sit.at/notes">eid.a-sit.at/notes</a>
     * and may need to be adapted to only check for "at.gv.oe.app"
     * <p>
     * Be sure to declare these package names in your AndroidManifest.xml under {@code <queries>},
     * see <a href="https://developer.android.com/training/package-visibility/declaring#package-name">Android Docs</a>.
     */
    private boolean checkInstalledApp(String url) {
        if (url.endsWith("eidp")) {
            return isPackageInstalled("at.gv.oe.app");
        } else if (url.endsWith("eidq")) {
            return isPackageInstalled("at.gv.oe.app.q")
                    || isPackageInstalled("at.gv.oe.app")
                    || isPackageInstalled("at.asitplus.eidappandroid");
        } else if (url.endsWith("eidt")) {
            return isPackageInstalled("at.gv.oe.app.t")
                    || isPackageInstalled("at.asitplus.eidappandroid");
        } else {
            return isPackageInstalled("at.asitplus.eidappandroid");
        }
    }

    private boolean isPackageInstalled(String packageName) {
        try {
            requireActivity().getPackageManager().getPackageInfo(packageName, 0);
            return true;
        } catch (PackageManager.NameNotFoundException e) {
            return false;
        }
    }

}
