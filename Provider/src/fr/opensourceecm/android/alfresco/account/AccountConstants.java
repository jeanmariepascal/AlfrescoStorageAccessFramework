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

/**
 * Authentication constants
 */
public interface AccountConstants
{
    /**
     * Account type
     */
    String ACCOUNT_TYPE = "fr.opensourceecm.android.alfresco";

    /**
     * Account name
     */
    String ACCOUNT_NAME = "fr.opensourceecm.android.alfresco.account";

    /**
     * Account On Premise
     */
    int ACCOUNT_ONPREMISE_VALUE = 1;

    String ACCOUNT_ONPREMISE = String.valueOf(ACCOUNT_ONPREMISE_VALUE);

    /**
     * Account cloud
     */
    int ACCOUNT_CLOUD_VALUE = 2;

    String ACCOUNT_CLOUD = String.valueOf(ACCOUNT_CLOUD_VALUE);

    String ACCOUNT_URL = "fr.opensourceecm.android.alfresco.account.url";

    /**
     * Alfresco Cloud KEYS
     */
    String OAUTH_SCOPE = "pub_api";

    String OAUTH_ACCESS_TOKEN = "access_token";

    String OAUTH_REFRESH_TOKEN = "refresh_token";

}
