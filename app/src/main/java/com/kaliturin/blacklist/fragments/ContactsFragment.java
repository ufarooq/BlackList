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

import android.content.Context;
import android.database.Cursor;
import android.os.Bundle;
import androidx.annotation.Nullable;
import androidx.annotation.StringRes;
import androidx.fragment.app.Fragment;
import androidx.loader.app.LoaderManager;
import androidx.loader.content.CursorLoader;
import androidx.loader.content.Loader;
import androidx.core.view.MenuItemCompat;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.kaliturin.blacklist.R;
import com.kaliturin.blacklist.activities.CustomFragmentActivity;
import com.kaliturin.blacklist.adapters.ContactsCursorAdapter;
import com.kaliturin.blacklist.utils.ButtonsBar;
import com.kaliturin.blacklist.utils.ContactsAccessHelper.ContactSourceType;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper.Contact;
import com.kaliturin.blacklist.utils.DefaultSMSAppHelper;
import com.kaliturin.blacklist.utils.DialogBuilder;
import com.kaliturin.blacklist.utils.IdentifiersContainer;
import com.kaliturin.blacklist.utils.Permissions;
import com.kaliturin.blacklist.utils.Utils;

/**
 * Contacts fragment (black/white list)
 */

public class ContactsFragment extends Fragment implements FragmentArguments {
    private boolean defaultSMSAppPrompt = true;
    private ContactsCursorAdapter cursorAdapter = null;
    private ButtonsBar snackBar = null;
    private int contactType = 0;
    private String itemsFilter = null;
    private ListView listView = null;
    private int listPosition = 0;

    public ContactsFragment() {
        // Required empty public constructor
    }

    @Override
    public void onActivityCreated(@Nullable Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);
        // set activity title
        Bundle arguments = getArguments();
        ActionBar actionBar = ((AppCompatActivity) getActivity()).getSupportActionBar();
        if (arguments != null && actionBar != null) {
            actionBar.setTitle(arguments.getString(TITLE));
        }
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
            contactType = arguments.getInt(CONTACT_TYPE, 0);
        }

        if (savedInstanceState != null) {
            listPosition = savedInstanceState.getInt(LIST_POSITION, 0);
        }

        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_contacts, container, false);
    }

    @Override
    public void onViewCreated(final View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        Permissions.notifyIfNotGranted(getContext(), Permissions.WRITE_EXTERNAL_STORAGE);

        // snack bar
        snackBar = new ButtonsBar(view, R.id.three_buttons_bar);
        // "Cancel button" button
        snackBar.setButton(R.id.button_left,
                getString(R.string.CANCEL),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackBar.dismiss();
                        clearCheckedItems();
                    }
                });
        // "Delete" button
        snackBar.setButton(R.id.button_center,
                getString(R.string.DELETE),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        snackBar.dismiss();
                        deleteCheckedItems();
                    }
                });
        // "Select all" button
        snackBar.setButton(R.id.button_right,
                getString(R.string.SELECT_ALL),
                new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        setAllItemsChecked();
                    }
                });

        // cursor adapter
        cursorAdapter = new ContactsCursorAdapter(getContext());

        // on row click listener
        cursorAdapter.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View row) {
                if (cursorAdapter.hasCheckedItems()) {
                    snackBar.show();
                } else {
                    snackBar.dismiss();
                }
            }
        });

        // on row long click listener (receives clicked row)
        cursorAdapter.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View row) {
                // get contact from the clicked row
                final Contact contact = cursorAdapter.getContact(row);
                if (contact != null) {
                    // create and show menu dialog for actions with the contact
                    DialogBuilder dialog = new DialogBuilder(getContext());
                    dialog.setTitle(contact.name).
                            // add menu item of contact editing
                                    addItem(R.string.Edit_contact, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    // edit contact
                                    editContact(contact.id);
                                }
                            }).
                            // add menu item of contact deletion
                                    addItem(R.string.Remove_contact, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    deleteContact(contact.id);
                                    reloadItems(itemsFilter);
                                }
                            });
                    // add menu item of contact moving to opposite list
                    String itemTitle = (contact.type == Contact.TYPE_WHITE_LIST ?
                            getString(R.string.Move_to_black_list) :
                            getString(R.string.Move_to_white_list));
                    dialog.addItem(itemTitle, new View.OnClickListener() {
                        @Override
                        public void onClick(View v) {
                            moveContactToOppositeList(contact);
                            reloadItems(itemsFilter);
                        }
                    }).show();
                }
                return true;
            }
        });

        // add cursor listener to the list
        listView = (ListView) view.findViewById(R.id.contacts_list);
        listView.setAdapter(cursorAdapter);

        // on list empty comment
        TextView textEmptyView = (TextView) view.findViewById(R.id.text_empty);
        listView.setEmptyView(textEmptyView);

        // load the list view
        loadListViewItems(itemsFilter, false, listPosition);

        // prompt how to enable SMS-blocking
        showDefaultSMSAppPrompt();
    }

    @Override
    public void onDestroyView() {
        getLoaderManager().destroyLoader(0);
        super.onDestroyView();
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.main, menu);

        // tune menu options
        MenuItem itemSearch = menu.findItem(R.id.action_search);
        Utils.setMenuIconTint(getContext(), itemSearch, R.attr.colorAccent);
        itemSearch.setVisible(true);
        MenuItem itemAdd = menu.findItem(R.id.action_add);
        Utils.setMenuIconTint(getContext(), itemAdd, R.attr.colorAccent);
        itemAdd.setVisible(true);

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

        // item's 'add contact' on click listener
        itemAdd.setOnMenuItemClickListener(new MenuItem.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem item) {
                // show menu dialog
                showAddContactsMenuDialog();

                return true;
            }
        });

        super.onCreateOptionsMenu(menu, inflater);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(LIST_POSITION, listView.getFirstVisiblePosition());
    }

    @Override
    public void onPause() {
        super.onPause();
        listPosition = listView.getFirstVisiblePosition();
    }

