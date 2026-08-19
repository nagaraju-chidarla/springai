package com.ai.springai.config;

import org.springframework.ai.transformer.splitter.TextSplitter;

import java.lang.reflect.Array;
import java.util.ArrayList;
import java.util.List;

public class HrPolicySplitter extends TextSplitter {

    @Override
    protected List<String> splitText(String text) {
        List<String> chunks= new ArrayList<>();

        String[] sections = text.split("(?=Annual Leave:|Sick Leave:|Notice Period:|Work From Home:|Office Timing:|Medical Insurance:)");

        for (String section: sections) {
            String cleaned = section.trim();
            if (!cleaned.isEmpty()) {
                chunks.add(section);
            }
        }
        return chunks;
    }
}
