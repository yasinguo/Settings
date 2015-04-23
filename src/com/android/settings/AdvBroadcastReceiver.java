package com.android.settings;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

public class AdvBroadcastReceiver extends BroadcastReceiver {
	
	private static final String BACKUP_INTENT = "android.advantech.advbackup";
	private static final String RECOVERY_INTENT = "android.advantech.advrecovery";
	
    public AdvBroadcastReceiver() {
    }
    
    @Override
    public void onReceive(Context context, Intent intent) {
        if (intent.getAction().equals(BACKUP_INTENT)) {
        	Log.e("advantech", "receive BACKUP_INTENT");
        } else if (intent.getAction().equals(RECOVERY_INTENT)) {
        	Log.e("advantech", "receive RECOVERY_INTENT");
        }
    }
}
