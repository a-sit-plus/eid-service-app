package at.gv.egiz.serviceapp;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ProgressBar;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import java.util.Objects;

public class OidcNativeFragment extends Fragment implements NativeOidcClient.Delegate {

    private static final String TAG = "OidcNativeFragment";

    private static final String PREF_NAME = "auth";

    private static NativeOidcClient oidcClient;

    public static OidcNativeFragment newInstance() {
        return new OidcNativeFragment();
    }

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        oidcClient = new NativeOidcClient(this, false, false, new NativeOidcConfig());

        view.findViewById(R.id.btOpenIdStartNative).setOnClickListener(v -> {
            startProgress();
            new Thread(() -> {
                try {
                    oidcClient.executeAuthorizationRequest();
                } catch (Exception e) {
                    Log.e(TAG, "Error on executeAuthorizationRequest", e);
                    showLog("Error: " + e.getMessage());
                }
            }).start();
        });

        view.findViewById(R.id.btOpenIdNativeSetUrl).setOnClickListener(
                v -> ((TextView) view.findViewById(R.id.edOpenIdNativeSpUrl)).setText(
                        oidcClient.getConfig().getWebRelyingPartyUrl()));

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
        return inflater.inflate(R.layout.fragment_oidcnative, container, false);
    }

    private void handleIntent(@NonNull Intent intent) {
        Log.d(TAG, "Intent: " + intent.getData());
        if (intent.getData() == null ||
                !Objects.equals(intent.getScheme(), oidcClient.getConfig().getNativeUrlScheme())) {
            return;
        }
        SectionsPagerAdapter.changeTab(requireActivity(), 1);
        String dataString = intent.getDataString();
        showLog("Got intent: " + (dataString.length() > 128 ? dataString.substring(0, 128) + "..." : dataString));

        new Thread(() -> {
            try {
                oidcClient.handleAuthenticationResponse(intent.getData());
            } catch (Exception e) {
                Log.e(TAG, "Error on handleAuthenticationResponse", e);
                showLog("Error: " + e.getMessage());
            }
        }).start();
    }

    private void showLog(String text) {
        requireActivity().runOnUiThread(
                () -> ((TextView) requireActivity().findViewById(R.id.tvOpenIdNativeLog)).setText(text));
    }

    private void startProgress() {
        ProgressBar progressBar = requireView().findViewById(R.id.progressOpenIdNative);
        progressBar.bringToFront();
        progressBar.setVisibility(View.VISIBLE);
    }

    private void finishProgress() {
        ProgressBar progressBar = requireView().findViewById(R.id.progressOpenIdNative);
        progressBar.setVisibility(View.GONE);
    }

    @Override
    public void startViewIntentAndFinish(String urlString) {
        requireActivity().startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse(urlString)));
        requireActivity().finish();
    }

    @Override
    public void storePreference(String key, String value) {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        authPrefs.edit().putString(key, value).apply();
    }

    @Override
    public String loadPreference(String key) {
        SharedPreferences authPrefs = requireActivity().getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
        return authPrefs.getString(key, null);
    }

    @Override
    public void success(String text) {
        finishProgress();
        OidcNativeFragment.this.showLog("Native OpenId success: " + text);
    }

}
