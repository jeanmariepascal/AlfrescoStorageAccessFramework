/*******************************************************************************
 * Copyright (C) 2005-2014 Alfresco Software Limited.
 * 
 * This file is part of the Alfresco Mobile SDK.
 * 
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *  
 *  http://www.apache.org/licenses/LICENSE-2.0
 * 
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 ******************************************************************************/
package fr.opensourceecm.android.alfresco.account.ui;

import org.alfresco.mobile.android.api.constants.OAuthConstant;
import org.alfresco.mobile.android.api.exceptions.AlfrescoSessionException;
import org.alfresco.mobile.android.api.exceptions.ErrorCodeRegistry;
import org.alfresco.mobile.android.api.model.Person;
import org.alfresco.mobile.android.api.session.CloudSession;
import org.alfresco.mobile.android.api.session.authentication.OAuthData;
import org.alfresco.mobile.android.api.session.authentication.impl.OAuthHelper;
import org.alfresco.mobile.android.api.utils.messages.Messagesl18n;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Activity;
import android.app.DialogFragment;
import android.app.LoaderManager;
import android.app.LoaderManager.LoaderCallbacks;
import android.content.Loader;
import android.graphics.Bitmap;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.webkit.WebChromeClient;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.Toast;
import fr.opensourceecm.android.alfresco.R;
import fr.opensourceecm.android.alfresco.account.AccountAuthenticator;
import fr.opensourceecm.android.alfresco.account.cloud.OAuthAccessTokenLoader;
import fr.opensourceecm.android.alfresco.utils.LoaderResult;

public class AccountOAuthFragment extends DialogFragment implements LoaderCallbacks<LoaderResult<OAuthData>>
{
    public static final String TAG = AccountOAuthFragment.class.getSimpleName();

    public static final String LAYOUT_ID = "OAuthLayoutId";

    private String apiKey;

    private String apiSecret;

    private String callback;

    private String scope;

    private String code;

    private int layout_id = R.layout.wizard_cloud;

    private String baseOAuthUrl = OAuthConstant.PUBLIC_API_HOSTNAME;

    private OnOAuthAccessTokenListener onOAuthAccessTokenListener;

    private OnOAuthWebViewListener onOAuthWebViewListener;

    private boolean isLoaded;

    public CloudSession cloudSession;

    private UserLoginTask mAuthTask = null;

    private WebView webview;

    private View mLoginStatusView;

    public Person userPerson;

    // ///////////////////////////////////////////////////////////////////////////
    // CONSTRUCTOR
    // ///////////////////////////////////////////////////////////////////////////
    public AccountOAuthFragment()
    {
    }

