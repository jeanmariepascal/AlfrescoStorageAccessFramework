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
package fr.opensourceecm.android.alfresco.account;

import org.alfresco.mobile.android.api.session.authentication.OAuthData;
import org.alfresco.mobile.android.api.session.authentication.impl.OAuth2DataImpl;
import org.alfresco.mobile.android.api.session.authentication.impl.OAuthHelper;

import android.accounts.AbstractAccountAuthenticator;
import android.accounts.Account;
import android.accounts.AccountAuthenticatorResponse;
import android.accounts.AccountManager;
import android.accounts.NetworkErrorException;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import fr.opensourceecm.android.alfresco.account.ui.AccountCreationActivity;

public class AccountAuthenticator extends AbstractAccountAuthenticator
{
    private static final String TAG = AccountAuthenticator.class.getSimpleName();
    
    /////////////////////////////////////////////////////////////////////////////
    // Replace the following values by your Alfresco Cloud API Keys
    // More information at : https://www.alfresco.com/develop
    // Register at : http://www.alfresco.com/develop/cloud/signup
    /////////////////////////////////////////////////////////////////////////////
    private static final String OAUTH_API_KEY = "YOUR API KEY";

    private static final String OAUTH_API_SECRET = "YOUR API SECRET";

    public static final String OAUTH_CALLBACK = "YOUR CALLBACK URL";
    
    /////////////////////////////////////////////////////////////////////////////
    // CONSTANTS
    /////////////////////////////////////////////////////////////////////////////
    public static final String PARAM_APIKEY = "apiKey";

    public static final String PARAM_APISECRET = "apiSecret";

    public static final String PARAM_TOKEN = "token";
    
    public static final String PARAM_REFRESH_TOKEN = "refresh";
    
    public static final String OAUTH_SCOPE = "pub_api";

    public static final String OAUTH_CODE = "code";

    private Context context;

    /////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    /////////////////////////////////////////////////////////////////////////////
    public AccountAuthenticator(final Context context)
    {
        super(context);

        this.context = context;
    }

    /////////////////////////////////////////////////////////////////////////////
    // CONSTRUCTORS
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public Bundle addAccount(AccountAuthenticatorResponse response, String accountType, String authTokenType,
            String[] requiredFeatures, Bundle options) throws NetworkErrorException
    {
        final Intent intent = new Intent(context, AccountCreationActivity.class);
        intent.putExtra(AccountConstants.ACCOUNT_TYPE, accountType);
        intent.putExtra(AccountManager.KEY_ACCOUNT_AUTHENTICATOR_RESPONSE, response);
        final Bundle bundle = new Bundle();
        bundle.putParcelable(AccountManager.KEY_INTENT, intent);
        return bundle;
    }

    
    /////////////////////////////////////////////////////////////////////////////
    // PUBLIC METHODS
    /////////////////////////////////////////////////////////////////////////////
    @Override
    public Bundle confirmCredentials(AccountAuthenticatorResponse response, Account account, Bundle options)
            throws NetworkErrorException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle editProperties(AccountAuthenticatorResponse response, String accountType)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle getAuthToken(AccountAuthenticatorResponse response, Account account, String authTokenType,
            Bundle options) throws NetworkErrorException
    {
        final AccountManager am = AccountManager.get(context);
        String authToken = am.peekAuthToken(account, authTokenType);
        String refresh_token = null;
        Log.d(TAG, "authToken cacke : " + authToken);
        OAuth2DataImpl data = null;

        // Lets give another try to authenticate the user
        if (TextUtils.isEmpty(authToken))
        {
            OAuthHelper helper = new OAuthHelper();
            String token = am.getUserData(account, AccountConstants.OAUTH_ACCESS_TOKEN);
            refresh_token = am.getUserData(account, AccountConstants.OAUTH_REFRESH_TOKEN);
            Log.d(TAG, "OAuth Token : " + token + " - " + refresh_token);
            authToken = token;
            am.setAuthToken(account, OAUTH_SCOPE, authToken);
            data = new OAuth2DataImpl(OAUTH_API_KEY, OAUTH_API_SECRET, token, refresh_token);

            /*data = helper.refreshToken(new OAuth2DataImpl(OAUTH_API_KEY, OAUTH_API_SECRET, token, refresh_token));
            authToken = data.getAccessToken();
            am.setAuthToken(account, AccountConstants.OAUTH_SCOPE, data.getAccessToken());
            am.setUserData(account, AccountConstants.OAUTH_ACCESS_TOKEN, data.getAccessToken());
            am.setUserData(account, AccountConstants.OAUTH_REFRESH_TOKEN, data.getRefreshToken());*/
        }

        // If we get an authToken - we return it
        if (!TextUtils.isEmpty(authToken))
        {
            final Bundle result = new Bundle();
            result.putAll(options);
            result.putString(AccountManager.KEY_ACCOUNT_NAME, account.name);
            result.putString(AccountManager.KEY_ACCOUNT_TYPE, account.type);
            result.putString(PARAM_APIKEY, OAUTH_API_KEY);
            result.putString(PARAM_APISECRET, OAUTH_API_SECRET);
            result.putString(PARAM_REFRESH_TOKEN,refresh_token);
            result.putString(PARAM_TOKEN, authToken);
            return result;
        }

        return null;
    }

    @Override
    public String getAuthTokenLabel(String authTokenType)
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle hasFeatures(AccountAuthenticatorResponse response, Account account, String[] features)
            throws NetworkErrorException
    {
        // TODO Auto-generated method stub
        return null;
    }

    @Override
    public Bundle updateCredentials(AccountAuthenticatorResponse response, Account account, String authTokenType,
            Bundle options) throws NetworkErrorException
    {
        // TODO Auto-generated method stub
        return null;
    }

    /////////////////////////////////////////////////////////////////////////////
    // STATIC METHODS
    /////////////////////////////////////////////////////////////////////////////
    public static final OAuthData getOAuthAPIData()
    {
        return new OAuth2DataImpl(OAUTH_API_KEY, OAUTH_API_SECRET);
    }
}
