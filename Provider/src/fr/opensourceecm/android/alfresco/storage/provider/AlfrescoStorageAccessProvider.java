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

import java.io.File;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.GregorianCalendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;

import org.alfresco.mobile.android.api.exceptions.AlfrescoException;
import org.alfresco.mobile.android.api.model.ContentStream;
import org.alfresco.mobile.android.api.model.Folder;
import org.alfresco.mobile.android.api.model.KeywordSearchOptions;
import org.alfresco.mobile.android.api.model.Node;
import org.alfresco.mobile.android.api.model.Permissions;
import org.alfresco.mobile.android.api.model.SearchLanguage;
import org.alfresco.mobile.android.api.model.Site;
import org.alfresco.mobile.android.api.services.DocumentFolderService;
import org.alfresco.mobile.android.api.session.AlfrescoSession;
import org.alfresco.mobile.android.api.session.CloudSession;
import org.alfresco.mobile.android.api.session.RepositorySession;
import org.alfresco.mobile.android.api.session.authentication.OAuthData;
import org.alfresco.mobile.android.api.session.authentication.impl.OAuth2DataImpl;
import org.alfresco.mobile.android.api.utils.DateUtils;
import org.alfresco.mobile.android.api.utils.NodeRefUtils;

import android.accounts.Account;
import android.accounts.AccountManager;
import android.accounts.AccountManagerCallback;
import android.accounts.AccountManagerFuture;
import android.content.Context;
import android.content.pm.ProviderInfo;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Point;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.CancellationSignal;
import android.os.Environment;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import android.provider.DocumentsContract.Document;
import android.provider.DocumentsContract.Root;
import android.provider.DocumentsProvider;
import android.text.TextUtils;
import android.util.Log;
import fr.opensourceecm.android.alfresco.R;
import fr.opensourceecm.android.alfresco.account.AccountAuthenticator;
import fr.opensourceecm.android.alfresco.account.AccountConstants;
import fr.opensourceecm.android.alfresco.storage.StorageUtils;

public class AlfrescoStorageAccessProvider extends DocumentsProvider implements AccountManagerCallback<Bundle>
{
    // //////////////////////////////////////////////////////////////////////
    // CONSTANTS
    // //////////////////////////////////////////////////////////////////////
    private static final String TAG = AlfrescoStorageAccessProvider.class.getSimpleName();

    private static final int PREFIX_ACCOUNT = 1;

    private static final int PREFIX_SITE = 2;

    private static final String SEPARATOR = "::";

    /** CMIS Query to retrieve recent documents. */
    private static final String QUERY_RECENT = "SELECT * FROM cmis:document WHERE cmis:lastModificationDate > TIMESTAMP '%s' ORDER BY cmis:lastModificationDate DESC";

    private static final String[] DEFAULT_ROOT_PROJECTION = new String[] { Root.COLUMN_ROOT_ID, Root.COLUMN_MIME_TYPES,
            Root.COLUMN_FLAGS, Root.COLUMN_ICON, Root.COLUMN_TITLE, Root.COLUMN_SUMMARY, Root.COLUMN_DOCUMENT_ID,
            Root.COLUMN_AVAILABLE_BYTES };

    private static final String[] DEFAULT_DOCUMENT_PROJECTION = new String[] { Document.COLUMN_DOCUMENT_ID,
            Document.COLUMN_MIME_TYPE, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_LAST_MODIFIED,
            Document.COLUMN_FLAGS, Document.COLUMN_SIZE };

    @SuppressWarnings("serial")
    private static final List<String> IMPORT_FOLDER_LIST = new ArrayList<String>(4)
    {
        {
            add(String.valueOf(R.string.menu_browse_sites));
            add(String.valueOf(R.string.menu_browse_favorites_folder));
        }
    };

    protected static final String PARAM_URI = "uri";

    protected static final String PARAM_DOCUMENT_ID = "DocumentId";

    // //////////////////////////////////////////////////////////////////////
    // MEMBERS
    // //////////////////////////////////////////////////////////////////////
    private AlfrescoSession session;

    private String mAuthority;

    private final ConcurrentHashMap<Uri, Boolean> mLoadingUris = new ConcurrentHashMap<Uri, Boolean>();

    protected Map<String, Node> nodesIndex = new HashMap<String, Node>();

    protected Map<String, Node> pathIndex = new HashMap<String, Node>();

    protected Map<String, Site> siteIndex = new HashMap<String, Site>();

    protected Folder parentFolder;

    private AccountManager accountManager;

    private Map<String, Account> accountsIndex;

    private Map<String, AlfrescoSession> sessionIndex;

    private Account selectedAccount;

    private String selectedUrl;

    private Folder currentFolder;

    protected org.alfresco.mobile.android.api.model.Document createdNode;

