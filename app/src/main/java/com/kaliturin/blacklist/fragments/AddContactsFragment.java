/*
 * Copyright (C) 2017 Anton Kaliturin <kaliturin@gmail.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not
 * use this file except in compliance with the License. You may obtain a copy of
 * the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS, WITHOUT
 * WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied. See the
 * License for the specific language governing permissions and limitations under
 * the License.
 */

package com.kaliturin.blacklist.fragments;


import android.app.Activity;
import android.content.Context;
import android.content.DialogInterface;
import android.database.Cursor;
import android.os.AsyncTask;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.collection.LongSparseArray;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;

import com.kaliturin.blacklist.R;
import com.kaliturin.blacklist.adapters.ContactsCursorAdapter;
import com.kaliturin.blacklist.utils.ButtonsBar;
import com.kaliturin.blacklist.utils.ContactsAccessHelper;
import com.kaliturin.blacklist.utils.ContactsAccessHelper.ContactSourceType;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper.Contact;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper.ContactNumber;
import com.kaliturin.blacklist.utils.DialogBuilder;
import com.kaliturin.blacklist.utils.Permissions;
import com.kaliturin.blacklist.utils.ProgressDialogHolder;
import com.kaliturin.blacklist.utils.Utils;

import java.util.List;

/**
 * Fragment for representation the list of contacts to choose
 * which one is adding to the black/white list
 */
public class AddContactsFragment extends Fragment implements FragmentArguments {
    private ContactsCursorAdapter cursorAdapter = null;
    private ButtonsBar snackBar = null;
    private ContactSourceType sourceType = null;
    private int contactType = 0;
    private boolean singleNumberMode = false;
    private LongSparseArray<ContactNumber> singleContactNumbers = new LongSparseArray<>();

    public AddContactsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {

        Bundle arguments = getArguments();
        if (arguments != null) {
            contactType = arguments.getInt(CONTACT_TYPE);
            sourceType = (ContactSourceType) arguments.getSerializable(SOURCE_TYPE);
            singleNumberMode = arguments.getBoolean(SINGLE_NUMBER_MODE);
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_add_contacts, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // snack bar
        snackBar = new ButtonsBar(view, R.id.three_buttons_bar);
        // "Cancel button" button
        snackBar.setButton(R.id.button_left,
                getString(R.string.CANCEL),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        finishActivity(Activity.RESULT_CANCELED);
                    }
                });
        // "Add" button
        snackBar.setButton(R.id.button_center,
                getString(R.string.ADD),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackBar.dismiss();
                        // write checked contacts to the DB
                        addCheckedContacts();
                    }
                });
        // "Select all" button
        snackBar.setButton(R.id.button_right,
                getString(R.string.SELECT_ALL),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setCheckedAllItems();
                    }
                });

        // cursor adapter
        cursorAdapter = new ContactsCursorAdapter(getContext());
        cursorAdapter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View row) {
                if (cursorAdapter.hasCheckedItems()) {
                    snackBar.show();
                } else {
                    snackBar.dismiss();
                }

                if (singleNumberMode && cursorAdapter.isItemChecked(row)) {
                    Contact contact = cursorAdapter.getContact(row);
                    if (contact != null && contact.numbers.size() > 1) {
                        askForSingleContactNumber(contact);
                    }
                }
            }
        });

        // add cursor listener to the list
        ListView listView = (ListView) view.findViewById(R.id.contacts_list);
        listView.setAdapter(cursorAdapter);

        // on list empty comment
        TextView textEmptyView = (TextView) view.findViewById(R.id.text_empty);
        listView.setEmptyView(textEmptyView);

        // init and run the loader of contacts
        getLoaderManager().initLoader(0, null, newLoaderCallbacks(null));
    }

    @Override
    public void onDestroyView() {
        getLoaderManager().destroyLoader(0);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);
        MenuItem itemSearch = menu.findItem(R.id.action_search);
        Utils.setMenuIconTint(getContext(), itemSearch, R.attr.colorAccent);
        itemSearch.setVisible(true);

        // get the view from search menu item
        SearchView searchView = (SearchView) MenuItemCompat.getActionView(itemSearch);
        searchView.setQueryHint(getString(R.string.Search_action));
        // set on text change listener
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                reloadItems(newText);
                return true;
            }
        });

        // on search cancelling
        // SearchView.OnCloseListener is not calling so use other way...
        MenuItemCompat.setOnActionExpandListener(itemSearch,
                new MenuItemCompat.OnActionExpandListener() {
                    @Override
                    public boolean onMenuItemActionExpand(MenuItem item) {
                        return true;
                    }

                    @Override
                    public boolean onMenuItemActionCollapse(MenuItem item) {
                        reloadItems(null);
                        return true;
                    }
                });

        super.onCreateOptionsMenu(menu, inflater);
    }

