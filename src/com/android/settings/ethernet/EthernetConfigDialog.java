/*
 * Copyright (C) 2010 The Android-x86 Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Author: Yi Sun <beyounn@gmail.com>
 */

package com.android.settings.ethernet;

import java.net.InetAddress;
import java.util.List;

import com.android.settings.R;

import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.ContentResolver;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.NetworkUtils;
import android.net.NetworkInfo;
import android.net.ethernet.EthernetManager;
import android.net.ethernet.EthernetDevInfo;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TextView;
import android.text.Editable;
import android.text.TextUtils;
import android.text.TextWatcher;
import android.util.Slog;

public class EthernetConfigDialog extends AlertDialog implements
        TextWatcher, DialogInterface.OnClickListener, AdapterView.OnItemSelectedListener, View.OnClickListener {
    private final String TAG = "EtherenetSettings";
    private static final boolean localLOGV = false;

    private EthernetEnabler mEthEnabler;
    private View mView;
    private Spinner mDevList;
    private TextView mDevs;
    private RadioButton mConTypeDhcp;
    private RadioButton mConTypeManual;
    private EditText mIpaddr;
    private EditText mDns;
    private EditText mGw;
    private EditText mMask;

    private EthernetLayer mEthLayer;
    private EthernetManager mEthManager;
    private EthernetDevInfo mEthInfo;
    private boolean mEnablePending;
    private final Handler mTextViewChangedHandler;

    public EthernetConfigDialog(Context context, EthernetEnabler Enabler) {
        super(context);
        mEthLayer = new EthernetLayer(this);
        mEthEnabler = Enabler;
        mEthManager=Enabler.getManager();
        mTextViewChangedHandler = new Handler();
        buildDialogContent(context);
    }

    public int buildDialogContent(Context context) {
        this.setTitle(R.string.eth_config_title);
        this.setView(mView = getLayoutInflater().inflate(R.layout.eth_configure, null));
        mDevs = (TextView) mView.findViewById(R.id.eth_dev_list_text);
        mDevList = (Spinner) mView.findViewById(R.id.eth_dev_spinner);
        mConTypeDhcp = (RadioButton) mView.findViewById(R.id.dhcp_radio);
        mConTypeManual = (RadioButton) mView.findViewById(R.id.manual_radio);
        mIpaddr = (EditText)mView.findViewById(R.id.ipaddr_edit);
        mMask = (EditText)mView.findViewById(R.id.netmask_edit);
        mDns = (EditText)mView.findViewById(R.id.eth_dns_edit);
        mGw = (EditText)mView.findViewById(R.id.eth_gw_edit);

        mConTypeDhcp.setChecked(true);
        mConTypeManual.setChecked(false);
        mIpaddr.setEnabled(false);
        mMask.setEnabled(false);
        mDns.setEnabled(false);
        mGw.setEnabled(false);
        mConTypeManual.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(true);
                mDns.setEnabled(true);
                mGw.setEnabled(true);
                mMask.setEnabled(true);

                enableSubmitIfAppropriate();
            }
        });

        mConTypeDhcp.setOnClickListener(new RadioButton.OnClickListener() {
            public void onClick(View v) {
                mIpaddr.setEnabled(false);
                mDns.setEnabled(false);
                mGw.setEnabled(false);
                mMask.setEnabled(false);

                enableSubmitIfAppropriate();
            }
        });

        this.setInverseBackgroundForced(true);
        this.setButton(BUTTON_POSITIVE, context.getText(R.string.menu_save), this);
        this.setButton(BUTTON_NEGATIVE, context.getText(R.string.menu_cancel), this);
        String[] Devs = mEthEnabler.getManager().getDeviceNameList();
        if (Devs != null) {
            if (localLOGV)
                Slog.v(TAG, "found device: " + Devs[0]);
            updateDevNameList(Devs);
            if (mEthManager.isConfigured()) {
                mEthInfo = mEthManager.getSavedConfig();
                for (int i = 0 ; i < Devs.length; i++) {
                    if (Devs[i].equals(mEthInfo.getIfName())) {
                        mDevList.setSelection(i);
                        break;
                    }
                }

                // Read previous settings from detabase
                final ContentResolver res = context.getContentResolver();
                mIpaddr.setText(Settings.Secure.getString(res, Settings.Secure.ETHERNET_IP));
                mGw.setText(Settings.Secure.getString(res, Settings.Secure.ETHERNET_ROUTE));
                mDns.setText(Settings.Secure.getString(res, Settings.Secure.ETHERNET_DNS));
                mMask.setText(Settings.Secure.getString(res, Settings.Secure.ETHERNET_MASK));

                mIpaddr.addTextChangedListener(this);
                mMask.addTextChangedListener(this);
                mDns.addTextChangedListener(this);
                mGw.addTextChangedListener(this);

                if (mEthInfo.getConnectMode().equals(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP)) {
                    mIpaddr.setEnabled(false);
                    mDns.setEnabled(false);
                    mGw.setEnabled(false);
                    mMask.setEnabled(false);
                } else {
                    mConTypeDhcp.setChecked(false);
                    mConTypeManual.setChecked(true);
                    mIpaddr.setEnabled(true);
                    mDns.setEnabled(true);
                    mGw.setEnabled(true);
                    mMask.setEnabled(true);
                }
            }
            else {
                for (int i = 0 ; i < Devs.length; i++) {
                    if (Devs[i].equals("eth0")) {
                        mDevList.setSelection(i);
                        break;
                    }
                }
            }
        }
        return 0;
    }

    private void handle_saveconf() {
        EthernetDevInfo info = new EthernetDevInfo();
        info.setIfName(mDevList.getSelectedItem().toString());
        if (localLOGV)
            Slog.v(TAG, "Config device for " + mDevList.getSelectedItem().toString());
        if (mConTypeDhcp.isChecked()) {
            Slog.v(TAG, "Config device for DHCP ");
            info.setConnectMode(EthernetDevInfo.ETHERNET_CONN_MODE_DHCP);
        } else {
            Slog.v(TAG, "Config device for static " + mIpaddr.getText().toString() + mGw.getText().toString() + mDns.getText().toString() + mMask.getText().toString());
            info.setConnectMode(EthernetDevInfo.ETHERNET_CONN_MODE_MANUAL);
        }

        // Store the static IP fields in database
        info.setIpAddress(mIpaddr.getText().toString());
        info.setRouteAddr(mGw.getText().toString());
        info.setDnsAddr(mDns.getText().toString());
        info.setNetMask(mMask.getText().toString());

        mEthManager.updateDevInfo(info);
        if (mEnablePending) {
            mEthManager.setEnabled(true);
            mEnablePending = false;
        }
    }

    public void onClick(DialogInterface dialog, int which) {
        switch (which) {
            case BUTTON_POSITIVE:
                handle_saveconf();
                // [Advantech] Workaround for DNS unsync issue
                // Trun off/on the Enabler again to reset the ethernet connection.
                if (mEthManager.isConfigured() == true &&
                      mEthEnabler.isCheckBoxChecked() == true) {
                    mEthEnabler.onPreferenceChange(null, false);
                    mEthEnabler.onPreferenceChange(null, true);
                }
                break;
            case BUTTON_NEGATIVE:
                //Don't need to do anything
                break;
            default:
                Slog.e(TAG,"Unknow button");
        }
    }

    public void onItemSelected(AdapterView<?> parent, View view, int position, long id) {

    }

    public void onNothingSelected(AdapterView<?> parent) {

    }

    public void onClick(View v) {

    }

    public void updateDevNameList(String[] DevList) {
        if (DevList != null) {
            ArrayAdapter<CharSequence> adapter = new ArrayAdapter<CharSequence>(
                    getContext(), android.R.layout.simple_spinner_item, DevList);
            adapter.setDropDownViewResource(
                    android.R.layout.simple_spinner_dropdown_item);
            mDevList.setAdapter(adapter);
        }

    }

    public void enableAfterConfig() {
        mEnablePending = true;
    }

    // [Advantech] Add Text check for IP address format
    // IP address cannot be empty.
    // The other fields can be empty, but the format should be valid.
    private boolean ipAddrIsValid() {
        InetAddress inetAddr = null;

        String ipAddr = mIpaddr.getText().toString();
        if (TextUtils.isEmpty(ipAddr)) {
            //Slog.e(TAG, "IP address is null!");
            return false;
        } else {
            try {
                inetAddr = NetworkUtils.numericToInetAddress(ipAddr);
            } catch (IllegalArgumentException e) {
                //Slog.e(TAG, "IP address format is incorrect!");
                return false;
            }
        }

        String gateway = mGw.getText().toString();
        if (!TextUtils.isEmpty(gateway)) {
            try {
                inetAddr = NetworkUtils.numericToInetAddress(gateway);
            } catch (IllegalArgumentException e) {
                //Slog.e(TAG, "Gateway address format is incorrect!");
                return false;
            }
        }

        String dns = mDns.getText().toString();
        if (!TextUtils.isEmpty(dns)) {
            try {
                inetAddr = NetworkUtils.numericToInetAddress(dns);
            } catch (IllegalArgumentException e) {
                //Slog.e(TAG, "DNS address format is incorrect!");
                return false;
            }
        }

        String mask = mMask.getText().toString();
        if (!TextUtils.isEmpty(mask)) {
            try {
                inetAddr = NetworkUtils.numericToInetAddress(mask);
            } catch (IllegalArgumentException e) {
                //Slog.e(TAG, "Mask address format is incorrect!");
                return false;
            }
        }

        return true;
    }

    private void enableSubmitIfAppropriate() {
        boolean enabled = false;
        Button submit = this.getButton(BUTTON_POSITIVE);

        if (ipAddrIsValid() || mConTypeDhcp.isChecked()) {
            enabled = true;
        } else {
            enabled = false;
        }
        submit.setEnabled(enabled);
    }

    @Override
    public void afterTextChanged(Editable s) {
        mTextViewChangedHandler.post(new Runnable() {
                public void run() {
                    enableSubmitIfAppropriate();
                }
            });
    }

    @Override
    public void beforeTextChanged(CharSequence s, int start, int count, int after) {
        // work done in afterTextChanged
    }

    @Override
    public void onTextChanged(CharSequence s, int start, int before, int count) {
        // work done in afterTextChanged
    }
}