//----------------------------------------------------

    // Deletes contact by id
    private void deleteContact(long id) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(getContext());
        if (db != null) {
            db.deleteContact(id);
        }
    }

    // Move contact to opposite type list
    private void moveContactToOppositeList(Contact contact) {
        DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(getContext());
        if (db != null) {
            db.moveContact(contact);
        }
    }

    // Clears all items selection
    private void clearCheckedItems() {
        if (cursorAdapter != null) {
            cursorAdapter.setAllItemsChecked(false);
        }
    }

    // Sets all items selected
    private void setAllItemsChecked() {
        if (cursorAdapter != null) {
            cursorAdapter.setAllItemsChecked(true);
        }
    }

    // Closes snack bar
    public boolean dismissSnackBar() {
        clearCheckedItems();
        return snackBar != null && snackBar.dismiss();
    }

    // Deletes checked items
    private void deleteCheckedItems() {
        int listPosition = listView.getFirstVisiblePosition();
        loadListViewItems(itemsFilter, true, listPosition);
    }

    // Reloads items
    private void reloadItems(String itemsFilter) {
        this.itemsFilter = itemsFilter;
        dismissSnackBar();

        int listPosition = listView.getFirstVisiblePosition();
        loadListViewItems(itemsFilter, false, listPosition);
    }

    // Loads SMS conversations to the list view
    private void loadListViewItems(String itemsFilter, boolean deleteItems, int listPosition) {
        if (!isAdded()) {
            return;
        }
        int loaderId = 0;
        ContactsLoaderCallbacks callbacks =
                new ContactsLoaderCallbacks(getContext(), contactType, cursorAdapter,
                        itemsFilter, deleteItems, listView, listPosition);
        LoaderManager manager = getLoaderManager();
        if (manager.getLoader(loaderId) == null) {
            // init and run the items loader
            manager.initLoader(loaderId, null, callbacks);
        } else {
            // restart loader
            manager.restartLoader(loaderId, null, callbacks);
        }
    }

    // Opens fragment for contact editing
    private void editContact(long id) {
        Bundle arguments = new Bundle();
        arguments.putInt(CONTACT_ID, (int) id);
        arguments.putInt(CONTACT_TYPE, contactType);
        CustomFragmentActivity.show(getActivity(), getString(R.string.Editing_contact),
                AddOrEditContactFragment.class, arguments, 0);
    }

