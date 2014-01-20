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

import org.alfresco.mobile.android.api.model.Person;
import org.alfresco.mobile.android.api.session.CloudSession;

import android.accounts.Account;
import android.accounts.AccountAuthenticatorActivity;
import android.accounts.AccountManager;
import android.app.Fragment;
import android.app.FragmentTransaction;
import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import fr.opensourceecm.android.alfresco.R;
import fr.opensourceecm.android.alfresco.account.AccountConstants;

/**
 * Displays a wizard for the first account creation.
 * 
 * @author Jean Marie Pascal
 */
public class AccountCreationActivity extends AccountAuthenticatorActivity
{
    private static final String TAG = AccountCreationActivity.class.getName();

    private AccountManager mAccountManager;

    // ///////////////////////////////////////////////////////////////////////////
    // LIFECYCLE
    // ///////////////////////////////////////////////////////////////////////////
    /** Called when the activity is first created. */
    @Override
    public void onCreate(Bundle savedInstanceState)
    {
        super.onCreate(savedInstanceState);

        mAccountManager = AccountManager.get(this);

        setContentView(R.layout.app_main);

        if (getFragmentManager().findFragmentByTag(AccountTypesFragment.TAG) == null)
        {
            AccountTypesFragment newFragment = new AccountTypesFragment();
            FragmentTransaction transaction = getFragmentManager().beginTransaction();
            transaction.replace(R.id.left_pane_body, newFragment, AccountTypesFragment.TAG);
            transaction.commit();
        }
    }

    @Override
    protected void onStart()
    {
        super.onStart();
    }

    // ///////////////////////////////////////////////////////////////////////////
    // ACTIONS
    // ///////////////////////////////////////////////////////////////////////////
    public void selectAccount(View v)
    {
        Fragment newFragment = null;
        String tag = null;
        if (v.getId() == R.id.alfresco_server)
        {
            newFragment = new AccountOnPremiseFragment();
            tag = AccountOnPremiseFragment.TAG;
        }
        else if (v.getId() == R.id.alfresco_cloud)
        {
            newFragment = new AccountOAuthFragment();
            tag = AccountOAuthFragment.TAG;
        }

        FragmentTransaction transaction = getFragmentManager().beginTransaction();
        transaction.replace(R.id.left_pane_body, newFragment, tag);
        transaction.addToBackStack(tag);
        transaction.commit();
    }

    public void saveAccount(String url, String username, String password)
    {
        mAccountManager = AccountManager.get(this);
        final Account account = new Account(username, AccountConstants.ACCOUNT_TYPE);
        Bundle b = new Bundle();
        b.putString(AccountConstants.ACCOUNT_NAME, AccountConstants.ACCOUNT_ONPREMISE);
        b.putString(AccountConstants.ACCOUNT_URL, url);
        mAccountManager.addAccountExplicitly(account, password, b);

        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, username);
        intent.putExtra(AccountConstants.ACCOUNT_URL, url);
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_alfresco_onpremise));
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }
    
    public void saveAccount(CloudSession cloudSession, Person userPerson)
    {
        mAccountManager = AccountManager.get(this);
        final Account account = new Account(userPerson.getIdentifier(), AccountConstants.ACCOUNT_TYPE);
        Bundle b = new Bundle();
        b.putString(AccountConstants.ACCOUNT_NAME, AccountConstants.ACCOUNT_CLOUD);
        b.putString(AccountConstants.OAUTH_ACCESS_TOKEN, cloudSession.getOAuthData().getAccessToken());
        b.putString(AccountConstants.OAUTH_REFRESH_TOKEN, cloudSession.getOAuthData().getRefreshToken());
        mAccountManager.addAccountExplicitly(account, null, b);
        mAccountManager.setAuthToken(account, AccountConstants.OAUTH_SCOPE , cloudSession.getOAuthData().getAccessToken());
        
        final Intent intent = new Intent();
        intent.putExtra(AccountManager.KEY_ACCOUNT_NAME, userPerson.getIdentifier());
        intent.putExtra(AccountManager.KEY_ACCOUNT_TYPE, getString(R.string.account_alfresco_cloud));
        setAccountAuthenticatorResult(intent.getExtras());
        setResult(RESULT_OK, intent);
        finish();
    }
}
