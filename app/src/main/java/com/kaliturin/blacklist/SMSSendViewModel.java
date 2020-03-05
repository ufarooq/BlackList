package com.kaliturin.blacklist;

import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModel;

public class SMSSendViewModel extends ViewModel {
    private MutableLiveData<String> counterTextViewText;

    public SMSSendViewModel() {
        counterTextViewText = new MutableLiveData<>("");
    }

    public MutableLiveData<String> getCounterTextViewText() {
        return counterTextViewText;
    }
}