//----------------------------------------------------

    // Contact items loader
    private static class ContactsLoader extends CursorLoader {
        private IdentifiersContainer deletingItems;
        private int contactType;
        private String itemsFilter;

        ContactsLoader(Context context,
                       int contactType,
                       String itemsFilter,
                       @Nullable IdentifiersContainer deletingItems) {
            super(context);
            this.contactType = contactType;
            this.itemsFilter = itemsFilter;
            this.deletingItems = deletingItems;
        }

        @Override
        public Cursor loadInBackground() {
            DatabaseAccessHelper db = DatabaseAccessHelper.getInstance(getContext());
            if (db == null) {
                return null;
            }
            if (deletingItems != null) {
                db.deleteContacts(contactType, deletingItems, itemsFilter);
            }
            return db.getContacts(contactType, itemsFilter);
        }
    }

    // Contact items loader callbacks
    private static class ContactsLoaderCallbacks implements LoaderManager.LoaderCallbacks<Cursor> {
        private Context context;
        private int contactType;
        private String itemsFilter;
        private ContactsCursorAdapter cursorAdapter;
        private boolean deleteItems;
        private ListView listView;
        private int listPosition;

        ContactsLoaderCallbacks(Context context,
                                int contactType,
                                ContactsCursorAdapter cursorAdapter,
                                String itemsFilter,
                                boolean deleteItems,
                                ListView listView,
                                int listPosition) {
            this.context = context;
            this.itemsFilter = itemsFilter;
            this.contactType = contactType;
            this.cursorAdapter = cursorAdapter;
            this.deleteItems = deleteItems;
            this.listView = listView;
            this.listPosition = listPosition;
        }

        @Override
        public Loader<Cursor> onCreateLoader(int id, Bundle args) {
            IdentifiersContainer deletingItems = null;
            if (deleteItems) {
                deletingItems = cursorAdapter.getCheckedItems().clone();
            }
            return new ContactsLoader(context, contactType, itemsFilter, deletingItems);
        }

        @Override
        public void onLoadFinished(Loader<Cursor> loader, final Cursor data) {
            cursorAdapter.changeCursor(data);

            // scroll list to the saved position
            listView.post(new Runnable() {
                @Override
                public void run() {
                    listView.setSelection(listPosition);
                }
            });
        }

        @Override
        public void onLoaderReset(Loader<Cursor> loader) {
            cursorAdapter.changeCursor(null);
        }
    }

//----------------------------------------------------

    // Shows menu dialog of contacts adding
    private void showAddContactsMenuDialog() {
        // create and show menu dialog for actions with the contact
        DialogBuilder dialog = new DialogBuilder(getContext());
        dialog.setTitle(R.string.Add_contact).
                addItem(R.string.From_contacts_list, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddContactsActivity(Permissions.READ_CONTACTS,
                                ContactSourceType.FROM_CONTACTS,
                                R.string.List_of_contacts);
                    }
                }).
                addItem(R.string.From_calls_list, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddContactsActivity(Permissions.READ_CALL_LOG,
                                ContactSourceType.FROM_CALLS_LOG,
                                R.string.List_of_calls);
                    }
                }).
                addItem(R.string.From_SMS_list, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        showAddContactsActivity(Permissions.READ_SMS,
                                ContactSourceType.FROM_SMS_LIST,
                                R.string.List_of_SMS);
                    }
                });
        if (contactType == Contact.TYPE_WHITE_LIST) {
            dialog.addItem(R.string.From_Black_list, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddContactsActivity(Permissions.WRITE_EXTERNAL_STORAGE,
                            ContactSourceType.FROM_BLACK_LIST,
                            R.string.Black_list);
                }
            });
        } else {
            dialog.addItem(R.string.From_White_list, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    showAddContactsActivity(Permissions.WRITE_EXTERNAL_STORAGE,
                            ContactSourceType.FROM_WHITE_LIST,
                            R.string.White_list);
                }
            });
        }
        dialog.addItem(R.string.Manually, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                showAddContactsActivity(Permissions.WRITE_EXTERNAL_STORAGE,
                        null, R.string.Adding_contact);
            }
        }).show();
    }

    // Shows activity of of contacts adding
    private void showAddContactsActivity(String permission,
                                         ContactSourceType sourceType, @StringRes int titleId) {
        // if permission isn't granted
        if (Permissions.notifyIfNotGranted(getContext(), permission)) {
            return;
        }

        // permission is granted
        Bundle arguments = new Bundle();
        Class<? extends Fragment> fragmentClass;
        if (sourceType != null) {
            // fragment of adding contacts from sms-content / call log
            arguments.putInt(CONTACT_TYPE, contactType);
            arguments.putSerializable(SOURCE_TYPE, sourceType);
            arguments.putBoolean(SINGLE_NUMBER_MODE, true);
            fragmentClass = AddContactsFragment.class;
        } else {
            // fragment of adding contacts manually
            arguments.putInt(CONTACT_TYPE, contactType);
            fragmentClass = AddOrEditContactFragment.class;
        }

        // open activity with contacts adding fragment
        CustomFragmentActivity.show(getActivity(),
                getString(titleId), fragmentClass, arguments, 0);
    }

    // Prompts how to enable SMS-blocking
    private void showDefaultSMSAppPrompt() {
        if (defaultSMSAppPrompt && contactType == Contact.TYPE_BLACK_LIST) {
            defaultSMSAppPrompt = false;
            if (!DefaultSMSAppHelper.isDefault(getContext())) {
                Toast.makeText(getContext(), R.string.To_block_SMS_set_def_app, Toast.LENGTH_LONG).show();
            }
        }
    }
}
