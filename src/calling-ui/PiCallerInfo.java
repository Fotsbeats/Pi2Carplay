package com.android.car.dialer.ui;

import android.telecom.TelecomManager;
import android.telephony.PhoneNumberUtils;
import android.text.TextUtils;
import java.util.Locale;

/** Use the same synced, account-scoped phonebook as the full AOSP call screen. */
public final class PiCallerInfo {
    public static String label(String number,String account,int presentation){
        if(presentation==TelecomManager.PRESENTATION_RESTRICTED)return "Private caller";
        if(presentation!=TelecomManager.PRESENTATION_ALLOWED || TextUtils.isEmpty(number))return "Unknown caller";
        try {
            Class<?> type=Class.forName("com.android.car.telephony.common.InMemoryPhoneBook");Object book=type.getMethod("get").invoke(null);
            if(book!=null && (Boolean)type.getMethod("isLoaded").invoke(book)){
                Object contact=type.getMethod("lookupContactEntry",String.class,String.class).invoke(book,number,account);
                if(contact!=null){String name=(String)contact.getClass().getMethod("getDisplayName").invoke(contact);if(!TextUtils.isEmpty(name))return name;}
            }
        }catch(ReflectiveOperationException ignored){}
        String formatted=PhoneNumberUtils.formatNumber(number,Locale.getDefault().getCountry());return TextUtils.isEmpty(formatted)?number:formatted;
    }
}
