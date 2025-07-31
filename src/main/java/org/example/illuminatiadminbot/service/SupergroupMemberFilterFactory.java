package org.example.illuminatiadminbot.service;

import it.tdlight.jni.TdApi;

import java.util.ArrayList;
import java.util.List;

public class SupergroupMemberFilterFactory {

    public static TdApi.SupergroupMembersFilter get(String telegramStatus) {
        return switch (telegramStatus) {
            case "recent" -> new TdApi.SupergroupMembersFilterRecent();
            case "administrators" -> new TdApi.SupergroupMembersFilterAdministrators();
            case "banned" -> new TdApi.SupergroupMembersFilterBanned();
            case "restricted" -> new TdApi.SupergroupMembersFilterRestricted();
            case "bots" -> new TdApi.SupergroupMembersFilterBots();
            default -> null;
        };
    }

    public static List<TdApi.SupergroupMembersFilter> getAll() {
        List<TdApi.SupergroupMembersFilter> allMembers = new ArrayList<>();
        List<String> allTypes = List.of(
                "recent",
                "administrators",
                "banned",
                "restricted",
                "bots"
        );
        for (String type : allTypes) {
            allMembers.add(get(type));
        }

        return allMembers;
    }

}