    protected AlfrescoException exception;

    private int accountType;

    private OAuthData oauthdata;

    private ChildrenCursor tempCursor;

    // //////////////////////////////////////////////////////////////////////
    // INIT
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void attachInfo(Context context, ProviderInfo info)
    {
        mAuthority = info.authority;
        super.attachInfo(context, info);
    }

    @Override
    public boolean onCreate()
    {
        checkAccounts();
        return true;
    }

    // //////////////////////////////////////////////////////////////////////
    // PROVIDER METHODS
    // //////////////////////////////////////////////////////////////////////

    // Roots == Alfresco Accounts
    // Can be Alfresco Cloud or Alfresco On Premise
    @Override
    public Cursor queryRoots(String[] projection) throws FileNotFoundException
    {
        final ChildrenCursor rootCursor = new ChildrenCursor(resolveRootProjection(projection));
        try
        {
            for (Entry<String, Account> accountEntry : accountsIndex.entrySet())
            {
                addRootRow(rootCursor, accountEntry.getValue());
            }
        }
        catch (Exception e)
        {
            Log.d(TAG, Log.getStackTraceString(e));
        }

        return rootCursor;
    }

    @Override
    public Cursor queryChildDocuments(final String parentDocumentId, String[] projection, String sortOrder)
            throws FileNotFoundException
    {
        Log.d(TAG, "Query Children : " + parentDocumentId);

        final ChildrenCursor childrenCursor = new ChildrenCursor(resolveDocumentProjection(projection));
        final Uri uri = DocumentsContract.buildChildDocumentsUri(mAuthority, parentDocumentId);
        int prefix = -1;
        String parentDocumentIdValue = null;

        // Decode Items Value
        if (parentDocumentId != null)
        {
            int index = parentDocumentId.indexOf(SEPARATOR);
            if (index == -1 && TextUtils.isDigitsOnly(parentDocumentId))
            {
                // Case : Android Resource Id
                // This is a Menu Item
                prefix = Integer.parseInt(parentDocumentId);
                parentDocumentIdValue = null;
            }
            else if (index == -1
                    && (NodeRefUtils.isNodeRef(parentDocumentId) || NodeRefUtils.isIdentifier(parentDocumentId)))
            {
                // Case : NodeRef
                // This is a document / folder
                parentDocumentIdValue = parentDocumentId;
            }
            else
            {
                // Case : Encoding value
                // This is a a specific menu item
                String prefixString = parentDocumentId.substring(0, index);
                if (TextUtils.isDigitsOnly(prefixString))
                {
                    prefix = Integer.parseInt(prefixString);
                }
                parentDocumentIdValue = parentDocumentId.substring(index + 2);
            }
        }

        // Dispatch value
        try
        {
            Boolean active = mLoadingUris.get(uri);

            switch (prefix)
            {

                case PREFIX_ACCOUNT:
                    // First Rows after account selection
                    // Display Top level Entry Points
                    retrieveRootMenuChildren(uri, parentDocumentIdValue, childrenCursor);
                    break;

                case R.string.menu_browse_sites:
                    // List of Sites
                    if (active != null && !active)
                    {
                        fillSitesChildren(uri, active, childrenCursor);
                    }
                    else
                    {
                        retrieveSitesChildren(uri, parentDocumentIdValue, childrenCursor);
                    }
                    break;

                case R.string.menu_browse_favorites_folder:
                    // List favorite folders
                    if (active != null && !active)
                    {
                        fillNodeChildren(uri, active, childrenCursor);
                    }
                    else
                    {
                        retrieveFavoriteFoldersChildren(uri, parentDocumentIdValue, childrenCursor);
                    }
                    break;

                case PREFIX_SITE:
                    // List children for a specific site
                    // i.e Document Library Children
                    if (active != null && !active)
                    {
                        fillNodeChildren(uri, active, childrenCursor);
                    }
                    else
                    {
                        retrieveSiteDocumentLibraryChildren(uri, parentDocumentIdValue, childrenCursor);
                    }
                    break;

                default:
                    // Children browsing
                    if (parentDocumentId == null) { return childrenCursor; }

                    if (active != null && !active)
                    {
                        fillNodeChildren(uri, active, childrenCursor);
                    }
                    else
                    {
                        retrieveFolderChildren(uri, parentDocumentIdValue, childrenCursor);
                    }
                    break;

            }
        }
        catch (Exception e)
        {
            childrenCursor.setErrorInformation("Error : " + e.getMessage());
            childrenCursor.setNotificationUri(getContext().getContentResolver(), uri);
            getContext().getContentResolver().notifyChange(uri, null);
            Log.d(TAG, Log.getStackTraceString(e));
        }

        return childrenCursor;
    }