    public static AccountOAuthFragment newInstance()
    {
        AccountOAuthFragment oAuthFragment = new AccountOAuthFragment();
        return oAuthFragment;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        if (container == null) { return null; }

        if (getArguments() != null && getArguments().containsKey(LAYOUT_ID))
        {
            layout_id = getArguments().getInt(LAYOUT_ID);
        }

        View v = inflater.inflate(layout_id, container, false);

        OAuthData data = AccountAuthenticator.getOAuthAPIData();

        this.apiKey = data.getApiKey();
        this.apiSecret = data.getApiSecret();
        this.callback = AccountAuthenticator.OAUTH_CALLBACK;
        this.scope = AccountAuthenticator.OAUTH_SCOPE;

        mLoginStatusView = v.findViewById(R.id.waiting);
        webview = (WebView) v.findViewById(R.id.webview);
        webview.getSettings().setJavaScriptEnabled(true);

        final Activity activity = getActivity();
        webview.setWebChromeClient(new WebChromeClient()
        {
            public void onProgressChanged(WebView view, int progress)
            {
                // Activities and WebViews measure progress with different
                // scales.The progress meter will automatically disappear when
                // we reach 100%
                activity.setProgress(progress * 100);
            }
        });

        // attach WebViewClient to intercept the callback url
        webview.setWebViewClient(new WebViewClient()
        {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url)
            {
                // check for our custom callback protocol
                if (!isLoaded)
                {
                    onCodeUrl(url);
                    return true;
                }
                return super.shouldOverrideUrlLoading(view, url);
            }

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                super.onPageStarted(view, url, favicon);
                if (!isLoaded)
                {
                    onCodeUrl(url);
                }
                if (onOAuthWebViewListener != null)
                {
                    onOAuthWebViewListener.onPageStarted(webview, url, favicon);
                }
            }

            @Override
            public void onPageFinished(WebView view, String url)
            {
                super.onPageFinished(view, url);
                if (onOAuthWebViewListener != null)
                {
                    onOAuthWebViewListener.onPageFinished(webview, url);
                }
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                super.onReceivedError(view, errorCode, description, failingUrl);
                if (onOAuthWebViewListener != null)
                {
                    onOAuthWebViewListener.onReceivedError(webview, errorCode, description, failingUrl);
                }
            }
        });

        onOAuthAccessTokenListener = new OnOAuthAccessTokenListener()
        {

            @Override
            public void failedRequestAccessToken(Exception e)
            {
                getActivity().getFragmentManager().popBackStack();
                Log.e(TAG, Log.getStackTraceString(e));
                Toast.makeText(getActivity(), e.getMessage(), Toast.LENGTH_SHORT).show();
            }

            @Override
            public void beforeRequestAccessToken(Bundle b)
            {
                showProgress(true);
            }

            @Override
            public void afterRequestAccessToken(OAuthData result)
            {
                load(result);
            }
        };

        onOAuthWebViewListener = new OnOAuthWebViewListener()
        {

            @Override
            public void onPageStarted(WebView view, String url, Bitmap favicon)
            {
                showProgress(false);
            }

            @Override
            public void onPageFinished(WebView view, String url)
            {
                // showProgress(false);
            }

            @Override
            public void onReceivedError(WebView view, int errorCode, String description, String failingUrl)
            {
                // showProgress(false);
            }
        };

        OAuthHelper helper = new OAuthHelper(baseOAuthUrl);
        // Log.d("OAUTH URL", helper.getAuthorizationUrl(apiKey, callback,
        // scope));
        // send user to authorization page
        webview.loadUrl(helper.getAuthorizationUrl(apiKey, callback, scope));

        return v;
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState)
    {
        super.onActivityCreated(savedInstanceState);
        setRetainInstance(true);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ACTIONS
    // ///////////////////////////////////////////////////////////////////////////
    public void load(OAuthData oauthData)
    {
        if (oauthData == null)
        {
            getActivity().getFragmentManager().popBackStack();
            Toast.makeText(getActivity(), "Unable to retrieve token", Toast.LENGTH_SHORT).show();
            return;
        }
        else
        {
            mAuthTask = new UserLoginTask(oauthData);
            mAuthTask.execute();
        }
    }

    private void onCodeUrl(String url)
    {
        // check for our custom callback protocol
        if (url.startsWith(AccountAuthenticator.OAUTH_CALLBACK))
        {
            isLoaded = true;

            // authorization complete hide webview for now & retrieve
            // the acces token
            code = OAuthHelper.retrieveCode(url);
            if (code != null)
            {
                retrieveAccessToken(code);
            }
            else
            {
                if (onOAuthAccessTokenListener != null)
                {
                    onOAuthAccessTokenListener.failedRequestAccessToken(new AlfrescoSessionException(
                            ErrorCodeRegistry.SESSION_AUTH_CODE_INVALID, Messagesl18n
                                    .getString("ErrorCodeRegistry.SESSION_AUTH_CODE_INVALID")));
                }
            }
        }
    }

    public void retrieveAccessToken(String code)
    {
        LoaderManager lm = getLoaderManager();
        Bundle b = new Bundle();
        b.putString(OAuthAccessTokenLoader.PARAM_CODE, "code");
        b.putString(OAuthAccessTokenLoader.PARAM_APIKEY, apiKey);
        b.putString(OAuthAccessTokenLoader.PARAM_APISECRET, apiSecret);
        b.putString(OAuthAccessTokenLoader.PARAM_CALLBACK_URL, callback);
        b.putString(OAuthAccessTokenLoader.PARAM_BASEURL, baseOAuthUrl);
        b.putInt(OAuthAccessTokenLoader.PARAM_OPERATION, OAuthAccessTokenLoader.OPERATION_ACCESS_TOKEN);
        b.putString(OAuthAccessTokenLoader.PARAM_CODE, code);
        lm.restartLoader(OAuthAccessTokenLoader.ID, b, this);
    }

    // ///////////////////////////////////////////////////////////////////////////
    // LOADER
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public Loader<LoaderResult<OAuthData>> onCreateLoader(final int id, Bundle bundle)
    {
        if (onOAuthAccessTokenListener != null)
        {
            onOAuthAccessTokenListener.beforeRequestAccessToken(bundle);
        }
        return new OAuthAccessTokenLoader(getActivity(), bundle);
    }

    @Override
    public void onLoadFinished(Loader<LoaderResult<OAuthData>> arg0, LoaderResult<OAuthData> result)
    {

        if (onOAuthAccessTokenListener != null)
        {
            if (result.hasException() || result.getData() == null)
            {
                onOAuthAccessTokenListener.failedRequestAccessToken(result.getException());
            }
            else
            {
                onOAuthAccessTokenListener.afterRequestAccessToken(result.getData());
            }
        }
        else
        {
            if (result.hasException())
            {
                Toast.makeText(getActivity(), result.getException().getMessage(), Toast.LENGTH_SHORT).show();
            }
            else
            {
                Toast.makeText(getActivity(), result.getData().toString(), Toast.LENGTH_SHORT).show();
            }
        }
    }

    @Override
    public void onLoaderReset(
            Loader<LoaderResult<org.alfresco.mobile.android.api.session.authentication.OAuthData>> arg0)
    {

    }

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    public interface OnOAuthWebViewListener
    {

        void onPageStarted(WebView view, String url, Bitmap favicon);

        void onPageFinished(WebView view, String url);

        void onReceivedError(WebView view, int errorCode, String description, String failingUrl);
    }

    private void finishLogin()
    {
        ((AccountCreationActivity) getActivity()).saveAccount(cloudSession, userPerson);
    }

    /**
     * Shows the progress UI and hides the login form.
     */
    @TargetApi(Build.VERSION_CODES.HONEYCOMB_MR2)
    private void showProgress(final boolean show)
    {
        // On Honeycomb MR2 we have the ViewPropertyAnimator APIs, which allow
        // for very easy animations. If available, use these APIs to fade-in
        // the progress spinner.
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.HONEYCOMB_MR2)
        {
            int shortAnimTime = getResources().getInteger(android.R.integer.config_shortAnimTime);

            mLoginStatusView.setVisibility(View.VISIBLE);
            mLoginStatusView.animate().setDuration(shortAnimTime).alpha(show ? 1 : 0)
                    .setListener(new AnimatorListenerAdapter()
                    {
                        @Override
                        public void onAnimationEnd(Animator animation)
                        {
                            mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
                        }
                    });

            webview.setVisibility(View.VISIBLE);
            webview.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1).setListener(new AnimatorListenerAdapter()
            {
                @Override
                public void onAnimationEnd(Animator animation)
                {
                    webview.setVisibility(show ? View.GONE : View.VISIBLE);
                }
            });
        }
        else
        {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
            webview.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ASYNC TASK
    // ///////////////////////////////////////////////////////////////////////////
    /**
     * Represents an asynchronous login/registration task used to authenticate
     * the user.
     */
    public class UserLoginTask extends AsyncTask<Void, Void, Boolean>
    {
        private OAuthData oauthData;

        public UserLoginTask(OAuthData oauthData)
        {
            this.oauthData = oauthData;
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            try
            {
                cloudSession = CloudSession.connect(oauthData);
                userPerson = cloudSession.getServiceRegistry().getPersonService().getPerson(CloudSession.USER_ME);
                return (cloudSession != null);
            }
            catch (Exception e)
            {
                Log.d(TAG, Log.getStackTraceString(e));
            }
            return false;
        }

        @Override
        protected void onPostExecute(Boolean success)
        {
            Log.d(TAG, success + "");
            mAuthTask = null;
            showProgress(false);

            if (success)
            {
                finishLogin();
            }
            else
            {
                Toast.makeText(getActivity(), "Error during cloud session", Toast.LENGTH_SHORT).show();
            }
        }

        @Override
        protected void onCancelled()
        {
            mAuthTask = null;
            showProgress(false);
        }
    }
    
    public interface OnOAuthAccessTokenListener
    {
        void beforeRequestAccessToken(Bundle oauthBundle);
        
        void failedRequestAccessToken(Exception e);

        void afterRequestAccessToken(OAuthData data);
    }
}
