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

package com.kaliturin.blacklist.activities;

import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import androidx.annotation.IdRes;
import androidx.annotation.NonNull;
import com.google.android.material.navigation.NavigationView;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentTransaction;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import android.view.KeyEvent;
import android.view.MenuItem;
import android.view.View;

import com.kaliturin.blacklist.R;
import com.kaliturin.blacklist.fragments.ContactsFragment;
import com.kaliturin.blacklist.fragments.FragmentArguments;
import com.kaliturin.blacklist.fragments.InformationFragment;
import com.kaliturin.blacklist.fragments.JournalFragment;
import com.kaliturin.blacklist.fragments.SMSConversationFragment;
import com.kaliturin.blacklist.fragments.SMSConversationsListFragment;
import com.kaliturin.blacklist.fragments.SMSSendFragment;
import com.kaliturin.blacklist.fragments.SettingsFragment;
import com.kaliturin.blacklist.utils.ContactsAccessHelper;
import com.kaliturin.blacklist.utils.DatabaseAccessHelper.Contact;
import com.kaliturin.blacklist.utils.Permissions;
import com.kaliturin.blacklist.utils.Settings;

public class MainActivity extends AppCompatActivity
        implements NavigationView.OnNavigationItemSelectedListener {

    public static final String ACTION_JOURNAL = "com.kaliturin.blacklist.ACTION_JOURNAL";
    public static final String ACTION_SMS_CONVERSATIONS = "com.kaliturin.blacklist.ACTION_SMS_CONVERSATIONS";
    public static final String ACTION_SETTINGS = "com.kaliturin.blacklist.ACTION_SETTINGS";
    public static final String ACTION_SMS_SEND_TO = "android.intent.action.SENDTO";

    private static final String CURRENT_ITEM_ID = "CURRENT_ITEM_ID";
    private FragmentSwitcher fragmentSwitcher = new FragmentSwitcher();
    private NavigationView navigationView;
    private DrawerLayout drawer;
    private int selectedMenuItemId = 0;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Settings.applyCurrentTheme(this);
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        // permissions
        Permissions.checkAndRequest(this);

        // init settings defaults
        Settings.initDefaults(this);

        // toolbar
        Toolbar toolbar = (Toolbar) findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // show custom toolbar shadow on pre LOLLIPOP devices
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            View view = findViewById(R.id.toolbar_shadow);
            if (view != null) {
                view.setVisibility(View.VISIBLE);
            }
        }

        // drawer
        drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(this, drawer, toolbar,
                R.string.Open_navigation_drawer, R.string.Close_navigation_drawer);
        drawer.addDrawerListener(toggle);
        toggle.syncState();

        // navigation menu
        navigationView = (NavigationView) findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);

        // if there was a screen rotation
        int itemId;
        if (savedInstanceState != null) {
            // get saved current navigation menu item
            itemId = savedInstanceState.getInt(CURRENT_ITEM_ID);
        } else {
            // choose the fragment by activity's action
            String action = getIntent().getAction();
            action = (action == null ? "" : action);
            switch (action) {
                case ACTION_SMS_SEND_TO:
                    // show SMS sending activity
                    showSendSMSActivity();
                    // switch to SMS chat fragment
                    itemId = R.id.nav_sms;
                    break;
                case ACTION_SMS_CONVERSATIONS:
                    // switch to SMS chat fragment
                    itemId = R.id.nav_sms;
                    break;
                case ACTION_SETTINGS:
                    // switch to settings fragment
                    itemId = R.id.nav_settings;
                    break;
                case ACTION_JOURNAL:
                    // switch to journal fragment
                    itemId = R.id.nav_journal;
                    break;
                default:
                    if (Settings.getBooleanValue(this, Settings.GO_TO_JOURNAL_AT_START)) {
                        // switch to journal fragment
                        itemId = R.id.nav_journal;
                    } else {
                        // switch to SMS chat fragment
                        itemId = R.id.nav_sms;
                    }
                    break;
            }
            // switch to chosen fragment
            fragmentSwitcher.switchFragment(itemId);
        }

        // select navigation menu item
        selectNavigationMenuItem(itemId);
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putInt(CURRENT_ITEM_ID, selectedMenuItemId);
    }

    @Override
    public void onBackPressed() {
        if (drawer.isDrawerOpen(GravityCompat.START)) {
            drawer.closeDrawer(GravityCompat.START);
        } else {
            if (!fragmentSwitcher.onBackPressed()) {
                if (!Settings.getBooleanValue(this, Settings.DONT_EXIT_ON_BACK_PRESSED)) {
                    super.onBackPressed();
                }
            }
        }
    }

    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int itemId = item.getItemId();

        // exit item was clicked
        if (itemId == R.id.nav_exit) {
            finish();
            return true;
        }

        // switch to the new fragment
        fragmentSwitcher.switchFragment(itemId);
        drawer.closeDrawer(GravityCompat.START);

        // Normally we don't need to select navigation items manually. But in API 10
        // (and maybe some another) there is bug of menu item selection/deselection.
        // To resolve this problem we deselect the old selected item and select the
        // new one manually. And it's why we return false in the current method.
        // This way of deselection of the item was found as the most appropriate.
        // Because of some side effects of all others tried.
        selectNavigationMenuItem(itemId);

        return false;
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        // check for result code from the child activity (it could be a dialog-activity)
        if (requestCode == 0 && resultCode == RESULT_OK) {
            fragmentSwitcher.updateFragment();
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode,
                                           @NonNull String permissions[],
                                           @NonNull int[] grantResults) {
        // process permissions results
        Permissions.onRequestPermissionsResult(requestCode, permissions, grantResults);
        // check granted permissions and notify about not granted
        Permissions.notifyIfNotGranted(this);
    }

    @Override
    public boolean onKeyUp(int keyCode, KeyEvent event) {
        if (keyCode == KeyEvent.KEYCODE_MENU) {
            DrawerLayout drawer = (DrawerLayout) findViewById(R.id.drawer_layout);
            if (drawer.isDrawerOpen(GravityCompat.START)) {
                drawer.closeDrawer(GravityCompat.START);
            } else {
                drawer.openDrawer(GravityCompat.START);
            }
            return true;
        }
        return super.onKeyUp(keyCode, event);
    }