    @Override
    public Cursor queryDocument(String documentId, String[] projection) throws FileNotFoundException
    {
        Log.d(TAG, "Query Document : " + documentId);
        final ChildrenCursor result = new ChildrenCursor(resolveDocumentProjection(projection));

        if (nodesIndex.containsKey(documentId))
        {
            addNodeRow(result, nodesIndex.get(documentId));
        }
        else if (pathIndex.containsKey(documentId))
        {
            addNodeRow(result, pathIndex.get(documentId));
        }
        else if (siteIndex.containsKey(documentId))
        {
            addSiteRow(result, siteIndex.get(documentId));
        }
        else if (IMPORT_FOLDER_LIST.contains(documentId))
        {
            addRootMenuRow(result, Integer.parseInt(documentId));
        }
        else
        {
            ChildrenCursor.RowBuilder row = result.newRow();
            row.add(Document.COLUMN_DOCUMENT_ID, documentId);
            row.add(Document.COLUMN_DISPLAY_NAME, documentId);
            row.add(Document.COLUMN_SIZE, null);
            row.add(Document.COLUMN_LAST_MODIFIED, null);
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            row.add(Document.COLUMN_ICON, null);
        }

        return result;
    }

    @Override
    public ParcelFileDescriptor openDocument(String documentId, String mode, CancellationSignal signal)
            throws FileNotFoundException
    {
        Log.d(TAG, "Open Document : " + documentId);

        Node currentNode = null;

        // Retrieve node by its id
        if (nodesIndex.containsKey(documentId))
        {
            currentNode = nodesIndex.get(documentId);
        }
        else
        {
            currentNode = session.getServiceRegistry().getDocumentFolderService().getNodeByIdentifier(documentId);
        }

        // Check Document has Content
        if (currentNode.isDocument()
                && ((org.alfresco.mobile.android.api.model.Document) currentNode).getContentStreamLength() == 0) { return null; }

        // Document has content so let's get it !
        // Store the document inside a temporary folder per account
        File downloadedFile = StorageUtils.getStorageFolder(getContext(), Environment.DIRECTORY_DOWNLOADS, selectedUrl,
                selectedAccount.name, currentNode.getName());

        // Check the mode
        final int accessMode = ParcelFileDescriptor.parseMode(mode);
        final boolean isWrite = (mode.indexOf('w') != -1);

        // Is Document in cache ?
        if (downloadedFile.exists() && currentNode.getModifiedAt().getTimeInMillis() < downloadedFile.lastModified())
        {
            // Document available locally
            return createFileDescriptor(isWrite, downloadedFile, accessMode);
        }

        // Not in cache so let's download the content !
        ContentStream contentStream = session.getServiceRegistry().getDocumentFolderService()
                .getContentStream((org.alfresco.mobile.android.api.model.Document) currentNode);

        // Check Stream
        if (contentStream == null || contentStream.getLength() == 0) { return null; }

        // Copy the content locally.
        StorageUtils.copyFile(contentStream.getInputStream(), contentStream.getLength(), downloadedFile, signal);

        if (downloadedFile.exists())
        {
            // Document available locally
            return createFileDescriptor(isWrite, downloadedFile, accessMode);
        }
        else
        {
            return null;
        }
    }

