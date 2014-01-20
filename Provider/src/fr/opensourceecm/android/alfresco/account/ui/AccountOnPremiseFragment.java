/*******************************************************************************
 * Copyright Jean Marie Pascal
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

import java.net.MalformedURLException;
import java.net.URL;

import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.api.session.RepositorySession;

import android.animation.Animator;
import android.animation.AnimatorListenerAdapter;
import android.annotation.TargetApi;
import android.app.Fragment;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.TextView;
import fr.opensourceecm.android.alfresco.R;

public class AccountOnPremiseFragment extends Fragment
{

    public static final String TAG = AccountOnPremiseFragment.class.getSimpleName();

    /**
     * Keep track of the login task to ensure we can cancel it if requested.
     */
    private UserLoginTask mAuthTask = null;

    // Values for email and password at the time of the login attempt.
    private String mUrl;

    private String mEmail;

    private String mPassword;

    // UI references.
    private EditText mUrlView;

    private EditText mEmailView;

    private EditText mPasswordView;

    private View mLoginFormView;

    private View mLoginStatusView;

    private TextView mLoginStatusMessageView;

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState)
    {
        View v = inflater.inflate(R.layout.activity_login, container, false);

        // Set up the login form.
        mEmailView = (EditText) v.findViewById(R.id.email);

        mUrlView = (EditText) v.findViewById(R.id.url);

        mPasswordView = (EditText) v.findViewById(R.id.password);
        mPasswordView.setOnEditorActionListener(new TextView.OnEditorActionListener()
        {
            @Override
            public boolean onEditorAction(TextView textView, int id, KeyEvent keyEvent)
            {
                if (id == R.id.login || id == EditorInfo.IME_NULL)
                {
                    attemptLogin();
                    return true;
                }
                return false;
            }
        });

        mLoginFormView = v.findViewById(R.id.login_form);
        mLoginStatusView = v.findViewById(R.id.waiting);
        mLoginStatusMessageView = (TextView) v.findViewById(R.id.login_status_message);

        v.findViewById(R.id.sign_in_button).setOnClickListener(new View.OnClickListener()
        {
            @Override
            public void onClick(View view)
            {
                attemptLogin();
            }
        });

        return v;
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ACTIONS
    // ///////////////////////////////////////////////////////////////////////////
    /**
     * Attempts to sign in or register the account specified by the login form.
     * If there are form errors (invalid email, missing fields, etc.), the
     * errors are presented and no actual login attempt is made.
     */
    public void attemptLogin()
    {
        if (mAuthTask != null) { return; }

        // Reset errors.
        mEmailView.setError(null);
        mPasswordView.setError(null);
        mUrlView.setError(null);

        // Store values at the time of the login attempt.
        mEmail = mEmailView.getText().toString();
        mPassword = mPasswordView.getText().toString();
        mUrl = mUrlView.getText().toString();

        boolean cancel = false;
        View focusView = null;

        // Check for a valid url.
        if (TextUtils.isEmpty(mUrl))
        {
            mUrlView.setError(getString(R.string.error_field_required));
            focusView = mUrlView;
            cancel = true;
        }
        else
        {
            try
            {
                URL uri = new URL(mUrl);
            }
            catch (MalformedURLException e)
            {
                Log.d(TAG, Log.getStackTraceString(e));
                mUrlView.setError(getString(R.string.error_incorrect_url));
                focusView = mUrlView;
                cancel = true;
            }
        }

        // Check for a valid password.
        if (TextUtils.isEmpty(mPassword))
        {
            mPasswordView.setError(getString(R.string.error_field_required));
            focusView = mPasswordView;
            cancel = true;
        }

        // Check for a valid email address.
        if (TextUtils.isEmpty(mEmail))
        {
            mEmailView.setError(getString(R.string.error_field_required));
            focusView = mEmailView;
            cancel = true;
        }

        if (cancel)
        {
            // There was an error; don't attempt login and focus the first
            // form field with an error.
            focusView.requestFocus();
        }
        else
        {
            // Show a progress spinner, and kick off a background task to
            // perform the user login attempt.
            mLoginStatusMessageView.setText(R.string.login_progress_signing_in);
            showProgress(true);
            mAuthTask = new UserLoginTask(mUrl, mEmail, mPassword);
            mAuthTask.execute();
        }
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

            mLoginFormView.setVisibility(View.VISIBLE);
            mLoginFormView.animate().setDuration(shortAnimTime).alpha(show ? 0 : 1)
                    .setListener(new AnimatorListenerAdapter()
                    {
                        @Override
                        public void onAnimationEnd(Animator animation)
                        {
                            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
                        }
                    });
        }
        else
        {
            // The ViewPropertyAnimator APIs are not available, so simply show
            // and hide the relevant UI components.
            mLoginStatusView.setVisibility(show ? View.VISIBLE : View.GONE);
            mLoginFormView.setVisibility(show ? View.GONE : View.VISIBLE);
        }
    }

    private void finishLogin()
    {
        ((AccountCreationActivity) getActivity()).saveAccount(mUrl, mEmail, mPassword);
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
        private String mUrl;

        private String mEmail;

        private String mPassword;

        public UserLoginTask(String mUrl, String mEmail, String mPassword)
        {
            this.mEmail = mEmail;
            this.mPassword = mPassword;
            this.mUrl = mUrl;
        }

        @Override
        protected Boolean doInBackground(Void... params)
        {
            try
            {
                AlfrescoSession session = RepositorySession.connect(mUrl, mEmail, mPassword);
                return (session != null);
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
                mPasswordView.setError(getString(R.string.error_incorrect_password));
                mPasswordView.requestFocus();
            }
        }

        @Override
        protected void onCancelled()
        {
            mAuthTask = null;
            showProgress(false);
        }
    }

}
