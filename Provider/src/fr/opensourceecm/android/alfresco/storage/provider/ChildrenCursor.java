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
package fr.opensourceecm.android.alfresco.storage.provider;

import android.database.MatrixCursor;
import android.os.Bundle;
import android.provider.DocumentsContract;

/**
 * Inspired by @flavienlaurent on https://gist.github.com/flavienlaurent/7337127
 */
public class ChildrenCursor extends MatrixCursor
{
    private Bundle extras;

    public ChildrenCursor(String[] columnNames, int initialCapacity)
    {
        super(columnNames, initialCapacity);
    }

    public ChildrenCursor(String[] columnNames)
    {
        super(columnNames);
    }

    public Bundle getExtras()
    {
        if (extras == null) { return super.getExtras(); }
        return extras;
    }

    public void setErrorInformation(String errorMessage)
    {
        if (extras == null)
        {
            this.extras = new Bundle();
            extras.putString(DocumentsContract.EXTRA_ERROR, errorMessage);
        }
    }

    public void setIsLoading(boolean isLoading)
    {
        if (extras == null)
        {
            extras = new Bundle();
            extras.putBoolean(DocumentsContract.EXTRA_LOADING, isLoading);
        }
    }
}