    @Override
    public AssetFileDescriptor openDocumentThumbnail(String documentId, Point sizeHint, CancellationSignal signal)
            throws FileNotFoundException
    {
        Log.v(TAG, "openDocumentThumbnail");

        Node currentNode = null;

        // Retrieve node by its id
        if (nodesIndex.containsKey(documentId))
        {
            currentNode = nodesIndex.get(documentId);
        }
        else if (NodeRefUtils.isNodeRef(documentId))
        {
            // If the documentId is a NodeRef i.e a Node from Alfresco
            currentNode = session.getServiceRegistry().getDocumentFolderService().getNodeByIdentifier(documentId);
        }
        else
        {
            // It's not a NodeRef, so nothing to display.
            return null;
        }

        // Let's retrieve the thumbnail
        // Store the document inside a temporary folder per account
        File downloadedFile = StorageUtils.getStorageFolder(getContext(), Environment.DIRECTORY_PICTURES, selectedUrl,
                selectedAccount.name, currentNode.getName());

        // Is Document in cache ?
        if (downloadedFile.exists() && currentNode.getModifiedAt().getTimeInMillis() < downloadedFile.lastModified())
        {
            // Document available locally
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(downloadedFile, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        }

        // Not in cache so let's download the content !
        ContentStream contentStream = session.getServiceRegistry().getDocumentFolderService()
                .getRenditionStream(currentNode, DocumentFolderService.RENDITION_THUMBNAIL);

        // Check ContentStream
        if (contentStream == null || contentStream.getLength() == 0) { return null; }

        // Store the thumbnail locally
        StorageUtils.copyFile(contentStream.getInputStream(), contentStream.getLength(), downloadedFile, signal);

        // Return the fileDescriptor
        if (downloadedFile.exists())
        {
            ParcelFileDescriptor pfd = ParcelFileDescriptor.open(downloadedFile, ParcelFileDescriptor.MODE_READ_ONLY);
            return new AssetFileDescriptor(pfd, 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        }
        else
        {
            return null;
        }
    }

    @Override
    public Cursor querySearchDocuments(String rootId, final String query, String[] projection)
            throws FileNotFoundException
    {
        final ChildrenCursor childrenCursor = new ChildrenCursor(resolveDocumentProjection(projection));
        final Uri uri = DocumentsContract.buildSearchDocumentsUri(mAuthority, rootId, query);

        if (!hasSession(uri, childrenCursor)) { return childrenCursor; }

        Boolean active = mLoadingUris.get(uri);

        if (active != null)
        {
            for (Entry<String, Node> nodeEntry : nodesIndex.entrySet())
            {
                addNodeRow(childrenCursor, nodeEntry.getValue());
            }
            if (!active)
            {
                // loading request is finished and refreshed
                mLoadingUris.remove(uri);
            }
        }

        if (active == null)
        {
            new StorageProviderAsyncTask(uri, childrenCursor, true)
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    List<Node> nodes = new ArrayList<Node>();

                    // Use the Alfresco session and the searchService to
                    // retrieve documents based on keywords
                    nodes = session.getServiceRegistry().getSearchService()
                            .keywordSearch(query, new KeywordSearchOptions());

                    for (Node node : nodes)
                    {
                        nodesIndex.put(node.getIdentifier(), node);
                    }

                    return null;
                }
            }.execute();
        }
        return childrenCursor;
    }

    @Override
    public Cursor queryRecentDocuments(String rootId, String[] projection) throws FileNotFoundException
    {
        Log.v(TAG, "queryRecentDocuments");

        final ChildrenCursor recentDocumentsCursor = new ChildrenCursor(resolveDocumentProjection(projection));
        final Uri uri = DocumentsContract.buildRecentDocumentsUri(mAuthority, rootId);

        if (!hasSession(uri, recentDocumentsCursor)) { return recentDocumentsCursor; }

        Boolean active = mLoadingUris.get(uri);

        if (active != null)
        {
            for (Entry<String, Node> nodeEntry : nodesIndex.entrySet())
            {
                addNodeRow(recentDocumentsCursor, nodeEntry.getValue());
            }
            if (!active)
            {
                // loading request is finished and refreshed
                mLoadingUris.remove(uri);
            }
        }

        if (active == null)
        {
            new StorageProviderAsyncTask(uri, recentDocumentsCursor, true)
            {
                @Override
                protected Void doInBackground(Void... params)
                {

                    List<Node> nodes = new ArrayList<Node>();
                    GregorianCalendar calendar = new GregorianCalendar();
                    calendar.add(Calendar.DAY_OF_YEAR, -7);
                    String formatedDate = DateUtils.format(calendar);

                    Log.d(TAG, "Recent Document : " + String.format(QUERY_RECENT, formatedDate));
                    nodes = session.getServiceRegistry().getSearchService()
                            .search(String.format(QUERY_RECENT, formatedDate), SearchLanguage.CMIS);
                    Log.d(TAG, "Recent Document : " + nodes.size());

                    for (Node node : nodes)
                    {
                        nodesIndex.put(node.getIdentifier(), node);
                    }

                    return null;
                }
            }.execute();
        }
        return recentDocumentsCursor;
    }

