Welcome to the Alfresco Android Storage Access Framework :  Document Provider 
===================================

The goal of this project is to demonstrates how to implement a custom Document Provider based on [Alfresco Mobile SDK](https://www.alfresco.com/develop/mobile) and the Android 4.4 [Storage Access Framework](https://developer.android.com/guide/topics/providers/document-provider.html).

It provides just the essential to setup an account binded to an Alfresco OnPremise or Alfresco Cloud server. This account is then used by client application which used the Storage Access Framework like Google Drive or GMail application.

In other words there's no Launcher activity which means there's no Launcher icon available inside your device application listing. 

See **Usage** section on how to use this project.


Usage
-------

### Add a server
* Install the application. 
* Go to your device Settings > Account
 * Tap on Add Account
 * Select Alfresco Server from the list
* Choose Alfresco onPremise or Alfresco Cloud
 * Enter your credentials (and url for Alfresco OnPremise)
 * Validate

### Pick a file from Alfresco and attach it to an Email via GMail
* Open GMail application 
* Create a New Email
* In option menu
  * Tap attach file
* In the File picker dialog, 
 * Select Alfresco Server account
 * Browse the repository until you find the appropriate document
 * Select it

### Pick a file from Alfresco and upload it to Google Drive
* Open Google Drive application 
* Tap on Upload...
* In the File picker dialog, 
 * Select Alfresco Server account
 * Browse the repository until you find the appropriate document
 * Select it
 * Upload it to Google Drive


Test Server
-------
If you have no Alfresco onPremise server, you can use this public one 
* URL : http://cmis.alfresco.com/cmisatom
* User : admin
* Password : admin


Architecture
------------

* [AlfrescoStorageAccessProvider](AlfrescoStorageAccessFramework/src/fr/opensourceecm/android/alfresco/storage/provider/AlfrescoStorageAccessProvider.java) : Document Provider
* [AccountCreationActivity](AlfrescoStorageAccessFramework/src/fr/opensourceecm/android/alfresco/account/ui/AccountCreationActivity.java) : Activity to create an Account



License
-------

### Alfresco Android Storage Access Framework :  Document Provider

> This project is distributed under the [Apache 2 License](http://www.apache.org/licenses/LICENSE-2.0.html).


### Alfresco Android SDK

> Copyright © 2014 Alfresco Software, Ltd. and others. 

> The Alfresco Android SDK is distributed under the [Apache 2 License](http://www.apache.org/licenses/LICENSE-2.0.html).

> More information can be found on Alfresco [developer portal](http://developer.alfresco.com/mobile), on [website](http://www.alfresco.com/products/mobile) and [wiki](https://wiki.alfresco.com/wiki/Mobile).

