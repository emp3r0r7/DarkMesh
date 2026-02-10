package com.geeksville.mesh.service;

import static ar.com.hjg.pngj.PngHelperInternal.debug;

import com.geeksville.mesh.DataPacket;
import com.geeksville.mesh.IMeshService;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

public class GlobalRadioMesh {

    public static volatile IMeshService radioMeshService; //globale per tutte le istanze

    public static synchronized void setRadio(IMeshService s) {
        radioMeshService = s;
    }

    public static synchronized IMeshService getRadio() {
        return radioMeshService;
    }

    public static final Map<Integer, String> autoDeleteMap = new ConcurrentHashMap<>();

    public static void sendMessage(String str, String contactKey, Integer replyId) {

        if (radioMeshService == null) {
            debug("Could not send message, RadioMesh is null!");
            return;
        }

        try {
            // contactKey: unique contact key filter (channel)+(nodeId)
            Integer channel = null;
            if (contactKey != null && !contactKey.isEmpty()) {
                char firstChar = contactKey.charAt(0);
                if (Character.isDigit(firstChar)) {
                    channel = Character.getNumericValue(firstChar);
                }
            }
            String dest = (channel != null) ? contactKey.substring(1) : contactKey;

            DataPacket p = new DataPacket(dest, (channel != null) ? channel : 0, str, replyId);
            radioMeshService.send(p);
        } catch (Exception e) {
            debug("Could not send  message");
        }
    }

}