    @Override
    public void deleteDocument(final String documentId) throws FileNotFoundException
    {
        Log.v(TAG, "deleteDocument");

        final Uri uri = DocumentsContract.buildDocumentUri(mAuthority, documentId);

        Boolean active = mLoadingUris.get(uri);

        if (active != null && !active)
        {
            // loading request is finished and refreshed
            mLoadingUris.remove(uri);
        }

        if (active == null && session != null)
        {
            mLoadingUris.put(uri, Boolean.TRUE);

            new AsyncTask<Void, Void, Void>()
            {

                @Override
                protected Void doInBackground(Void... params)
                {
                    Node currentNode;
                    if (nodesIndex.containsKey(documentId))
                    {
                        currentNode = nodesIndex.get(documentId);
                    }
                    else
                    {
                        currentNode = session.getServiceRegistry().getDocumentFolderService()
                                .getNodeByIdentifier(documentId);
                    }
                    session.getServiceRegistry().getDocumentFolderService().deleteNode(currentNode);
                    return null;
                }

                protected void onPostExecute(Void noResult)
                {
                    mLoadingUris.put(uri, Boolean.FALSE);
                    getContext().getContentResolver().notifyChange(uri, null);
                };
            }.execute();
        }
        else if (session == null)
        {
            // Session unavailable
            // User needs to open the application
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }

    @Override
    public String createDocument(final String parentDocumentId, String mimeType, final String displayName)
            throws FileNotFoundException
    {

        Log.v(TAG, "Create Document");

        final Uri uri = DocumentsContract.buildDocumentUri(mAuthority, parentDocumentId);

        Boolean active = mLoadingUris.get(uri);

        if (active != null && !active)
        {
            // loading request is finished and refreshed
            mLoadingUris.remove(uri);
            return createdNode.getIdentifier();
        }

        if (active == null && session != null)
        {
            mLoadingUris.put(uri, Boolean.TRUE);

            new AsyncTask<Void, Void, Void>()
            {

                @Override
                protected Void doInBackground(Void... params)
                {
                    Node currentNode;
                    if (nodesIndex.containsKey(parentDocumentId))
                    {
                        currentNode = nodesIndex.get(parentDocumentId);
                    }
                    else if (pathIndex.containsKey(parentDocumentId))
                    {
                        currentNode = nodesIndex.get(parentDocumentId);
                    }
                    {
                        currentNode = session.getServiceRegistry().getDocumentFolderService()
                                .getNodeByIdentifier(parentDocumentId);
                    }

                    createdNode = session.getServiceRegistry().getDocumentFolderService()
                            .createDocument((Folder) currentNode, displayName, null, null);

                    return null;
                }

                protected void onPostExecute(Void noResult)
                {
                    mLoadingUris.put(uri, Boolean.FALSE);
                    getContext().getContentResolver().notifyChange(uri, null);
                };
            }.execute();
        }
        else if (session == null)
        {
            // Session unavailable
            // User needs to open the application
            getContext().getContentResolver().notifyChange(uri, null);
        }

        return displayName;
    }

    // //////////////////////////////////////////////////////////////////////
    // PROJECTION
    // //////////////////////////////////////////////////////////////////////
    /**
     * @param projection the requested root column projection
     * @return either the requested root column projection, or the default
     *         projection if the requested projection is null.
     */
    private static String[] resolveRootProjection(String[] projection)
    {
        return projection != null ? projection : DEFAULT_ROOT_PROJECTION;
    }

    private static String[] resolveDocumentProjection(String[] projection)
    {
        return projection != null ? projection : DEFAULT_DOCUMENT_PROJECTION;
    }

    // //////////////////////////////////////////////////////////////////////
    // CHECK SESSION
    // //////////////////////////////////////////////////////////////////////
    private void checkAccounts()
    {
        // Refresh in case of crash
        if (accountsIndex == null || accountsIndex.isEmpty())
        {
            accountManager = AccountManager.get(getContext());
            Account[] accounts = accountManager.getAccountsByType(AccountConstants.ACCOUNT_TYPE);
            accountsIndex = new HashMap<String, Account>(accounts.length);
            sessionIndex = new HashMap<String, AlfrescoSession>(accounts.length);
            for (Account account : accounts)
            {
                accountsIndex.put(account.name, account);
            }
        }
    }

    private boolean hasSession(Uri uri, ChildrenCursor childrenCursor)
    {
        if (session == null)
        {
            // Session unavailable
            // User needs to open the application
            removeUri(uri, false);
            childrenCursor.setErrorInformation("Refresh required.");
            return false;
        }
        else
        {
            return true;
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // ROOTS
    // //////////////////////////////////////////////////////////////////////
    private void addRootRow(ChildrenCursor result, Account account)
    {
        ChildrenCursor.RowBuilder row = result.newRow();
        row.add(Root.COLUMN_ROOT_ID, encodeItem(PREFIX_ACCOUNT, account.name));
        row.add(Root.COLUMN_SUMMARY, account.name);
        row.add(Root.COLUMN_FLAGS, Root.FLAG_SUPPORTS_CREATE | Root.FLAG_SUPPORTS_SEARCH | Root.FLAG_SUPPORTS_RECENTS);
        row.add(Root.COLUMN_DOCUMENT_ID, encodeItem(PREFIX_ACCOUNT, account.name));

        // Type & Logo
        accountType = Integer.parseInt(accountManager.getUserData(account, AccountConstants.ACCOUNT_NAME));
        switch (accountType)
        {
            case AccountConstants.ACCOUNT_CLOUD_VALUE:
                row.add(Root.COLUMN_TITLE, getContext().getString(R.string.account_alfresco_cloud));
                row.add(Root.COLUMN_ICON, R.drawable.ic_cloud);
                break;

            case AccountConstants.ACCOUNT_ONPREMISE_VALUE:
                row.add(Root.COLUMN_TITLE, getContext().getString(R.string.account_alfresco_onpremise));
                row.add(Root.COLUMN_ICON, R.drawable.ic_onpremise);
                break;
            default:
                row.add(Root.COLUMN_ICON, R.drawable.ic_launcher);
                break;
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // ROOT MENU
    // //////////////////////////////////////////////////////////////////////
    private void retrieveRootMenuChildren(Uri uri, final String parentDocumentId, ChildrenCursor rootMenuCursor)
    {
        Log.v(TAG, "retrieveRootMenuChildren");

        checkAccounts();

        selectedAccount = accountsIndex.get(parentDocumentId);
        selectedUrl = accountManager.getUserData(selectedAccount, AccountConstants.ACCOUNT_URL);

        Boolean active = mLoadingUris.get(uri);
        Boolean available = sessionIndex.containsKey(selectedAccount.name);

        Log.v(TAG, "active " + active + " available " + available);

        if (active != null || available)
        {
            fillRootMenuCursor(uri, active, rootMenuCursor);
            return;
        }

        if (active == null)
        {
            new StorageProviderAsyncTask(uri, rootMenuCursor)
            {
                @Override
                protected Void doInBackground(Void... params)
                {
                    try
                    {
                        switch (accountType)
                        {
                            case AccountConstants.ACCOUNT_CLOUD_VALUE:
                                if (oauthdata == null)
                                {
                                    Bundle options = new Bundle();
                                    options.putString(PARAM_URI, uri.toString());
                                    options.putString(PARAM_DOCUMENT_ID, parentDocumentId);
                                    tempCursor = childrenCursor;
                                    accountManager.getAuthToken(selectedAccount, AccountConstants.ACCOUNT_TYPE,
                                            options, false, AlfrescoStorageAccessProvider.this, null);
                                }
                                else
                                {
                                    session = CloudSession.connect(oauthdata);
                                }
                                break;
                            case AccountConstants.ACCOUNT_ONPREMISE_VALUE:
                                session = RepositorySession.connect(selectedUrl, selectedAccount.name,
                                        accountManager.getPassword(selectedAccount));
                                break;
                            default:
                                break;
                        }
                    }
                    catch (AlfrescoException e)
                    {
                        Log.v(TAG, "AlfrescoException");
                        exception = e;
                    }
                    return null;
                }
            }.execute();
        }
    }

    private void fillRootMenuCursor(Uri uri, Boolean active, ChildrenCursor rootMenuCursor)
    {
        Log.v(TAG, "fillRootMenuCursor");

        if (hasError(uri, active, rootMenuCursor)) { return; }
        if (!hasSession(uri, rootMenuCursor)) { return; }

        int id = -1;
        for (String idValue : IMPORT_FOLDER_LIST)
        {
            id = Integer.parseInt(idValue);
            addRootMenuRow(rootMenuCursor, id);
        }
        if (session.getRootFolder() != null)
        {
            addNodeRow(rootMenuCursor, session.getRootFolder(), true);
        }
        removeUri(uri, active);
    }

    private void addRootMenuRow(ChildrenCursor rootMenuCursor, int id)
    {
        ChildrenCursor.RowBuilder row = rootMenuCursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, id);
        row.add(Document.COLUMN_DISPLAY_NAME, getContext().getString(id));
        row.add(Document.COLUMN_SIZE, null);
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_LAST_MODIFIED, null);
        row.add(Document.COLUMN_FLAGS, 0);
    }

    // //////////////////////////////////////////////////////////////////////
    // SITES
    // //////////////////////////////////////////////////////////////////////
    private void retrieveSitesChildren(final Uri uri, final String parentFolderId, ChildrenCursor sitesCursor)
    {
        if (!hasSession(uri, sitesCursor)) { return; }
        new StorageProviderAsyncTask(uri, sitesCursor)
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                List<Site> sites = session.getServiceRegistry().getSiteService().getSites();
                for (Site site : sites)
                {
                    siteIndex.put(site.getIdentifier(), site);
                }
                return null;
            }
        }.execute();
    }

    private void fillSitesChildren(Uri uri, Boolean active, ChildrenCursor sitesCursor)
    {
        if (hasError(uri, active, sitesCursor)) { return; }
        if (!hasSession(uri, sitesCursor)) { return; }
        for (Entry<String, Site> siteEntry : siteIndex.entrySet())
        {
            addSiteRow(sitesCursor, siteEntry.getValue());
        }
        removeUri(uri, active);
    }

    private void addSiteRow(ChildrenCursor sitesCursor, Site site)
    {
        ChildrenCursor.RowBuilder row = sitesCursor.newRow();
        row.add(Document.COLUMN_DOCUMENT_ID, encodeItem(PREFIX_SITE, site.getIdentifier()));
        row.add(Document.COLUMN_DISPLAY_NAME, site.getTitle());
        row.add(Document.COLUMN_SIZE, null);
        row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
        row.add(Document.COLUMN_LAST_MODIFIED, null);
        row.add(Document.COLUMN_FLAGS, 0);
        row.add(Document.COLUMN_ICON, R.drawable.ic_site);
    }

    private void retrieveSiteDocumentLibraryChildren(final Uri uri, String parentDocumentId, ChildrenCursor sitesCursor)
    {
        if (!hasSession(uri, sitesCursor)) { return; }

        Site currentSite = null;
        if (siteIndex != null && siteIndex.containsKey(parentDocumentId))
        {
            currentSite = siteIndex.get(parentDocumentId);
        }
        else
        {
            currentSite = session.getServiceRegistry().getSiteService().getSite(parentDocumentId);
        }

        Folder documentLibraryFolder = session.getServiceRegistry().getSiteService().getDocumentLibrary(currentSite);

        retrieveFolderChildren(uri, documentLibraryFolder.getIdentifier(), sitesCursor);
    }

    // //////////////////////////////////////////////////////////////////////
    // FAVORITES FOLDER
    // //////////////////////////////////////////////////////////////////////
    private void retrieveFavoriteFoldersChildren(final Uri uri, final String parentFolderId,
            ChildrenCursor childrenCursor)
    {
        if (!hasSession(uri, childrenCursor)) { return; }

        new StorageProviderAsyncTask(uri, childrenCursor, true)
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                List<Folder> folders = new ArrayList<Folder>();
                folders = session.getServiceRegistry().getDocumentFolderService().getFavoriteFolders();
                for (Node node : folders)
                {
                    nodesIndex.put(node.getIdentifier(), node);
                }
                return null;
            }
        }.execute();
    }

    // //////////////////////////////////////////////////////////////////////
    // DOCUMENTS & FOLDERS
    // //////////////////////////////////////////////////////////////////////
    private void retrieveFolderChildren(final Uri uri, final String parentFolderId, ChildrenCursor childrenCursor)
    {
        if (!hasSession(uri, childrenCursor)) { return; }

        new StorageProviderAsyncTask(uri, childrenCursor, true)
        {
            @Override
            protected Void doInBackground(Void... params)
            {
                Log.d(TAG, "Parent ID : " + parentFolderId);
                List<Node> nodes = new ArrayList<Node>();
                if (parentFolderId == null)
                {
                    nodes = session.getServiceRegistry().getDocumentFolderService()
                            .getChildren(session.getRootFolder());
                }
                else
                {
                    currentFolder = (Folder) session.getServiceRegistry().getDocumentFolderService()
                            .getNodeByIdentifier(parentFolderId);
                    pathIndex.put(currentFolder.getIdentifier(), currentFolder);
                    nodes = session.getServiceRegistry().getDocumentFolderService().getChildren(currentFolder);
                }

                for (Node node : nodes)
                {
                    nodesIndex.put(node.getIdentifier(), node);
                }

                return null;
            }
        }.execute();
    }

    private void fillNodeChildren(Uri uri, Boolean active, ChildrenCursor childrenCursor)
    {
        if (hasError(uri, active, childrenCursor)) { return; }
        if (!hasSession(uri, childrenCursor)) { return; }

        for (Entry<String, Node> nodeEntry : nodesIndex.entrySet())
        {
            addNodeRow(childrenCursor, nodeEntry.getValue());
        }
        removeUri(uri, active);
    }

    private void addNodeRow(ChildrenCursor result, Node node)
    {
        addNodeRow(result, node, false);
    }

    private void addNodeRow(ChildrenCursor result, Node node, boolean isRoot)
    {
        int flags = 0;

        Permissions permission = session.getServiceRegistry().getDocumentFolderService().getPermissions(node);

        ChildrenCursor.RowBuilder row = result.newRow();

        row.add(Document.COLUMN_DOCUMENT_ID, node.getIdentifier());
        row.add(Document.COLUMN_DISPLAY_NAME,
                isRoot ? getContext().getString(R.string.menu_browse_root) : node.getName());
        if (node.isFolder())
        {
            row.add(Document.COLUMN_SIZE, null);
            row.add(Document.COLUMN_MIME_TYPE, Document.MIME_TYPE_DIR);
            if (permission.canAddChildren())
            {
                flags |= Document.FLAG_DIR_SUPPORTS_CREATE;
            }
        }
        else
        {
            row.add(Document.COLUMN_SIZE,
                    ((org.alfresco.mobile.android.api.model.Document) node).getContentStreamLength());
            flags |= Document.FLAG_SUPPORTS_THUMBNAIL;
            row.add(Document.COLUMN_MIME_TYPE,
                    ((org.alfresco.mobile.android.api.model.Document) node).getContentStreamMimeType());
            if (permission.canEdit())
            {
                flags |= Document.FLAG_SUPPORTS_WRITE;
            }

            if (permission.canDelete())
            {
                flags |= Document.FLAG_SUPPORTS_DELETE;
            }
        }

        row.add(Document.COLUMN_LAST_MODIFIED, isRoot ? null : node.getModifiedAt().getTimeInMillis());
        row.add(Document.COLUMN_FLAGS, flags);
    }

    // //////////////////////////////////////////////////////////////////////
    // ROOT MENU
    // //////////////////////////////////////////////////////////////////////
    private static String encodeItem(int prefix, String value)
    {
        return Integer.toString(prefix).concat(SEPARATOR).concat(value);
    }

    // //////////////////////////////////////////////////////////////////////
    // FILE DESCRIPTOR
    // //////////////////////////////////////////////////////////////////////

    private ParcelFileDescriptor createFileDescriptor(boolean isWrite, File file, int accessMode)
            throws FileNotFoundException
    {
        if (isWrite)
        {
            // Attach a close listener if the document is opened in write mode.
            try
            {
                Handler handler = new Handler(getContext().getMainLooper());
                return ParcelFileDescriptor.open(file, accessMode, handler, new ParcelFileDescriptor.OnCloseListener()
                {
                    @Override
                    public void onClose(IOException e)
                    {
                        // TODO !
                    }

                });
            }
            catch (IOException e)
            {
                throw new FileNotFoundException("Failed to open document");
            }
        }
        else
        {
            return ParcelFileDescriptor.open(file, accessMode);
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // INDEX
    // //////////////////////////////////////////////////////////////////////
    public void removeUri(Uri uri, Boolean active)
    {
        if (active != null && !active)
        {
            mLoadingUris.remove(uri);
        }
    }

    private boolean hasError(Uri uri, Boolean active, ChildrenCursor cursor)
    {
        if (exception != null)
        {
            cursor.setErrorInformation("Error : " + exception.getMessage());
            removeUri(uri, active);
            exception = null;
            return true;
        }
        return false;
    }

    // //////////////////////////////////////////////////////////////////////
    // OAUTH TOKEN
    // //////////////////////////////////////////////////////////////////////
    @Override
    public void run(AccountManagerFuture<Bundle> result)
    {
        try
        {
            Log.d(TAG, "OAUTH Result");
            Bundle bundle = result.getResult();
            oauthdata = new OAuth2DataImpl(bundle.getString(AccountAuthenticator.PARAM_APIKEY, ""), bundle.getString(
                    AccountAuthenticator.PARAM_APISECRET, ""), bundle.getString(AccountAuthenticator.PARAM_TOKEN, ""),
                    bundle.getString(AccountAuthenticator.PARAM_REFRESH_TOKEN, ""));
            Uri uri = Uri.parse(bundle.getString(PARAM_URI, ""));
            retrieveRootMenuChildren(uri, bundle.getString(PARAM_DOCUMENT_ID, ""), tempCursor);
            mLoadingUris.remove(uri);
        }
        catch (Exception e)
        {
            // TODO: handle exception
        }
    }

    // //////////////////////////////////////////////////////////////////////
    // Base AsyncTask
    // //////////////////////////////////////////////////////////////////////
    public abstract class StorageProviderAsyncTask extends AsyncTask<Void, Void, Void>
    {
        protected Uri uri;

        protected ChildrenCursor childrenCursor;

        private boolean clearNodes = false;

        public StorageProviderAsyncTask(Uri uri, ChildrenCursor childrenCursor)
        {
            this.uri = uri;
            this.childrenCursor = childrenCursor;
        }

        public StorageProviderAsyncTask(Uri uri, ChildrenCursor childrenCursor, boolean clearNodes)
        {
            this.uri = uri;
            this.childrenCursor = childrenCursor;
            this.clearNodes = clearNodes;
        }

        @Override
        protected void onPreExecute()
        {
            if (clearNodes && nodesIndex != null)
            {
                nodesIndex.clear();
            }
            startLoadingUri(uri, childrenCursor);
        }

        protected void onPostExecute(Void noResult)
        {
            stopLoadingUri(uri);
        }

        @Override
        protected void onCancelled()
        {
            uri = null;
            childrenCursor = null;
        }

        public void startLoadingUri(Uri uri, ChildrenCursor childrenCursor)
        {
            childrenCursor.setIsLoading(true);
            childrenCursor.setNotificationUri(getContext().getContentResolver(), uri);
            mLoadingUris.put(uri, Boolean.TRUE);
        }

        public void stopLoadingUri(Uri uri)
        {
            mLoadingUris.put(uri, Boolean.FALSE);
            getContext().getContentResolver().notifyChange(uri, null);
        }
    }
}