//-------------------------------------------------------------------

    // Opens menu dialog with list of contact's numbers to choose
    private void askForSingleContactNumber(final Contact contact) {
        DialogBuilder dialog = new DialogBuilder(getContext());
        dialog.setTitle(contact.name);
        for (ContactNumber contactNumber : contact.numbers) {
            dialog.addItem(0, contactNumber.number, contactNumber, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    ContactNumber contactNumber = (ContactNumber) v.getTag();
                    if (contactNumber != null) {
                        singleContactNumbers.put(contact.id, contactNumber);
                    }
                }
            });
        }
        if (contact.numbers.size() > 1) {
            dialog.addItem(0, R.string.Select_all, null, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    singleContactNumbers.remove(contact.id);
                }
            });
        }
        dialog.show();
    }

    // Clears all items selection
    private void clearCheckedItems() {
        singleContactNumbers.clear();
        if (cursorAdapter != null) {
            cursorAdapter.setAllItemsChecked(false);
        }
    }

    // Sets all items selected
    private void setCheckedAllItems() {
        singleContactNumbers.clear();
        if (cursorAdapter != null) {
            cursorAdapter.setAllItemsChecked(true);
        }
    }

    // Closes snack bar
    public boolean dismissSnackBar() {
        clearCheckedItems();
        return snackBar != null && snackBar.dismiss();
    }

    // Reloads items
    private void reloadItems(String itemsFilter) {
        singleContactNumbers.clear();
        dismissSnackBar();
        if (isAdded()) {
            getLoaderManager().restartLoader(0, null, newLoaderCallbacks(itemsFilter));
        }
    }

    // Creates new contacts loader
    private ContactsLoaderCallbacks newLoaderCallbacks(String itemsFilter) {
        return new ContactsLoaderCallbacks(getContext(), sourceType, cursorAdapter, itemsFilter);
    }

//-------------------------------------------------------------------

    // Contact items loader
    private static class ContactsLoader extends CursorLoader {
        private ContactSourceType sourceType;
        private String itemsFilter;

        ContactsLoader(Context context,
                       ContactSourceType sourceType,
                       String itemsFilter) {
            super(context);
            this.sourceType = sourceType;
            this.itemsFilter = itemsFilter;
        }

        @Override
        public Cursor loadInBackground() {
            ContactsAccessHelper dao = ContactsAccessHelper.getInstance(getContext());
            return dao.getContacts(getContext(), sourceType, itemsFilter);
        }
    }

    // Contact items loader callbacks
    private static class ContactsLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        ProgressDialogHolder progress = new ProgressDialogHolder();
        private Context context;
        private ContactSourceType sourceType;
        private ContactsCursorAdapter cursorAdapter;
        private String itemsFilter;

        ContactsLoaderCallbacks(Context context,
                                ContactSourceType sourceType,
                                ContactsCursorAdapter cursorAdapter,
                                String itemsFilter) {
            this.context = context;
            this.sourceType = sourceType;
            this.cursorAdapter = cursorAdapter;
            this.itemsFilter = itemsFilter;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            progress.show(context, 0, R.string.Loading_);
            return new ContactsLoader(context, sourceType, itemsFilter);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, Cursor data) {
            cursorAdapter.changeCursor(data);
            progress.dismiss();
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            cursorAdapter.changeCursor(null);
            progress.dismiss();
        }
    }

//-------------------------------------------------------------------

    private void finishActivity(int result) {
        getActivity().setResult(result);
        getActivity().finish();
    }

    // Writes checked contacts to the database
    private void addCheckedContacts() {
        // get list of contacts and write it to the DB
        List<Contact> contacts = cursorAdapter.extractCheckedContacts();
        addContacts(contacts, singleContactNumbers);
    }

    // Writes checked contacts to the database
    protected void addContacts(List<Contact> contacts, LongSparseArray<ContactNumber> singleContactNumbers) {
        // if permission is granted
        if (!Permissions.notifyIfNotGranted(getContext(), Permissions.WRITE_EXTERNAL_STORAGE)) {
            ContactsWriter writer = new ContactsWriter(contactType, contacts, singleContactNumbers.clone());
            writer.execute();
        }
    }

    // Async task - writes contacts to the DB
    private class ContactsWriter extends AsyncTask<Void, Integer, Void> {
        ProgressDialogHolder progress = new ProgressDialogHolder();
        List<Contact> contacts;
        LongSparseArray<ContactNumber> singleContactNumbers;
        private int contactType;

        ContactsWriter(int contactType, List<Contact> contacts,
                       LongSparseArray<ContactNumber> singleContactNumbers) {
            this.contactType = contactType;
            this.contacts = contacts;
            this.singleContactNumbers = singleContactNumbers;
        }

        @Override
        protected Void doInBackground(Void... params) {
            DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(getContext());
            if (db != null) {
                int count = 1;
                for (Contact contact : contacts) {
                    if (isCancelled()) break;
                    ContactNumber contactNumber = singleContactNumbers.get(contact.id);
                    if (contactNumber != null) {
                        // add only the single number of contact
                        db.addContact(contactType, contact.name, contactNumber);
                        publishProgress(100 / contacts.size() * count++);
                    } else {
                        // add all numbers of contact
                        db.addContact(contactType, contact.name, contact.numbers);
                        publishProgress(100 / contacts.size() * count++);
                    }
                }
            }
            return null;
        }

        @Override
        protected void onCancelled() {
            super.onCancelled();
            progress.dismiss();
            finishActivity(Activity.RESULT_OK);
        }

        @Override
        protected void onPostExecute(Void result) {
            progress.dismiss();
            finishActivity(Activity.RESULT_OK);
        }

        @Override
        protected void onPreExecute() {
            progress.show(getContext(), new DialogInterface.OnCancelListener() {
                @Override
                public void onCancel(DialogInterface dialog) {
                    ContactsWriter.this.cancel(true);
                }
            });
            progress.setMessage(getString(R.string.Saving_) + " 0%");
        }

        @Override
        protected void onProgressUpdate(Integer... values) {
            progress.setMessage(getString(R.string.Saving_) + " " + values[0] + "%");
        }
    }
}
