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
package fr.opensourceecm.android.alfresco.storage;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.MalformedURLException;
import java.net.URL;

import org.alfresco.mobile.android.api.utils.IOUtils;

import android.content.Context;
import android.os.CancellationSignal;
import android.util.Log;

/**
 * Utility class responsible to store documents & thumbnails inside the device.
 * 
 * @author Jean Marie Pascal
 */
public final class StorageUtils
{
    private static String TAG = StorageUtils.class.getSimpleName();

    private static final int MAX_BUFFER_SIZE = 1024;
    
    /**
     * Retrieve the file object where to store the Document or the thumbnail.
     * Each account has its own dedicated storage space inside the sdcard.
     * 
     * @return
     */
    public static File getStorageFolder(Context context, String environmentFolder, String selectedUrl, String username,
            String documentName)
    {
        File downloadFolder = context.getExternalFilesDir(environmentFolder);
        File tmpFolder = new File(downloadFolder, StorageUtils.getAccountFolder(selectedUrl, username));
        if (!tmpFolder.exists())
        {
            tmpFolder.mkdirs();
        }
        return new File(tmpFolder, documentName);
    }

    /**
     * Copy an inputStream to the dedicated file object.
     */
    public static boolean copyFile(InputStream src, long size, File dest, CancellationSignal signal)
    {
        IOUtils.ensureOrCreatePathAndFile(dest);
        OutputStream os = null;
        boolean copied = true;
        int downloaded = 0;

        try
        {
            os = new BufferedOutputStream(new FileOutputStream(dest));

            byte[] buffer = new byte[MAX_BUFFER_SIZE];

            while (size - downloaded > 0)
            {
                if (size - downloaded < MAX_BUFFER_SIZE)
                {
                    buffer = new byte[(int) (size - downloaded)];
                }

                int read = src.read(buffer);
                if (read == -1)
                {
                    break;
                }

                os.write(buffer, 0, read);
                downloaded += read;
                if (signal != null && signal.isCanceled())
                {
                    signal.throwIfCanceled();
                }
            }
        }
        catch (FileNotFoundException e)
        {
            Log.e(TAG, Log.getStackTraceString(e));
            copied = false;
        }
        catch (IOException e)
        {
            Log.e(TAG, Log.getStackTraceString(e));
            copied = false;
        }
        finally
        {
            IOUtils.closeStream(src);
            IOUtils.closeStream(os);
        }
        return copied;
    }

    /**
     * Retrieve the file Path associated to a specific account based on server url & username.
     */
    private static String getAccountFolder(String urlValue, String username)
    {
        String host = null;
        try
        {
            URL url = new URL(urlValue);
            host = url.getHost();
        }
        catch (MalformedURLException e)
        {
            Log.e(TAG, Log.getStackTraceString(e));
        }
        return host + "-" + username;
    }

}
