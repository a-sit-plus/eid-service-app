# Demo E-ID Service App

This app demonstrates OpenId Connect authentication using the E-ID demo app from <https://eid.a-sit.at>. This app will open an intent carrying an HTTPS URL, which will be picked up by the E-ID app, if installed, or in the browser otherwise.

Use case for a web client: App contains a WebView, back-end service handles OpenId Connect login on their own.

## Web client

Example running with the relying party at `https://eid.a-sit.at/notes` and the authorization server at `eid.egiz.gv.at` (button `WebView` in app).

The WebView navigates to the main page of the back-end service, e.g. `https://eid.a-sit.at/notes`. The service creates a redirect to the authorization server, which the app detects (in its `WebViewClient` implementation).

This app opens an intent for `https://eid.egiz.gv.at/idp/profile/oidc/authorize?response_type=code&client_id=https://eid.a-sit.at/notes&scope=openid&state=...&redirect_uri=https://eid.a-sit.at/notes/login/oauth2/code/eid`
 - The URL points to the OpenId Provider
 - Parameters `response_type`, `client_id`, `scope`, `state` are needed for a typical OpenId Connect flow
 - Parameter `redirect_uri` is also needed, and points to an URL registered for this app, i.e. an [Android App Link](https://developer.android.com/training/app-links) stated in the [AndroidManifest](./app/src/main/AndroidManifest.xml)
 
In case of a successful authentication, the E-ID app opens an intent for `https://eid.a-sit.at/notes/login/oauth2/code/eid?code=...&state=...`
 - The URL points to the `redirect_uri` from before
 - Parameters `code` and `state` are the default OpenId Connect parameters

Then the service app can use the URL to load it in the WebView. The back-end service will get an access token on the back-end channel an log the user in. 

## Libraries

This app uses the following libraries:
 - Material Components for Android, [Github](https://github.com/material-components/material-components-android), licensed under the [Apache License 2.0](https://github.com/material-components/material-components-android/blob/master/LICENSE)
 - ConstraintLayout, [Github](https://github.com/androidx/constraintlayout), licensed under the [Apache License 2.0](https://github.com/androidx/constraintlayout/blob/main/LICENSE)
 - Appcompat, [Android](https://developer.android.com/jetpack/androidx/releases/appcompat), licensed under the Apache License 2.0