//----------------------------------------------------------------------------

    private void selectNavigationMenuItem(int itemId) {
        navigationView.getMenu().clear();
        navigationView.inflateMenu(R.menu.activity_main_drawer);
        navigationView.getMenu().findItem(itemId).setChecked(true);
        // save selected item
        selectedMenuItemId = itemId;
    }

    // Switcher of activity's fragments
    private class FragmentSwitcher implements FragmentArguments {
        private final String CURRENT_FRAGMENT = "CURRENT_FRAGMENT";
        private ContactsFragment blackListFragment = new ContactsFragment();
        private ContactsFragment whiteListFragment = new ContactsFragment();
        private JournalFragment journalFragment = new JournalFragment();
        private SettingsFragment settingsFragment = new SettingsFragment();
        private InformationFragment informationFragment = new InformationFragment();
        private SMSConversationsListFragment smsFragment = new SMSConversationsListFragment();

        boolean onBackPressed() {
            return journalFragment.dismissSnackBar() ||
                    blackListFragment.dismissSnackBar() ||
                    whiteListFragment.dismissSnackBar();
        }

        // Switches fragment by navigation menu item
        void switchFragment(@IdRes int itemId) {
            Intent intent = getIntent();
            // passing intent's extra to the fragment
            Bundle extras = intent.getExtras();
            Bundle arguments = (extras != null ? new Bundle(extras) : new Bundle());
            switch (itemId) {
                case R.id.nav_journal:
                    arguments.putString(TITLE, getString(R.string.Journal));
                    switchFragment(journalFragment, arguments);
                    break;
                case R.id.nav_black_list:
                    arguments.putString(TITLE, getString(R.string.Black_list));
                    arguments.putInt(CONTACT_TYPE, Contact.TYPE_BLACK_LIST);
                    switchFragment(blackListFragment, arguments);
                    break;
                case R.id.nav_white_list:
                    arguments.putString(TITLE, getString(R.string.White_list));
                    arguments.putInt(CONTACT_TYPE, Contact.TYPE_WHITE_LIST);
                    switchFragment(whiteListFragment, arguments);
                    break;
                case R.id.nav_sms:
                    arguments.putString(TITLE, getString(R.string.Messaging));
                    switchFragment(smsFragment, arguments);
                    break;
                case R.id.nav_settings:
                    arguments.putString(TITLE, getString(R.string.Settings));
                    switchFragment(settingsFragment, arguments);
                    break;
                default:
                    arguments.putString(TITLE, getString(R.string.Information));
                    switchFragment(informationFragment, arguments);
                    break;
            }

            // remove used extras
            intent.removeExtra(LIST_POSITION);
        }

        // Switches to passed fragment
        private void switchFragment(Fragment fragment, Bundle arguments) {
            // replace the current showed fragment
            Fragment current = getSupportFragmentManager().findFragmentByTag(CURRENT_FRAGMENT);
            if (current != fragment) {
                fragment.setArguments(arguments);
                getSupportFragmentManager().beginTransaction().
                        replace(R.id.frame_layout, fragment, CURRENT_FRAGMENT).commit();
            }
        }

        // Updates the current fragment
        private void updateFragment() {
            Fragment fragment = getSupportFragmentManager().findFragmentByTag(CURRENT_FRAGMENT);
            if (fragment != null) {
                FragmentTransaction ft = getSupportFragmentManager().beginTransaction();
                ft.detach(fragment).attach(fragment).commit();
            }
        }
    }

    // Shows the activity of SMS sending
    private void showSendSMSActivity() {
        Uri uri = getIntent().getData();
        if (uri == null) {
            return;
        }

        // get phone number where to send the SMS
        String ssp = uri.getSchemeSpecificPart();
        String number = ContactsAccessHelper.normalizePhoneNumber(ssp);
        if (number.isEmpty()) {
            return;
        }

        // find person by phone number in contacts
        String person = null;
        ContactsAccessHelper db = ContactsAccessHelper.getInstance(this);
        Contact contact = db.getContact(this, number);
        if (contact != null) {
            person = contact.name;
        }

        // get SMS thread id by phone number
        int threadId = db.getSMSThreadIdByNumber(this, number);
        if (threadId >= 0) {
            // get the count of unread sms of the thread
            int unreadCount = db.getSMSMessagesUnreadCountByThreadId(this, threadId);

            // open thread's SMS conversation activity
            Bundle arguments = new Bundle();
            arguments.putInt(FragmentArguments.THREAD_ID, threadId);
            arguments.putInt(FragmentArguments.UNREAD_COUNT, unreadCount);
            arguments.putString(FragmentArguments.CONTACT_NUMBER, number);
            String title = (person != null ? person : number);
            CustomFragmentActivity.show(this, title, SMSConversationFragment.class, arguments);
        }

        // open SMS sending activity
        Bundle arguments = new Bundle();
        arguments.putString(FragmentArguments.CONTACT_NAME, person);
        arguments.putString(FragmentArguments.CONTACT_NUMBER, number);
        String title = getString(R.string.New_message);
        CustomFragmentActivity.show(this, title, SMSSendFragment.class, arguments);
    }
}
