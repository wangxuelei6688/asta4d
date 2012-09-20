package com.astamuse.asta4d.data.builtin;

import com.astamuse.asta4d.data.DataConvertor;

public class String2Bool implements DataConvertor<String, Boolean> {

    @Override
    public Boolean convert(String s) {
        if (s == null) {
            return false;
        } else {
            return Boolean.parseBoolean(s);
        }
    }

}
